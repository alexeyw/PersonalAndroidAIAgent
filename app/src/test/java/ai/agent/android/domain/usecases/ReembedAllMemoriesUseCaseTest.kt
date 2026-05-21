package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReembedAllMemoriesUseCaseTest {

    @Test
    fun `progress flows from 0 to 1 inclusive`() = runTest {
        val repo = mockk<MemoryRepository>(relaxed = true)
        val embedder = mockk<TextEmbeddingEngine>()
        coEvery { repo.getAllMemories() } returns listOf(
            MemoryChunk(id = 1, text = "alpha", embedding = floatArrayOf(0f), timestamp = 0L),
            MemoryChunk(id = 2, text = "beta", embedding = floatArrayOf(0f), timestamp = 0L),
        )
        coEvery { embedder.generateEmbedding(any()) } returns floatArrayOf(1f)

        val emissions = ReembedAllMemoriesUseCase(repo, embedder)().toList()
        assertEquals(listOf(0f, 0.5f, 1f), emissions)
        coVerify(exactly = 2) { repo.updateMemory(any(), any(), any()) }
    }

    @Test
    fun `empty memory emits 1 and skips work`() = runTest {
        val repo = mockk<MemoryRepository>(relaxed = true)
        val embedder = mockk<TextEmbeddingEngine>(relaxed = true)
        coEvery { repo.getAllMemories() } returns emptyList()
        val emissions = ReembedAllMemoriesUseCase(repo, embedder)().toList()
        assertEquals(listOf(1f), emissions)
    }
}
