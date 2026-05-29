package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProvider
import ai.agent.android.domain.services.EmbeddingProviderResolver
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
 * Unit tests for [MemoryExtractionUseCase].
 *
 * Each test mocks the local model reply, the embedding provider, and the
 * repository so the parsing / dedup / persistence logic is exercised in
 * isolation from any real inference or storage.
 */
class MemoryExtractionUseCaseTest {

    private lateinit var llmInferenceEngine: LlmInferenceEngine
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var promptTemplateEngine: PromptTemplateEngine
    private lateinit var embeddingProviderResolver: EmbeddingProviderResolver
    private lateinit var embeddingProvider: EmbeddingProvider
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: MemoryExtractionUseCase

    private val sessionId = "session-1"
    private val messages = listOf(
        ChatMessage(id = 1, sessionId = sessionId, role = Role.USER, content = "I love dark mode", timestamp = 1L),
        ChatMessage(id = 2, sessionId = sessionId, role = Role.AGENT, content = "Noted!", timestamp = 2L),
    )

    @Before
    fun setup() {
        llmInferenceEngine = mockk()
        loadModelUseCase = mockk()
        promptTemplateEngine = mockk()
        embeddingProviderResolver = mockk()
        embeddingProvider = mockk()
        memoryRepository = mockk()
        settingsRepository = mockk()

        // Default happy-path plumbing; individual tests override the model reply.
        coEvery { loadModelUseCase.invoke(any()) } returns Result.Success(Unit)
        coEvery { promptTemplateEngine.render(any(), any()) } answers { firstArg() }
        coEvery { embeddingProviderResolver.resolve() } returns embeddingProvider
        // Default batch stub: one orthogonal (one-hot) vector per fact so
        // facts are distinct by default. Index-aligned with the input list,
        // matching the EmbeddingProvider.embed(List) contract.
        coEvery { embeddingProvider.embed(any<List<String>>()) } answers {
            val texts = firstArg<List<String>>()
            texts.indices.map { i -> FloatArray(texts.size) { if (it == i) 1f else 0f } }
        }
        every { settingsRepository.maxMemoryChunksForSearch } returns flowOf(1000)
        coEvery { memoryRepository.findSimilarMemories(any(), any(), any()) } returns emptyList()
        coEvery { memoryRepository.saveMemory(any(), any(), any()) } returns 1L

        useCase = MemoryExtractionUseCase(
            llmInferenceEngine = llmInferenceEngine,
            loadModelUseCase = loadModelUseCase,
            promptTemplateEngine = promptTemplateEngine,
            promptVariableProviders = emptySet(),
            embeddingProviderResolver = embeddingProviderResolver,
            memoryRepository = memoryRepository,
            settingsRepository = settingsRepository,
        )
    }

    private fun stubReply(reply: String) {
        every { llmInferenceEngine.generateResponseStream(any()) } returns flowOf(reply)
    }

    @Test
    fun `given valid facts when invoke then saves each with ChatSession source`() = runTest {
        stubReply(
            """[
              {"type": "preference", "text": "Prefers dark mode"},
              {"type": "relation", "text": "Has a brother named Alex"}
            ]""",
        )
        // Default batch stub yields distinct (orthogonal) vectors per fact, so
        // neither is collapsed as a within-pass duplicate.

        val outcome = useCase(sessionId, messages)

        assertEquals(2, outcome.parsed)
        assertEquals(2, outcome.saved)
        assertEquals(0, outcome.skippedDuplicates)
        coVerify { memoryRepository.saveMemory("Prefers dark mode", any(), MemorySource.ChatSession(sessionId)) }
        coVerify { memoryRepository.saveMemory("Has a brother named Alex", any(), MemorySource.ChatSession(sessionId)) }
    }

    @Test
    fun `given empty array reply when invoke then saves nothing`() = runTest {
        stubReply("[]")

        val outcome = useCase(sessionId, messages)

        assertEquals(0, outcome.parsed)
        assertEquals(0, outcome.saved)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any()) }
    }

    @Test
    fun `given malformed reply when invoke then saves nothing and does not throw`() = runTest {
        stubReply("Sorry, I cannot help with that.")

        val outcome = useCase(sessionId, messages)

        assertEquals(0, outcome.parsed)
        assertEquals(0, outcome.saved)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any()) }
    }

    @Test
    fun `given facts with unknown type or blank text when invoke then drops them`() = runTest {
        stubReply(
            """[
              {"type": "preference", "text": "Likes tea"},
              {"type": "gossip", "text": "Something irrelevant"},
              {"type": "event", "text": "   "}
            ]""",
        )

        val outcome = useCase(sessionId, messages)

        assertEquals(1, outcome.parsed)
        assertEquals(1, outcome.saved)
        coVerify(exactly = 1) { memoryRepository.saveMemory("Likes tea", any(), any()) }
    }

    @Test
    fun `given a near-duplicate of an existing chunk when invoke then skips it`() = runTest {
        stubReply("""[{"type": "preference", "text": "Prefers dark mode"}]""")
        // The stored-chunk search reports a 0.95 similarity (>= 0.92 threshold).
        coEvery { memoryRepository.findSimilarMemories(any(), any(), any()) } returns
            listOf(mockk<MemoryChunk>() to 0.95f)

        val outcome = useCase(sessionId, messages)

        assertEquals(1, outcome.parsed)
        assertEquals(0, outcome.saved)
        assertEquals(1, outcome.skippedDuplicates)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any()) }
    }

    @Test
    fun `given two near-identical facts in one pass when invoke then saves only the first`() = runTest {
        stubReply(
            """[
              {"type": "preference", "text": "Prefers dark mode"},
              {"type": "preference", "text": "Prefers dark mode too"}
            ]""",
        )
        // Both facts embed to the same vector, so the second collides with the first within the pass.
        coEvery { embeddingProvider.embed(any<List<String>>()) } returns
            listOf(floatArrayOf(0.5f, 0.5f), floatArrayOf(0.5f, 0.5f))

        val outcome = useCase(sessionId, messages)

        assertEquals(2, outcome.parsed)
        assertEquals(1, outcome.saved)
        assertEquals(1, outcome.skippedDuplicates)
        coVerify(exactly = 1) { memoryRepository.saveMemory(any(), any(), any()) }
    }

    @Test
    fun `given model cannot be loaded when invoke then returns empty without inference`() = runTest {
        coEvery { loadModelUseCase.invoke(any()) } returns
            Result.Error(error = object : AppError.System {}, message = "no model")

        val outcome = useCase(sessionId, messages)

        assertEquals(MemoryExtractionUseCase.MemoryExtractionOutcome.EMPTY, outcome)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any()) }
    }

    @Test
    fun `given too few messages when invoke then returns empty without loading model`() = runTest {
        val outcome = useCase(sessionId, listOf(messages.first()))

        assertEquals(MemoryExtractionUseCase.MemoryExtractionOutcome.EMPTY, outcome)
        coVerify(exactly = 0) { loadModelUseCase.invoke(any()) }
    }

    @Test
    fun `given batch embedding fails when invoke then saves nothing and does not throw`() = runTest {
        stubReply(
            """[
              {"type": "preference", "text": "Likes tea"},
              {"type": "preference", "text": "Likes coffee"}
            ]""",
        )
        // The batch embed endpoint is all-or-nothing; a failure drops the pass.
        coEvery { embeddingProvider.embed(any<List<String>>()) } throws RuntimeException("boom")

        val outcome = useCase(sessionId, messages)

        assertEquals(2, outcome.parsed)
        assertEquals(0, outcome.saved)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any(), any()) }
    }
}
