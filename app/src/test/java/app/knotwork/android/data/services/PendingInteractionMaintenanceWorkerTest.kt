package app.knotwork.android.data.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.ParkedRunResumer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Robolectric coverage for the `@HiltWorker`-annotated
 * [PendingInteractionMaintenanceWorker].
 *
 * As with [MemoryCompactionWorkerTest], Hilt's assisted-inject machinery only
 * fires inside a real application runtime, so the test hands the mocked
 * collaborators to the worker through a manual [WorkerFactory] and drives
 * `doWork()` via [TestListenableWorkerBuilder].
 */
@RunWith(RobolectricTestRunner::class)
class PendingInteractionMaintenanceWorkerTest {

    private lateinit var context: Context
    private lateinit var pendingInteractionRepository: PendingInteractionRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var parkedRunResumer: ParkedRunResumer

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        pendingInteractionRepository = mockk()
        settingsRepository = mockk()
        parkedRunResumer = mockk(relaxed = true)
        every { settingsRepository.backgroundApprovalWindowHours } returns flowOf(24)
    }

    private fun workerFactory(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = PendingInteractionMaintenanceWorker(
            appContext,
            workerParameters,
            pendingInteractionRepository,
            settingsRepository,
            parkedRunResumer,
        )
    }

    private fun buildWorker(): PendingInteractionMaintenanceWorker =
        TestListenableWorkerBuilder<PendingInteractionMaintenanceWorker>(context)
            .setWorkerFactory(workerFactory())
            .build()

    /** A parked clarification whose window already elapsed. */
    private fun expiredPark(runId: String): PendingInteraction = PendingInteraction(
        runId = runId,
        sessionId = "session-1",
        kind = PendingInteractionKind.CLARIFICATION,
        question = "Q?",
        requestedAt = 0L,
    )

    @Test
    fun `given expired parks when doWork runs then each is failed through the shared settlement`() = runTest {
        coEvery { pendingInteractionRepository.getRequestedAtOrBefore(any()) } returns listOf(
            expiredPark("run-1"),
            expiredPark("run-2"),
        )

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify {
            parkedRunResumer.failPark(
                match { it.runId == "run-1" },
                ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE,
            )
            parkedRunResumer.failPark(
                match { it.runId == "run-2" },
                ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE,
            )
        }
    }

    @Test
    fun `given nothing expired when doWork runs then succeeds without settlements`() = runTest {
        coEvery { pendingInteractionRepository.getRequestedAtOrBefore(any()) } returns emptyList()

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { parkedRunResumer.failPark(any(), any()) }
    }

    @Test
    fun `given the cutoff honours the configured window when doWork runs then query uses it`() = runTest {
        every { settingsRepository.backgroundApprovalWindowHours } returns flowOf(2)
        coEvery { pendingInteractionRepository.getRequestedAtOrBefore(any()) } returns emptyList()
        val before = System.currentTimeMillis() - 2 * 3_600_000L

        buildWorker().doWork()

        coVerify {
            pendingInteractionRepository.getRequestedAtOrBefore(
                match { cutoff -> cutoff in before..(System.currentTimeMillis() - 2 * 3_600_000L) },
            )
        }
    }

    @Test
    fun `given the pass throws when doWork runs then retries`() = runTest {
        coEvery { pendingInteractionRepository.getRequestedAtOrBefore(any()) } throws IllegalStateException("io")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
