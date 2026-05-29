package ai.agent.android.domain.usecases

import ai.agent.android.domain.constants.TimeAndIdConstants
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProvider
import ai.agent.android.domain.services.EmbeddingProviderResolver
import ai.agent.android.domain.services.KMeansClusterer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemoryCompactionUseCase].
 *
 * Clustering is mocked ([KMeansClusterer]) so the use case's candidate-gating,
 * per-cluster consolidation, persistence, and resilience logic is exercised in
 * isolation from the k-means algorithm (covered by [KMeansClustererTest]).
 */
class MemoryCompactionUseCaseTest {

    private lateinit var llmInferenceEngine: LlmInferenceEngine
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var promptTemplateEngine: PromptTemplateEngine
    private lateinit var embeddingProviderResolver: EmbeddingProviderResolver
    private lateinit var embeddingProvider: EmbeddingProvider
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var kMeansClusterer: KMeansClusterer
    private lateinit var useCase: MemoryCompactionUseCase

    private val now = 1_000_000_000_000L

    private fun chunk(id: Long, text: String) =
        MemoryChunk(id = id, text = text, embedding = floatArrayOf(1f, 0f), timestamp = 1L)

    @Before
    fun setup() {
        llmInferenceEngine = mockk()
        loadModelUseCase = mockk()
        promptTemplateEngine = mockk()
        embeddingProviderResolver = mockk()
        embeddingProvider = mockk()
        memoryRepository = mockk()
        settingsRepository = mockk()
        kMeansClusterer = mockk()

        every { settingsRepository.memoryCompactionAgeDays } returns flowOf(30)
        every { settingsRepository.verboseMemoryLoggingEnabled } returns flowOf(false)
        coEvery { loadModelUseCase.invoke(any()) } returns Result.Success(Unit)
        coEvery { promptTemplateEngine.render(any(), any()) } answers { firstArg() }
        coEvery { embeddingProviderResolver.resolve() } returns embeddingProvider
        coEvery { embeddingProvider.embed(any<String>()) } returns floatArrayOf(0.5f, 0.5f)
        coEvery { memoryRepository.saveMemory(any(), any(), any(), any()) } returns 99L
        coEvery { memoryRepository.deleteMemory(any()) } returns Unit
        coEvery { settingsRepository.setMemoryLastCompactedAt(any()) } returns Unit
        every { llmInferenceEngine.generateResponseStream(any()) } returns flowOf("Merged fact")

        useCase = MemoryCompactionUseCase(
            llmInferenceEngine = llmInferenceEngine,
            loadModelUseCase = loadModelUseCase,
            promptTemplateEngine = promptTemplateEngine,
            promptVariableProviders = emptySet(),
            embeddingProviderResolver = embeddingProviderResolver,
            memoryRepository = memoryRepository,
            settingsRepository = settingsRepository,
            kMeansClusterer = kMeansClusterer,
        )
    }

    @Test
    fun `given fewer than three candidates when invoke then does nothing`() = runTest {
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns listOf(chunk(1, "a"), chunk(2, "b"))

        val outcome = useCase(now)

        assertEquals(MemoryCompactionUseCase.MemoryCompactionOutcome.EMPTY, outcome)
        coVerify(exactly = 0) { kMeansClusterer.cluster(any()) }
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any(), any()) }
    }

    @Test
    fun `given a dense cluster when invoke then consolidates and deletes originals`() = runTest {
        val candidates = listOf(chunk(1, "a"), chunk(2, "b"), chunk(3, "c"))
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns candidates
        every { kMeansClusterer.cluster(any()) } returns listOf(listOf(0, 1, 2))

        val outcome = useCase(now)

        assertEquals(1, outcome.clustersProcessed)
        assertEquals(3, outcome.chunksConsolidated)
        assertEquals(1, outcome.chunksCreated)
        coVerify(exactly = 1) {
            memoryRepository.saveMemory(
                "Merged fact",
                any(),
                MemorySource.Compaction(originalChunkIds = listOf(1L, 2L, 3L)),
            )
        }
        coVerify(exactly = 1) { memoryRepository.deleteMemory(1L) }
        coVerify(exactly = 1) { memoryRepository.deleteMemory(2L) }
        coVerify(exactly = 1) { memoryRepository.deleteMemory(3L) }
    }

    @Test
    fun `given verbose memory logging when a dense cluster is consolidated then the pass still succeeds`() = runTest {
        // Verbose logging adds a membership log line; it must not change behaviour.
        every { settingsRepository.verboseMemoryLoggingEnabled } returns flowOf(true)
        val candidates = listOf(chunk(1, "a"), chunk(2, "b"), chunk(3, "c"))
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns candidates
        every { kMeansClusterer.cluster(any()) } returns listOf(listOf(0, 1, 2))

        val outcome = useCase(now)

        assertEquals(1, outcome.clustersProcessed)
        assertEquals(3, outcome.chunksConsolidated)
        coVerify(exactly = 1) {
            memoryRepository.saveMemory(
                "Merged fact",
                any(),
                MemorySource.Compaction(originalChunkIds = listOf(1L, 2L, 3L)),
            )
        }
    }

    @Test
    fun `given only small clusters when invoke then leaves them untouched`() = runTest {
        val candidates = listOf(chunk(1, "a"), chunk(2, "b"), chunk(3, "c"), chunk(4, "d"))
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns candidates
        every { kMeansClusterer.cluster(any()) } returns listOf(listOf(0, 1), listOf(2, 3))

        val outcome = useCase(now)

        assertEquals(0, outcome.clustersProcessed)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any(), any()) }
        coVerify(exactly = 0) { memoryRepository.deleteMemory(any()) }
    }

    @Test
    fun `given a blank model reply when invoke then keeps originals`() = runTest {
        val candidates = listOf(chunk(1, "a"), chunk(2, "b"), chunk(3, "c"))
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns candidates
        every { kMeansClusterer.cluster(any()) } returns listOf(listOf(0, 1, 2))
        every { llmInferenceEngine.generateResponseStream(any()) } returns flowOf("   ")

        val outcome = useCase(now)

        assertEquals(0, outcome.clustersProcessed)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any(), any()) }
        coVerify(exactly = 0) { memoryRepository.deleteMemory(any()) }
    }

    @Test
    fun `given embedding failure when invoke then keeps originals`() = runTest {
        val candidates = listOf(chunk(1, "a"), chunk(2, "b"), chunk(3, "c"))
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns candidates
        every { kMeansClusterer.cluster(any()) } returns listOf(listOf(0, 1, 2))
        coEvery { embeddingProvider.embed(any<String>()) } throws RuntimeException("embed boom")

        val outcome = useCase(now)

        assertEquals(0, outcome.clustersProcessed)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any(), any()) }
        coVerify(exactly = 0) { memoryRepository.deleteMemory(any()) }
    }

    @Test
    fun `given model unavailable when invoke then does nothing`() = runTest {
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns
            listOf(chunk(1, "a"), chunk(2, "b"), chunk(3, "c"))
        coEvery { loadModelUseCase.invoke(any()) } returns
            Result.Error<Unit, AppError>(error = object : AppError.System {})

        val outcome = useCase(now)

        assertEquals(MemoryCompactionUseCase.MemoryCompactionOutcome.EMPTY, outcome)
        coVerify(exactly = 0) { kMeansClusterer.cluster(any()) }
    }

    @Test
    fun `given age window when invoke then queries candidates older than the cutoff`() = runTest {
        coEvery { memoryRepository.getCompactionCandidates(any()) } returns emptyList()

        useCase(now)

        val expectedCutoff = now - 30L * TimeAndIdConstants.MS_PER_DAY
        coVerify(exactly = 1) { memoryRepository.getCompactionCandidates(expectedCutoff) }
    }
}
