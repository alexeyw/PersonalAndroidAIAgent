package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.services.EmbeddingProvider
import ai.agent.android.domain.services.EmbeddingProviderResolver
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RecomputePendingEmbeddingsUseCase] — the lazy re-embedding of
 * chunks imported under a different provider.
 */
class RecomputePendingEmbeddingsUseCaseTest {

    private lateinit var repository: MemoryRepository
    private lateinit var resolver: EmbeddingProviderResolver
    private lateinit var provider: EmbeddingProvider
    private lateinit var useCase: RecomputePendingEmbeddingsUseCase

    @Before
    fun setup() {
        repository = mockk()
        resolver = mockk()
        provider = mockk()
        useCase = RecomputePendingEmbeddingsUseCase(repository, resolver)
        coEvery { resolver.resolve() } returns provider
        coEvery { repository.markMemoryReembedded(any(), any()) } just Runs
    }

    private fun chunk(id: Long, text: String) =
        MemoryChunk(id = id, text = text, embedding = floatArrayOf(0f), timestamp = id)

    @Test
    fun `returns zero without touching the provider when nothing is pending`() = runTest {
        coEvery { repository.countMemoriesNeedingReembedding() } returns 0

        val repaired = useCase()

        assertEquals(0, repaired)
        coVerify(exactly = 0) { resolver.resolve() }
        coVerify(exactly = 0) { repository.getMemoriesNeedingReembedding() }
    }

    @Test
    fun `batch-embeds pending chunks and clears their flags`() = runTest {
        val pending = listOf(chunk(1, "alpha"), chunk(2, "beta"))
        coEvery { repository.countMemoriesNeedingReembedding() } returns 2
        coEvery { repository.getMemoriesNeedingReembedding() } returns pending
        val fresh = listOf(floatArrayOf(1f, 1f), floatArrayOf(2f, 2f))
        coEvery { provider.embed(listOf("alpha", "beta")) } returns fresh

        val repaired = useCase()

        assertEquals(2, repaired)
        coVerify(exactly = 1) { provider.embed(listOf("alpha", "beta")) }
        coVerify(exactly = 1) { repository.markMemoryReembedded(1L, fresh[0]) }
        coVerify(exactly = 1) { repository.markMemoryReembedded(2L, fresh[1]) }
    }

    @Test
    fun `returns zero when the batch embedding fails`() = runTest {
        coEvery { repository.countMemoriesNeedingReembedding() } returns 1
        coEvery { repository.getMemoriesNeedingReembedding() } returns listOf(chunk(1, "alpha"))
        coEvery { provider.embed(any<List<String>>()) } throws RuntimeException("offline")

        val repaired = useCase()

        assertEquals(0, repaired)
        coVerify(exactly = 0) { repository.markMemoryReembedded(any(), any()) }
    }
}
