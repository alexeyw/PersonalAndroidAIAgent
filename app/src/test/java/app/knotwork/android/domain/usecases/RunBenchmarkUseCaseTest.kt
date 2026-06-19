package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.ModelPerformanceSample
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.ModelPerformanceRepository
import app.knotwork.android.domain.services.NativeMemorySampler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RunBenchmarkUseCaseTest {

    private lateinit var taskQueueManager: TaskQueueManager
    private lateinit var localModelRepository: LocalModelRepository
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var engine: LlmInferenceEngine
    private lateinit var repository: ModelPerformanceRepository
    private lateinit var sampler: NativeMemorySampler
    private lateinit var useCase: RunBenchmarkUseCase

    @Before
    fun setup() {
        taskQueueManager = mockk()
        localModelRepository = mockk()
        loadModelUseCase = mockk()
        engine = mockk()
        repository = mockk(relaxed = true)
        sampler = NativeMemorySampler { 2_048L }

        every { taskQueueManager.globalState } returns MutableStateFlow(AgentOrchestratorState.Idle)
        useCase =
            RunBenchmarkUseCase(taskQueueManager, localModelRepository, loadModelUseCase, engine, repository, sampler)
    }

    @Test
    fun `given engine busy when invoked then refuses without touching the model`() = runTest {
        every { taskQueueManager.globalState } returns MutableStateFlow(AgentOrchestratorState.Loading)

        val outcome = useCase()

        assertEquals(BenchmarkOutcome.EngineBusy, outcome)
        coVerify(exactly = 0) { localModelRepository.getActiveModel() }
    }

    @Test
    fun `given no active model when invoked then NoActiveModel`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns null

        assertEquals(BenchmarkOutcome.NoActiveModel, useCase())
    }

    @Test
    fun `given model load fails when invoked then Failed`() = runTest {
        coEvery { localModelRepository.getActiveModel() } returns activeModel()
        coEvery { loadModelUseCase() } returns Result.Error(error = object : AppError.System {}, message = "boom")

        val outcome = useCase()

        assertTrue(outcome is BenchmarkOutcome.Failed)
        assertEquals("boom", (outcome as BenchmarkOutcome.Failed).message)
    }

    @Test
    fun `given a successful run when invoked then reports phases warmup then measure and records a benchmark sample`() =
        runTest {
            coEvery { localModelRepository.getActiveModel() } returns activeModel()
            coEvery { loadModelUseCase() } returns Result.Success(Unit)
            every { engine.currentModelPath } returns "/models/gemma.litertlm"
            every { engine.generateResponseStream(any(), any(), any()) } returns flowOf("a", "b", "c")

            val phases = mutableListOf<BenchmarkRunPhase>()
            val outcome = useCase { phases.add(it) }

            assertEquals(listOf(BenchmarkRunPhase.WARMING_UP, BenchmarkRunPhase.MEASURING), phases)
            assertTrue(outcome is BenchmarkOutcome.Success)
            val report = (outcome as BenchmarkOutcome.Success).report
            assertEquals("gemma-4-E2B-it", report.modelName)

            val sampleSlot = slot<ModelPerformanceSample>()
            coVerify { repository.record(capture(sampleSlot)) }
            assertTrue(sampleSlot.captured.isBenchmark)
            assertEquals("/models/gemma.litertlm", sampleSlot.captured.modelPath)
            assertEquals(3, sampleSlot.captured.tokenCount)
        }

    private fun activeModel(): LocalModel = LocalModel(
        id = 1,
        name = "gemma-4-E2B-it",
        path = "/models/gemma.litertlm",
        size = 0,
        isActive = true,
    )
}
