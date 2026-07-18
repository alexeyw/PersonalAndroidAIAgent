package app.knotwork.android.data.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.models.TriggerSkipReason
import app.knotwork.android.domain.usecases.FireTriggerUseCase
import app.knotwork.android.domain.usecases.TriggerFireOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Robolectric coverage for the `@HiltWorker`-annotated [TriggerWatchWorker],
 * including the self-cancel reclaim path.
 */
@RunWith(RobolectricTestRunner::class)
class TriggerWatchWorkerTest {

    private lateinit var context: Context
    private lateinit var fireTriggerUseCase: FireTriggerUseCase
    private lateinit var workManager: WorkManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        fireTriggerUseCase = mockk()
        workManager = mockk(relaxed = true)
    }

    private fun workerFactory(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = TriggerWatchWorker(appContext, workerParameters, fireTriggerUseCase, workManager)
    }

    private fun buildWorker(triggerId: String?): TriggerWatchWorker {
        val builder = TestListenableWorkerBuilder<TriggerWatchWorker>(context).setWorkerFactory(workerFactory())
        if (triggerId != null) {
            builder.setInputData(workDataOf(TriggerWatchWorker.KEY_TRIGGER_ID to triggerId))
        }
        return builder.build()
    }

    @Test
    fun `given a fired outcome when doWork runs then succeeds and keeps the watch`() = runTest {
        coEvery { fireTriggerUseCase("t1", any(), any()) } returns TriggerFireOutcome.Fired("pipe-1")

        val result = buildWorker("t1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // The per-trigger periodic watch evaluates as the POLL source.
        coVerify(exactly = 1) { fireTriggerUseCase("t1", TriggerEvaluationSource.POLL, any()) }
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    @Test
    fun `given a still-valid skip when doWork runs then keeps the watch`() = runTest {
        coEvery { fireTriggerUseCase("t1", any(), any()) } returns
            TriggerFireOutcome.Skipped(TriggerSkipReason.CONDITION_NOT_MET)

        val result = buildWorker("t1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    @Test
    fun `given a NotFound outcome when doWork runs then cancels its own watch`() = runTest {
        coEvery { fireTriggerUseCase("t1", any(), any()) } returns TriggerFireOutcome.NotFound

        val result = buildWorker("t1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) { workManager.cancelUniqueWork(TriggerWatchWorker.UNIQUE_NAME_PREFIX + "t1") }
    }

    @Test
    fun `given an auto-disabled outcome when doWork runs then cancels its own watch`() = runTest {
        coEvery { fireTriggerUseCase("t1", any(), any()) } returns TriggerFireOutcome.Disabled("pipe-1")

        val result = buildWorker("t1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) { workManager.cancelUniqueWork(TriggerWatchWorker.UNIQUE_NAME_PREFIX + "t1") }
    }

    @Test
    fun `given no trigger id when doWork runs then returns failure without firing`() = runTest {
        val result = buildWorker(triggerId = null).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { fireTriggerUseCase(any(), any(), any()) }
    }

    @Test
    fun `given the use case throws when doWork runs then returns retry`() = runTest {
        coEvery { fireTriggerUseCase("t1", any(), any()) } throws IllegalStateException("db unavailable")

        val result = buildWorker("t1").doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
