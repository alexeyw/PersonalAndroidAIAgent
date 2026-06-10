package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.MemoryChunk
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.EmbeddingProvider
import app.knotwork.android.domain.services.EmbeddingProviderResolver
import app.knotwork.android.domain.services.MemoryReranker
import app.knotwork.android.domain.services.MemorySearchStatsTracker
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
 *
 * A real [MemoryReranker] is used (it is a pure, dependency-free service) so
 * these double as integration coverage of the embed → search → re-rank → top-K
 * pipeline. Chunks are timestamped at "now" so recency decay leaves their
 * scores intact and the threshold assertions stay deterministic.
 */
class RetrieveRelevantMemoryUseCaseTest {

    private lateinit var embeddingProviderResolver: EmbeddingProviderResolver
    private lateinit var provider: EmbeddingProvider
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var memorySearchStatsTracker: MemorySearchStatsTracker
    private lateinit var useCase: RetrieveRelevantMemoryUseCase

    private val settingsTopK = 5
    private val settingsThreshold = 0.55f
    private val settingsHalfLifeDays = 30
    private val now = System.currentTimeMillis()

    @Before
    fun setup() {
        embeddingProviderResolver = mockk()
        provider = mockk()
        memoryRepository = mockk()
        settingsRepository = mockk()
        memorySearchStatsTracker = mockk(relaxed = true)
        useCase = RetrieveRelevantMemoryUseCase(
            embeddingProviderResolver,
            memoryRepository,
            MemoryReranker(),
            settingsRepository,
            memorySearchStatsTracker,
        )

        coEvery { embeddingProviderResolver.resolve() } returns provider
        coEvery { settingsRepository.memorySearchTopK } returns flowOf(settingsTopK)
        coEvery { settingsRepository.memorySearchThreshold } returns flowOf(settingsThreshold)
        coEvery { settingsRepository.memoryRecencyHalfLifeDays } returns flowOf(settingsHalfLifeDays)
    }

    private fun chunk(id: Long, text: String, embedding: FloatArray, isPinned: Boolean = false) =
        MemoryChunk(id = id, text = text, embedding = embedding, timestamp = now, isPinned = isPinned)

    @Test
    fun `given three chunks when one clears the threshold then only that chunk is returned`() = runTest {
        val query = "where do I live?"
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { provider.embed(query) } returns queryEmbedding

        val relevant = chunk(1, "user lives in Berlin", floatArrayOf(1f, 0f, 0f))
        val weak = chunk(2, "user likes coffee", floatArrayOf(0f, 1f, 0f))
        val noise = chunk(3, "the sky is blue", floatArrayOf(0f, 0f, 1f))

        // The use case asks for the full scored pool (limit == null) and the
        // re-ranker drops everything below the configured threshold.
        coEvery {
            memoryRepository.findSimilarMemories(queryEmbedding, limit = null)
        } returns listOf(
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
            memoryRepository.findSimilarMemories(queryEmbedding, limit = null)
        } returns emptyList()

        useCase(query)

        coVerify(exactly = 1) { embeddingProviderResolver.resolve() }
        coVerify(exactly = 1) { provider.embed(query) }
    }

    @Test
    fun `given no explicit params when invoked then top-K, threshold and half-life come from settings`() = runTest {
        val query = "q"
        val queryEmbedding = floatArrayOf(0.5f)
        coEvery { provider.embed(query) } returns queryEmbedding

        val chunk = chunk(1, "fact", floatArrayOf(0.5f))
        // 0.50 is below the settings threshold (0.55) and must be filtered out,
        // proving the threshold default is read from settings.
        coEvery {
            memoryRepository.findSimilarMemories(queryEmbedding, limit = null)
        } returns listOf(chunk to 0.50f)

        val result = useCase(query)

        assertEquals(emptyList<MemoryChunk>(), result)
        coVerify(exactly = 1) { settingsRepository.memoryRecencyHalfLifeDays }
        coVerify(exactly = 1) {
            memoryRepository.findSimilarMemories(queryEmbedding, limit = null)
        }
    }

    @Test
    fun `explicit limit and threshold override the settings values`() = runTest {
        val query = "q"
        val queryEmbedding = floatArrayOf(0.5f)
        val overrideLimit = 2
        val overrideThreshold = 0.3f
        coEvery { provider.embed(query) } returns queryEmbedding

        val chunk = chunk(1, "fact", floatArrayOf(0.5f))
        coEvery {
            memoryRepository.findSimilarMemories(queryEmbedding, limit = null)
        } returns listOf(chunk to 0.40f) // clears the 0.30 override but not the 0.55 default

        val result = useCase(query, limit = overrideLimit, threshold = overrideThreshold)

        assertEquals(listOf(chunk), result)
    }

    @Test
    fun `retrieveScored preserves the final scores and order, while invoke drops them`() = runTest {
        val query = "where do I live?"
        val queryEmbedding = floatArrayOf(1f, 0f, 0f)
        coEvery { provider.embed(query) } returns queryEmbedding

        val first = chunk(1, "user lives in Berlin", floatArrayOf(1f, 0f, 0f))
        val second = chunk(2, "user moved from Munich", floatArrayOf(0.9f, 0.1f, 0f))
        coEvery {
            memoryRepository.findSimilarMemories(queryEmbedding, limit = null)
        } returns listOf(first to 0.95f, second to 0.80f)

        val scored = useCase.retrieveScored(query)

        // The scored variant keeps the (chunk, score) pairs best-first.
        assertEquals(listOf(first, second), scored.map { it.first })
        assertEquals(0.95f, scored[0].second, 1e-4f)
        assertEquals(0.80f, scored[1].second, 1e-4f)
        // The score-free façade returns the same chunks in the same order.
        assertEquals(listOf(first, second), useCase(query))
    }

    @Test
    fun `given a pinned chunk when reranked then it is promoted above a stronger non-pinned chunk`() = runTest {
        val query = "q"
        val queryEmbedding = floatArrayOf(0.5f)
        coEvery { provider.embed(query) } returns queryEmbedding

        val pinned = chunk(1, "pinned fact", floatArrayOf(0.5f), isPinned = true)
        val strong = chunk(2, "strong but unpinned", floatArrayOf(0.5f))
        coEvery {
            memoryRepository.findSimilarMemories(queryEmbedding, limit = null)
        } returns listOf(strong to 0.90f, pinned to 0.30f)

        // top-K of 1 is applied AFTER re-ranking, so the pinned chunk wins the
        // single slot despite its lower raw similarity.
        val result = useCase(query, limit = 1)

        assertEquals(listOf(pinned), result)
    }

    @Test
    fun `given a search when invoked then raw similarity scores are recorded in the stats tracker`() = runTest {
        val query = "q"
        val queryEmbedding = floatArrayOf(0.5f)
        coEvery { provider.embed(query) } returns queryEmbedding

        val first = chunk(1, "fact", floatArrayOf(0.5f))
        val second = chunk(2, "other", floatArrayOf(0.4f))
        coEvery {
            memoryRepository.findSimilarMemories(queryEmbedding, limit = null)
        } returns listOf(first to 0.90f, second to 0.20f)

        useCase(query)

        // Raw (pre-rerank) scores feed the AVG SCORE stat, best-first.
        coVerify(exactly = 1) { memorySearchStatsTracker.record(listOf(0.90f, 0.20f)) }
    }
}
