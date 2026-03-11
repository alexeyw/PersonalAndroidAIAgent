package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RetrieveRelevantMemoryUseCase].
 */
class RetrieveRelevantMemoryUseCaseTest {

    private lateinit var textEmbeddingEngine: TextEmbeddingEngine
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var useCase: RetrieveRelevantMemoryUseCase

    @Before
    fun setup() {
        textEmbeddingEngine = mockk()
        memoryRepository = mockk()
        useCase = RetrieveRelevantMemoryUseCase(textEmbeddingEngine, memoryRepository)
    }

    @Test
    fun `invoke returns filtered and correctly mapped chunks`() = runTest {
        val query = "test query"
        val queryEmbedding = floatArrayOf(0.1f, 0.2f)
        val limit = 3
        val threshold = 0.5f

        val chunk1 = MemoryChunk(1, "Text 1", floatArrayOf(0.1f, 0.2f), 1000L)
        val chunk2 = MemoryChunk(2, "Text 2", floatArrayOf(0.0f, 0.0f), 2000L)

        // Mock dependencies
        coEvery { textEmbeddingEngine.generateEmbedding(query) } returns queryEmbedding
        coEvery { memoryRepository.findSimilarMemories(queryEmbedding, limit) } returns listOf(
            Pair(chunk1, 0.9f), // Should pass threshold
            Pair(chunk2, 0.2f)  // Should fail threshold
        )

        // Execute
        val result = useCase(query, limit, threshold)

        // Verify
        assertEquals(1, result.size)
        assertEquals(chunk1, result[0])

        coVerify(exactly = 1) { textEmbeddingEngine.generateEmbedding(query) }
        coVerify(exactly = 1) { memoryRepository.findSimilarMemories(queryEmbedding, limit) }
    }
}
