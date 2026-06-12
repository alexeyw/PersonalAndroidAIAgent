package app.knotwork.android.data.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.knotwork.android.domain.usecases.CleanupPipelineRunsUseCase
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
 * Robolectric coverage for the `@HiltWorker`-annotated [RunRetentionWorker].
 *
 * As with [MemoryCompactionWorkerTest], Hilt's assisted-inject machinery only
 * fires inside a real application runtime, so the test hands the mocked use
 * case to the worker through a manual [WorkerFactory] and drives `doWork()`
 * via [TestListenableWorkerBuilder].
 */
@RunWith(RobolectricTestRunner::class)
class RunRetentionWorkerTest {

    private lateinit var context: Context
    private lateinit var useCase: CleanupPipelineRunsUseCase

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        useCase = mockk()
    }

    private fun workerFactory(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = RunRetentionWorker(appContext, workerParameters, useCase)
    }

    private fun buildWorker(): RunRetentionWorker = TestListenableWorkerBuilder<RunRetentionWorker>(context)
        .setWorkerFactory(workerFactory())
        .build()

    @Test
    fun `given pass completes when doWork runs then returns success`() = runTest {
        coEvery { useCase() } returns CleanupPipelineRunsUseCase.Outcome(
            deletedRuns = 3,
            deletedLegacyTraceRows = 1,
        )

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { useCase() }
    }

    @Test
    fun `given the use case throws when doWork runs then returns retry`() = runTest {
        coEvery { useCase() } throws IllegalStateException("store unavailable")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
