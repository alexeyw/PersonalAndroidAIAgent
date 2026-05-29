package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.services.EmbeddingProvider
import ai.agent.android.domain.services.EmbeddingProviderResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReembedAllMemoriesUseCaseTest {

    @Test
    fun `progress flows from 0 to 1 inclusive`() = runTest {
        val repo = mockk<MemoryRepository>(relaxed = true)
        val resolver = mockk<EmbeddingProviderResolver>()
        val provider = mockk<EmbeddingProvider>()
        coEvery { resolver.resolve() } returns provider
        coEvery { repo.getAllMemories() } returns listOf(
            MemoryChunk(id = 1, text = "alpha", embedding = floatArrayOf(0f), timestamp = 0L),
            MemoryChunk(id = 2, text = "beta", embedding = floatArrayOf(0f), timestamp = 0L),
        )
        coEvery { provider.embed(any<String>()) } returns floatArrayOf(1f)

        val emissions = ReembedAllMemoriesUseCase(repo, resolver)().toList()
        assertEquals(listOf(0f, 0.5f, 1f), emissions)
        coVerify(exactly = 2) { repo.updateMemory(any(), any(), any()) }
    }

    @Test
    fun `re-embed resolves the active provider once for the whole batch`() = runTest {
        val repo = mockk<MemoryRepository>(relaxed = true)
        val resolver = mockk<EmbeddingProviderResolver>()
        val provider = mockk<EmbeddingProvider>()
        coEvery { resolver.resolve() } returns provider
        coEvery { repo.getAllMemories() } returns listOf(
            MemoryChunk(id = 1, text = "alpha", embedding = floatArrayOf(0f), timestamp = 0L),
            MemoryChunk(id = 2, text = "beta", embedding = floatArrayOf(0f), timestamp = 0L),
            MemoryChunk(id = 3, text = "gamma", embedding = floatArrayOf(0f), timestamp = 0L),
        )
        coEvery { provider.embed(any<String>()) } returns floatArrayOf(1f)

        ReembedAllMemoriesUseCase(repo, resolver)().toList()

        // The provider is resolved a single time up front, not per chunk.
        coVerify(exactly = 1) { resolver.resolve() }
        coVerify(exactly = 3) { provider.embed(any<String>()) }
    }

    @Test
    fun `embed failure on a chunk is logged and skipped without halting`() = runTest {
        val repo = mockk<MemoryRepository>(relaxed = true)
        val resolver = mockk<EmbeddingProviderResolver>()
        val provider = mockk<EmbeddingProvider>()
        coEvery { resolver.resolve() } returns provider
        coEvery { repo.getAllMemories() } returns listOf(
            MemoryChunk(id = 1, text = "alpha", embedding = floatArrayOf(0f), timestamp = 0L),
        )
        coEvery { provider.embed(any<String>()) } throws RuntimeException("transient backend error")

        val emissions = ReembedAllMemoriesUseCase(repo, resolver)().toList()

        // Progress still reaches 1f; the failed chunk is simply not written.
        assertEquals(listOf(0f, 1f), emissions)
        coVerify(exactly = 0) { repo.updateMemory(any(), any(), any()) }
    }

    @Test
    fun `cancellation during embed propagates and halts the batch`() = runTest {
        val repo = mockk<MemoryRepository>(relaxed = true)
        val resolver = mockk<EmbeddingProviderResolver>()
        val provider = mockk<EmbeddingProvider>()
        coEvery { resolver.resolve() } returns provider
        coEvery { repo.getAllMemories() } returns listOf(
            MemoryChunk(id = 1, text = "alpha", embedding = floatArrayOf(0f), timestamp = 0L),
            MemoryChunk(id = 2, text = "beta", embedding = floatArrayOf(0f), timestamp = 0L),
        )
        coEvery { provider.embed(any<String>()) } throws CancellationException("cancelled")

        var thrown: Throwable? = null
        try {
            ReembedAllMemoriesUseCase(repo, resolver)().toList()
        } catch (e: CancellationException) {
            thrown = e
        }

        // CancellationException must not be swallowed by runCatching.
        assertTrue("Expected CancellationException to propagate", thrown is CancellationException)
        coVerify(exactly = 0) { repo.updateMemory(any(), any(), any()) }
    }

    @Test
    fun `empty memory emits 1 and skips work`() = runTest {
        val repo = mockk<MemoryRepository>(relaxed = true)
        val resolver = mockk<EmbeddingProviderResolver>(relaxed = true)
        coEvery { repo.getAllMemories() } returns emptyList()
        val emissions = ReembedAllMemoriesUseCase(repo, resolver)().toList()
        assertEquals(listOf(1f), emissions)
    }
}
