package app.knotwork.android.data.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.knotwork.android.domain.usecases.FireTriggerUseCase
import app.knotwork.android.domain.usecases.TriggerFireOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Robolectric coverage for the `@HiltWorker`-annotated [TriggerWatchWorker].
 *
 * Hilt assisted-inject only fires inside a real runtime, so the test supplies
 * the mocked [FireTriggerUseCase] through a manual [WorkerFactory] and drives
 * `doWork()` via [TestListenableWorkerBuilder].
 */
@RunWith(RobolectricTestRunner::class)
class TriggerWatchWorkerTest {

    private lateinit var context: Context
    private lateinit var fireTriggerUseCase: FireTriggerUseCase

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        fireTriggerUseCase = mockk()
    }

    private fun workerFactory(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = TriggerWatchWorker(appContext, workerParameters, fireTriggerUseCase)
    }

    private fun buildWorker(triggerId: String?): TriggerWatchWorker {
        val builder = TestListenableWorkerBuilder<TriggerWatchWorker>(context).setWorkerFactory(workerFactory())
        if (triggerId != null) {
            builder.setInputData(workDataOf(TriggerWatchWorker.KEY_TRIGGER_ID to triggerId))
        }
        return builder.build()
    }

    @Test
    fun `given a trigger id when doWork runs then fires it and returns success`() = runTest {
        coEvery { fireTriggerUseCase("t1", any()) } returns TriggerFireOutcome.Fired("pipe-1")

        val result = buildWorker("t1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { fireTriggerUseCase("t1", any()) }
    }

    @Test
    fun `given a skip outcome when doWork runs then still returns success`() = runTest {
        coEvery { fireTriggerUseCase("t1", any()) } returns TriggerFireOutcome.NotFound

        val result = buildWorker("t1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `given no trigger id when doWork runs then returns failure without firing`() = runTest {
        val result = buildWorker(triggerId = null).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { fireTriggerUseCase(any(), any()) }
    }

    @Test
    fun `given the use case throws when doWork runs then returns retry`() = runTest {
        coEvery { fireTriggerUseCase("t1", any()) } throws IllegalStateException("db unavailable")

        val result = buildWorker("t1").doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
