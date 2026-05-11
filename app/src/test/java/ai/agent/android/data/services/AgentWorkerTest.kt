package ai.agent.android.data.services

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AgentWorkerTest {
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var useCase: AgentOrchestratorUseCase
    private lateinit var worker: AgentWorker

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        useCase = mockk()

        val actualWorker = AgentWorker(context, workerParams, useCase)
        worker = spyk(actualWorker)

        // Mock setProgress to prevent it from suspending indefinitely waiting for WorkManager internal futures
        coEvery { worker.setProgress(any()) } returns Unit
    }

    @Test
    fun `doWork returns failure when prompt is null`() = runTest {
        every { workerParams.inputData } returns Data.EMPTY

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork returns failure when prompt is blank`() = runTest {
        val inputData = Data.Builder().putString(AgentWorker.KEY_PROMPT, "").build()
        every { workerParams.inputData } returns inputData

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork processes prompt and returns success on completion`() = runTest {
        val inputData = Data.Builder().putString(AgentWorker.KEY_PROMPT, "test prompt").build()
        every { workerParams.inputData } returns inputData

        coEvery { useCase(any(), "test prompt") } returns flowOf(
            AgentOrchestratorState.PipelineStage(AgentOrchestratorState.PipelineStepInfo(1, 3, "INPUT")),
            AgentOrchestratorState.Completed("Done"),
        )

        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }

    @Test
    fun `doWork processes prompt and returns success on error state`() = runTest {
        val inputData = Data.Builder().putString(AgentWorker.KEY_PROMPT, "test prompt").build()
        every { workerParams.inputData } returns inputData

        coEvery { useCase(any(), "test prompt") } returns flowOf(
            AgentOrchestratorState.PipelineStage(AgentOrchestratorState.PipelineStepInfo(1, 3, "INPUT")),
            AgentOrchestratorState.Error("Some error"),
        )

        val result = worker.doWork()

        // It returns success because the worker successfully finished the stream
        assertEquals(Result.success(), result)
    }

    @Test
    fun `doWork returns retry on exception`() = runTest {
        val inputData = Data.Builder().putString(AgentWorker.KEY_PROMPT, "test prompt").build()
        every { workerParams.inputData } returns inputData

        coEvery { useCase(any(), "test prompt") } throws RuntimeException("Simulated exception")

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }
}
