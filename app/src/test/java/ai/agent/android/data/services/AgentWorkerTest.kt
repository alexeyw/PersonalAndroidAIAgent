package ai.agent.android.data.services

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
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
 * Robolectric coverage for the `@HiltWorker`-annotated [AgentWorker].
 *
 * Hilt's assisted-inject machinery only fires inside an actual application
 * runtime, so the test instantiates the worker through a manual [WorkerFactory]
 * that hands the mocked [AgentOrchestratorUseCase] to the worker's constructor.
 * [TestListenableWorkerBuilder] then drives the worker through its
 * `doWork()` lifecycle with the same `Dispatchers.Default` semantics WorkManager
 * uses in production.
 */
@RunWith(RobolectricTestRunner::class)
class AgentWorkerTest {

    private lateinit var context: Context
    private lateinit var useCase: AgentOrchestratorUseCase

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
        ): ListenableWorker = AgentWorker(appContext, workerParameters, useCase)
    }

    private fun buildWorker(input: Data = Data.EMPTY): AgentWorker = TestListenableWorkerBuilder<AgentWorker>(context)
        .setInputData(input)
        .setWorkerFactory(workerFactory())
        .build()

    @Test
    fun `given no prompt input when doWork runs then returns failure`() = runTest {
        val worker = buildWorker(Data.EMPTY)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `given blank prompt input when doWork runs then returns failure`() = runTest {
        val input = Data.Builder().putString(AgentWorker.KEY_PROMPT, "   ").build()
        val worker = buildWorker(input)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `given orchestrator emits Completed when doWork runs then returns success`() = runTest {
        val input = Data.Builder().putString(AgentWorker.KEY_PROMPT, "hello").build()
        coEvery { useCase(any(), "hello") } returns flowOf(
            AgentOrchestratorState.PipelineStage(
                AgentOrchestratorState.PipelineStepInfo(1, 3, "INPUT"),
            ),
            AgentOrchestratorState.Completed("done"),
        )
        val worker = buildWorker(input)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `given orchestrator emits Error when doWork runs then still returns success (stream completed)`() = runTest {
        // The worker semantics are: a *stream* that completes (even with an Error state) is
        // a successful worker run — the orchestrator's error handling lives upstream.
        // Only an *exception* aborts via Result.retry().
        val input = Data.Builder().putString(AgentWorker.KEY_PROMPT, "hello").build()
        coEvery { useCase(any(), "hello") } returns flowOf(
            AgentOrchestratorState.PipelineStage(
                AgentOrchestratorState.PipelineStepInfo(1, 3, "INPUT"),
            ),
            AgentOrchestratorState.Error("some failure"),
        )
        val worker = buildWorker(input)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `given orchestrator throws when doWork runs then returns retry`() = runTest {
        val input = Data.Builder().putString(AgentWorker.KEY_PROMPT, "hello").build()
        coEvery { useCase(any(), "hello") } throws RuntimeException("boom")
        val worker = buildWorker(input)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `given orchestrator emits only PipelineStage entries when doWork runs then returns success`() = runTest {
        // Guards against accidental `Result.failure()` short-circuits when the stream
        // contains no terminal Completed/Error state (e.g. cancellation upstream).
        val input = Data.Builder().putString(AgentWorker.KEY_PROMPT, "hello").build()
        coEvery { useCase(any(), "hello") } returns flowOf(
            AgentOrchestratorState.PipelineStage(
                AgentOrchestratorState.PipelineStepInfo(1, 3, "INPUT"),
            ),
            AgentOrchestratorState.PipelineStage(
                AgentOrchestratorState.PipelineStepInfo(2, 3, "LITE_RT"),
            ),
        )
        val worker = buildWorker(input)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `given input contains the canonical prompt key when fetched then matches the public contract`() {
        // The public KEY_PROMPT constant is the wire-level contract between the worker
        // and `ScheduleTaskUseCase` — a typo here breaks the integration silently.
        assertEquals("agent_prompt", AgentWorker.KEY_PROMPT)
        assertEquals("current_stage", AgentWorker.KEY_CURRENT_STAGE)
    }
}
