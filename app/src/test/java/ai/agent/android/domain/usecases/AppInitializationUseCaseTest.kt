package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.InitStage
import ai.agent.android.domain.models.MemorySummary
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AppInitializationUseCase]. Verifies the strict stage order,
 * the non-fatal handling of model-load failures, the fatal handling of every
 * Room-backed prefetch, and the determinate progress fractions.
 */
class AppInitializationUseCaseTest {

    private lateinit var initializeAppUseCase: InitializeAppUseCase
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var useCase: AppInitializationUseCase

    @Before
    fun setUp() {
        initializeAppUseCase = mockk(relaxed = true)
        loadModelUseCase = mockk()
        pipelineRepository = mockk()
        chatRepository = mockk()
        memoryRepository = mockk()

        coEvery { loadModelUseCase() } returns Result.Success(Unit)
        coEvery { pipelineRepository.getAllPipelines() } returns flowOf(emptyList())
        coEvery { chatRepository.getSessionsFlow() } returns flowOf(emptyList())
        coEvery { memoryRepository.getRecentMemorySummaries(any()) } returns emptyList()

        useCase = AppInitializationUseCase(
            initializeAppUseCase = initializeAppUseCase,
            loadModelUseCase = loadModelUseCase,
            pipelineRepository = pipelineRepository,
            chatRepository = chatRepository,
            memoryRepository = memoryRepository,
        )
    }

    @Test
    fun `given all stages succeed when invoked then emits ordered progress ending in Done`() = runTest {
        val emissions = useCase().toList()

        val stages = emissions.map { it.stage }
        assertEquals(
            listOf(
                InitStage.Initializing,
                InitStage.LoadingModel,
                InitStage.LoadingPipelines,
                InitStage.LoadingChats,
                InitStage.LoadingMemory,
                InitStage.Done,
            ),
            stages,
        )

        // Final emission is Done with full progress.
        val terminal = emissions.last()
        assertEquals(terminal.totalSteps, terminal.completedSteps)
        assertEquals(InitStage.Done, terminal.stage)

        // Each non-terminal emission carries `completedSteps` matching its
        // index — the contract the splash screen relies on for the bar.
        emissions.dropLast(1).forEachIndexed { index, progress ->
            assertEquals(index, progress.completedSteps)
            assertEquals(5, progress.totalSteps)
        }

        coVerify(exactly = 1) { initializeAppUseCase() }
        coVerify(exactly = 1) { loadModelUseCase() }
        coVerify(exactly = 1) { pipelineRepository.getAllPipelines() }
        coVerify(exactly = 1) { chatRepository.getSessionsFlow() }
        coVerify(exactly = 1) { memoryRepository.getRecentMemorySummaries(any()) }
    }

    @Test
    fun `given LoadModelUseCase returns Error when invoked then continues to next stage`() = runTest {
        coEvery { loadModelUseCase() } returns Result.Error(
            error = object : AppError.System {},
            message = "No active model found",
        )

        val emissions = useCase().toList()

        // Model failure must NOT be fatal: subsequent stages still run and
        // the flow terminates in Done.
        assertEquals(InitStage.Done, emissions.last().stage)
        assertTrue(emissions.any { it.stage == InitStage.LoadingPipelines })
    }

    @Test
    fun `given InitializeAppUseCase throws when invoked then terminates with Failed`() = runTest {
        coEvery { initializeAppUseCase() } throws RuntimeException("seed prompts failed")

        val emissions = useCase().toList()

        val terminal = emissions.last()
        assertTrue("Expected Failed but was ${terminal.stage}", terminal.stage is InitStage.Failed)
        val failed = terminal.stage as InitStage.Failed
        assertEquals(InitStage.Initializing, failed.failedStage)
        assertEquals("seed prompts failed", failed.cause)

        // Downstream stages must NOT run.
        coVerify(exactly = 0) { loadModelUseCase() }
        coVerify(exactly = 0) { pipelineRepository.getAllPipelines() }
    }

    @Test
    fun `given pipeline repository throws when invoked then terminates with Failed at LoadingPipelines`() = runTest {
        coEvery { pipelineRepository.getAllPipelines() } returns flow {
            throw RuntimeException("pipeline read failed")
        }

        val emissions = useCase().toList()

        val terminal = emissions.last()
        assertTrue(terminal.stage is InitStage.Failed)
        val failed = terminal.stage as InitStage.Failed
        assertEquals(InitStage.LoadingPipelines, failed.failedStage)

        // Earlier stages emitted; later stages did not run.
        coVerify(exactly = 1) { initializeAppUseCase() }
        coVerify(exactly = 1) { loadModelUseCase() }
        coVerify(exactly = 0) { chatRepository.getSessionsFlow() }
        coVerify(exactly = 0) { memoryRepository.getRecentMemorySummaries(any()) }
    }

    @Test
    fun `given memory repository throws when invoked then terminates with Failed at LoadingMemory`() = runTest {
        coEvery { memoryRepository.getRecentMemorySummaries(any()) } throws RuntimeException("memory read failed")

        val emissions = useCase().toList()

        val terminal = emissions.last()
        assertTrue(terminal.stage is InitStage.Failed)
        val failed = terminal.stage as InitStage.Failed
        assertEquals(InitStage.LoadingMemory, failed.failedStage)
    }

    @Test
    fun `given pipelines and memory return data when invoked then prefetch reads exactly once`() = runTest {
        coEvery { pipelineRepository.getAllPipelines() } returns flowOf(
            listOf(PipelineGraph(id = "p1", name = "P1")),
        )
        coEvery { memoryRepository.getRecentMemorySummaries(any()) } returns listOf(
            MemorySummary(id = 1L, text = "x", timestamp = 0L),
        )

        useCase().toList()

        coVerify(exactly = 1) { pipelineRepository.getAllPipelines() }
        coVerify(exactly = 1) { memoryRepository.getRecentMemorySummaries(any()) }
    }
}
