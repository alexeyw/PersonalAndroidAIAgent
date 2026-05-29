package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProvider
import ai.agent.android.domain.services.EmbeddingProviderResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RetrieveRelevantMemoryUseCase].
 */
class RetrieveRelevantMemoryUseCaseTest {

    private lateinit var embeddingProviderResolver: EmbeddingProviderResolver
    private lateinit var provider: EmbeddingProvider
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: RetrieveRelevantMemoryUseCase

    private val searchPoolLimit = 1_000
    private val settingsTopK = 5
    private val settingsThreshold = 0.55f

    @Before
    fun setup() {
        embeddingProviderResolver = mockk()
        provider = mockk()
        memoryRepository = mockk()
        settingsRepository = mockk()
        useCase = RetrieveRelevantMemoryUseCase(embeddingProviderResolver, memoryRepository, settingsRepository)

        coEvery { embeddingProviderResolver.resolve() } returns provider
        coEvery { settingsRepository.maxMemoryChunksForSearch } returns flowOf(searchPoolLimit)
        coEvery { settingsRepository.memorySearchTopK } returns flowOf(settingsTopK)
        coEvery { settingsRepository.memorySearchThreshold } returns flowOf(settingsThreshold)
    }

    @Test
    fun `given three chunks when one clears the threshold then only that chunk is returned`() = runTest {
        val query = "where do I live?"
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { provider.embed(query) } returns queryEmbedding

        val relevant = MemoryChunk(1, "user lives in Berlin", floatArrayOf(1f, 0f, 0f), 3_000L)
        val weak = MemoryChunk(2, "user likes coffee", floatArrayOf(0f, 1f, 0f), 2_000L)
        val noise = MemoryChunk(3, "the sky is blue", floatArrayOf(0f, 0f, 1f), 1_000L)

        // The repository returns its ranked pool; the use case is responsible
        // for dropping everything below the configured threshold (0.55).
        coEvery { memoryRepository.findSimilarMemories(queryEmbedding, searchPoolLimit, settingsTopK) } returns listOf(
            relevant to 0.82f, // clears 0.55
            weak to 0.40f, // below threshold
            noise to 0.31f, // below threshold
        )

        val result = useCase(query)

        assertEquals(listOf(relevant), result)
    }

    @Test
    fun `invoke embeds the query with the active provider, not a fixed engine`() = runTest {
        val query = "test query"
        val queryEmbedding = floatArrayOf(0.1f, 0.2f)
        coEvery { provider.embed(query) } returns queryEmbedding
        coEvery {
            memoryRepository.findSimilarMemories(queryEmbedding, searchPoolLimit, settingsTopK)
        } returns emptyList()

        useCase(query)

        coVerify(exactly = 1) { embeddingProviderResolver.resolve() }
        coVerify(exactly = 1) { provider.embed(query) }
    }

    @Test
    fun `given no explicit params when invoked then top-K and threshold come from settings`() = runTest {
        val query = "q"
        val queryEmbedding = floatArrayOf(0.5f)
        coEvery { provider.embed(query) } returns queryEmbedding

        val chunk = MemoryChunk(1, "fact", floatArrayOf(0.5f), 1_000L)
        // 0.50 is below the settings threshold (0.55) and must be filtered out,
        // proving the threshold default is read from settings.
        coEvery { memoryRepository.findSimilarMemories(queryEmbedding, searchPoolLimit, settingsTopK) } returns listOf(
            chunk to 0.50f,
        )

        val result = useCase(query)

        assertEquals(emptyList<MemoryChunk>(), result)
        // top-K from settings is forwarded to the repository search.
        coVerify(exactly = 1) {
            memoryRepository.findSimilarMemories(queryEmbedding, searchPoolLimit, settingsTopK)
        }
    }

    @Test
    fun `explicit limit and threshold override the settings values`() = runTest {
        val query = "q"
        val queryEmbedding = floatArrayOf(0.5f)
        val overrideLimit = 2
        val overrideThreshold = 0.3f
        coEvery { provider.embed(query) } returns queryEmbedding

        val chunk = MemoryChunk(1, "fact", floatArrayOf(0.5f), 1_000L)
        coEvery {
            memoryRepository.findSimilarMemories(queryEmbedding, searchPoolLimit, overrideLimit)
        } returns listOf(chunk to 0.40f) // clears the 0.30 override but not the 0.55 default

        val result = useCase(query, limit = overrideLimit, threshold = overrideThreshold)

        assertEquals(listOf(chunk), result)
        coVerify(exactly = 1) {
            memoryRepository.findSimilarMemories(queryEmbedding, searchPoolLimit, overrideLimit)
        }
    }
}
