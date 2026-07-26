package app.knotwork.android.data.network

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.knotwork.android.data.services.ModelDownloadWorker
import app.knotwork.android.domain.models.DownloadState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the projection of background work onto the download state the UI
 * speaks, and the enqueue policy behind it.
 *
 * The uniqueness policy carries real weight: re-entering the download screen
 * must attach to the transfer already running rather than start a second copy
 * of a multi-gigabyte download.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidModelDownloadManagerTest {

    private lateinit var workManager: WorkManager
    private lateinit var manager: AndroidModelDownloadManager

    @Before
    fun setUp() {
        workManager = mockk(relaxed = true)
        manager = AndroidModelDownloadManager(workManager)
    }

    @Test
    fun `given a requested download when observed then work is enqueued as unique and kept`() = runTest {
        stubWorkInfos(
            workInfo(
                WorkInfo.State.SUCCEEDED,
                output = workDataOf(
                    ModelDownloadWorker.KEY_OUTPUT_PATH to "/models/m.bin",
                ),
            ),
        )

        manager.downloadModel("http://example.com/m.bin", "m.bin").toList()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                "model-download-m.bin",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `given a run from enqueued to success when observed then states mirror the transfer`() = runTest {
        stubWorkInfos(
            workInfo(WorkInfo.State.ENQUEUED),
            workInfo(WorkInfo.State.RUNNING, progress = workDataOf(ModelDownloadWorker.KEY_PROGRESS to 42)),
            workInfo(
                WorkInfo.State.SUCCEEDED,
                output = workDataOf(ModelDownloadWorker.KEY_OUTPUT_PATH to "/models/m.bin"),
            ),
        )

        val states = manager.downloadModel("http://example.com/m.bin", "m.bin").toList()

        assertEquals(
            listOf(
                DownloadState.Pending,
                DownloadState.Downloading(42),
                DownloadState.Success("/models/m.bin"),
            ),
            states,
        )
    }

    @Test
    fun `given a running worker with no progress yet when observed then it reports zero`() = runTest {
        stubWorkInfos(workInfo(WorkInfo.State.RUNNING))

        val states = manager.downloadModel("http://example.com/m.bin", "m.bin").toList()

        assertEquals(listOf(DownloadState.Downloading(0)), states)
    }

    @Test
    fun `given the network drops mid-download when the worker is re-queued then the percent holds`() = runTest {
        stubWorkInfos(
            workInfo(WorkInfo.State.RUNNING, progress = workDataOf(ModelDownloadWorker.KEY_PROGRESS to 26)),
            // Losing the network stops the worker; WorkManager clears its
            // progress and re-queues it behind the network constraint.
            workInfo(WorkInfo.State.ENQUEUED),
            // It comes back and resumes — briefly before the first percent tick.
            workInfo(WorkInfo.State.RUNNING),
            workInfo(WorkInfo.State.RUNNING, progress = workDataOf(ModelDownloadWorker.KEY_PROGRESS to 31)),
        )

        val states = manager.downloadModel("http://example.com/m.bin", "m.bin").toList()

        // The bytes never left the disk, so the figure must not fall back to
        // zero and tell the user they lost 26 % of a multi-gigabyte download.
        assertEquals(
            listOf(DownloadState.Downloading(26), DownloadState.Downloading(31)),
            states,
        )
    }

    @Test
    fun `given a failed worker when observed then the message and HTTP status survive`() = runTest {
        stubWorkInfos(
            workInfo(
                WorkInfo.State.FAILED,
                output = workDataOf(
                    ModelDownloadWorker.KEY_ERROR to "Server returned code: 401",
                    ModelDownloadWorker.KEY_ERROR_CODE to 401,
                ),
            ),
        )

        val states = manager.downloadModel("http://example.com/m.bin", "m.bin").toList()

        val error = (states.single() as DownloadState.Error).error as AndroidModelDownloadManager.DownloadError
        assertEquals("Server returned code: 401", error.message)
        // The Discover screen branches on this to offer the token flow.
        assertEquals(401, error.code)
    }

    @Test
    fun `given a transport failure when observed then no HTTP status is invented`() = runTest {
        stubWorkInfos(
            workInfo(
                WorkInfo.State.FAILED,
                output = workDataOf(
                    ModelDownloadWorker.KEY_ERROR to "Network timeout",
                    ModelDownloadWorker.KEY_ERROR_CODE to ModelDownloadWorker.NO_HTTP_CODE,
                ),
            ),
        )

        val states = manager.downloadModel("http://example.com/m.bin", "m.bin").toList()

        val error = (states.single() as DownloadState.Error).error as AndroidModelDownloadManager.DownloadError
        assertEquals(null, error.code)
    }

    @Test
    fun `given a cancelled download when observed then the stream ends without a terminal state`() = runTest {
        stubWorkInfos(
            workInfo(WorkInfo.State.RUNNING, progress = workDataOf(ModelDownloadWorker.KEY_PROGRESS to 12)),
            workInfo(WorkInfo.State.CANCELLED),
        )

        val states = manager.downloadModel("http://example.com/m.bin", "m.bin").toList()

        // The user who cancelled needs neither an error nor a success.
        assertEquals(listOf(DownloadState.Downloading(12)), states)
    }

    @Test
    fun `given no work info yet when observed then the stream keeps waiting`() = runTest {
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(
            emptyList(),
            listOf(workInfo(WorkInfo.State.RUNNING, progress = workDataOf(ModelDownloadWorker.KEY_PROGRESS to 7))),
        )

        val states = manager.downloadModel("http://example.com/m.bin", "m.bin").toList()

        // An empty list means the observation raced the enqueue — reporting a
        // finished download there would strand the caller.
        assertEquals(listOf(DownloadState.Downloading(7)), states)
    }

    @Test
    fun `given a cancel request when issued then the unique work is cancelled by file name`() {
        manager.cancelDownload("m.bin")

        verify(exactly = 1) { workManager.cancelUniqueWork("model-download-m.bin") }
    }

    @Test
    fun `given a download for a nested name when enqueued then the work name stays unique per file`() = runTest {
        stubWorkInfos(workInfo(WorkInfo.State.CANCELLED))

        manager.downloadModel("http://example.com/m.bin", "q4/model.litertlm").toList()

        verify {
            workManager.enqueueUniqueWork(
                "model-download-q4/model.litertlm",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    private fun stubWorkInfos(vararg infos: WorkInfo) {
        val frames = infos.map { listOf(it) }.toTypedArray()
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(*frames)
    }

    private fun workInfo(state: WorkInfo.State, progress: Data = Data.EMPTY, output: Data = Data.EMPTY): WorkInfo =
        mockk {
            every { this@mockk.state } returns state
            every { this@mockk.progress } returns progress
            every { outputData } returns output
        }
}
