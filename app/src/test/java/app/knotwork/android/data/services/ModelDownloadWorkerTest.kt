package app.knotwork.android.data.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.knotwork.android.data.network.ResumableFileDownloader
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.RegisterDownloadedModelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Covers the download worker's policy decisions: what it does with the token,
 * what it does once the bytes are on disk, and — the part that decides whether
 * a flaky network costs the user their progress — when a failure is worth
 * another attempt.
 */
@RunWith(RobolectricTestRunner::class)
class ModelDownloadWorkerTest {

    private lateinit var context: Context
    private lateinit var downloader: ResumableFileDownloader
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var registerDownloadedModel: RegisterDownloadedModelUseCase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        downloader = mockk()
        settingsRepository = mockk()
        every { settingsRepository.huggingFaceAuthToken } returns flowOf("hf_stored")
        registerDownloadedModel = mockk(relaxed = true)
        coEvery { registerDownloadedModel(any(), any(), any()) } returns 1L
    }

    @Test
    fun `given a completed download when it finishes then the model is registered and the path returned`() = runTest {
        val file = java.io.File(context.cacheDir, "m.bin").apply { writeText("payload") }
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Success(file.absolutePath)

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            file.absolutePath,
            (result as ListenableWorker.Result.Success).outputData.getString(ModelDownloadWorker.KEY_OUTPUT_PATH),
        )
        // Registration lives here because the transfer outlives every screen —
        // a survived download must leave a model the app knows about.
        coVerify(exactly = 1) {
            registerDownloadedModel(fileName = "m.bin", path = file.absolutePath, sizeBytes = "payload".length.toLong())
        }
    }

    @Test
    fun `given no stored-auth flag when downloading then no token is read or sent`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Success("/models/m.bin")

        worker(useStoredAuth = false).doWork()

        coVerify { downloader.download(any(), any(), null, any()) }
    }

    @Test
    fun `given the stored-auth flag when downloading then the token comes from the encrypted store`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Success("/models/m.bin")

        worker(useStoredAuth = true).doWork()

        // The secret is read here, not carried through worker input — that
        // input is persisted by WorkManager in the clear.
        coVerify { downloader.download(any(), any(), "hf_stored", any()) }
    }

    @Test
    fun `given a transport failure on the first attempt when it fails then another attempt is scheduled`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Failure("Network timeout", httpCode = null)

        val result = worker().doWork()

        // Retrying resumes rather than restarts, so it costs the user nothing.
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `given an HTTP status when it fails then it gives up immediately`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Failure("Server returned code: 404", httpCode = 404)

        val result = worker().doWork()

        // The server's answer will not change on a retry — spending the user's
        // battery to hear it again is the wrong trade.
        val failure = result as ListenableWorker.Result.Failure
        assertEquals(404, failure.outputData.getInt(ModelDownloadWorker.KEY_ERROR_CODE, 0))
        assertEquals("Server returned code: 404", failure.outputData.getString(ModelDownloadWorker.KEY_ERROR))
    }

    @Test
    fun `given the attempt budget is spent when a transport failure repeats then it fails for good`() = runTest {
        coEvery { downloader.download(any(), any(), any(), any()) } returns
            ResumableFileDownloader.Outcome.Failure("Network timeout", httpCode = null)

        val result = worker(runAttemptCount = 2).doWork()

        val failure = result as ListenableWorker.Result.Failure
        assertEquals(
            ModelDownloadWorker.NO_HTTP_CODE,
            failure.outputData.getInt(ModelDownloadWorker.KEY_ERROR_CODE, 0),
        )
    }

    @Test
    fun `given a request without a URL when run then it fails instead of downloading`() = runTest {
        val result = worker(url = null).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { downloader.download(any(), any(), any(), any()) }
    }

    private fun worker(
        url: String? = "http://example.com/m.bin",
        fileName: String = "m.bin",
        useStoredAuth: Boolean = false,
        runAttemptCount: Int = 0,
    ): ModelDownloadWorker = TestListenableWorkerBuilder<ModelDownloadWorker>(context)
        .setInputData(
            workDataOf(
                ModelDownloadWorker.KEY_URL to url,
                ModelDownloadWorker.KEY_FILE_NAME to fileName,
                ModelDownloadWorker.KEY_USE_STORED_AUTH to useStoredAuth,
            ),
        )
        .setRunAttemptCount(runAttemptCount)
        .setWorkerFactory(
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = ModelDownloadWorker(
                    appContext,
                    workerParameters,
                    downloader,
                    settingsRepository,
                    registerDownloadedModel,
                )
            },
        )
        .build()
}
