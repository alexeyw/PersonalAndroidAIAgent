package ai.agent.android.data.repositories

import ai.agent.android.data.local.Converters
import ai.agent.android.data.local.dao.MemoryDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemoryRepositoryImpl].
 */
class MemoryRepositoryImplTest {

    private lateinit var memoryDao: MemoryDao
    private lateinit var converters: Converters
    private lateinit var repository: MemoryRepositoryImpl

    @Before
    fun setup() {
        memoryDao = mockk(relaxed = true)
        converters = Converters()
        repository = MemoryRepositoryImpl(memoryDao, converters)
    }

    @Test
    fun `cosineSimilarity calculates correct similarity for identical vectors`() {
        val vectorA = floatArrayOf(1f, 2f, 3f)
        val vectorB = floatArrayOf(1f, 2f, 3f)

        val similarity = repository.cosineSimilarity(vectorA, vectorB)

        // Should be exactly 1.0 (with slight float precision allowance)
        assertEquals(1.0f, similarity, 0.0001f)
    }

    @Test
    fun `cosineSimilarity calculates correct similarity for orthogonal vectors`() {
        val vectorA = floatArrayOf(1f, 0f, 0f)
        val vectorB = floatArrayOf(0f, 1f, 0f)

        val similarity = repository.cosineSimilarity(vectorA, vectorB)

        // Should be 0.0
        assertEquals(0.0f, similarity, 0.0001f)
    }

    @Test
    fun `cosineSimilarity calculates correct similarity for opposite vectors`() {
        val vectorA = floatArrayOf(1f, 1f)
        val vectorB = floatArrayOf(-1f, -1f)

        val similarity = repository.cosineSimilarity(vectorA, vectorB)

        // Should be -1.0
        assertEquals(-1.0f, similarity, 0.0001f)
    }

    @Test
    fun `cosineSimilarity returns 0 for empty vectors`() {
        val vectorA = floatArrayOf()
        val vectorB = floatArrayOf()

        val similarity = repository.cosineSimilarity(vectorA, vectorB)

        assertEquals(0.0f, similarity, 0.0f)
    }

    @Test
    fun `cosineSimilarity returns 0 for zero vectors`() {
        val vectorA = floatArrayOf(0f, 0f)
        val vectorB = floatArrayOf(0f, 0f)

        val similarity = repository.cosineSimilarity(vectorA, vectorB)

        assertEquals(0.0f, similarity, 0.0f)
    }

    @Test
    fun `findSimilarMemories uses getRecentMemories and returns correct pairs`() = kotlinx.coroutines.test.runTest {
        val queryEmbedding = floatArrayOf(1f, 0f)
        val entity1 = ai.agent.android.data.local.models.MemoryChunkEntity(1, "Text 1", "1.0,0.0", 1000L)
        val entity2 = ai.agent.android.data.local.models.MemoryChunkEntity(2, "Text 2", "0.0,1.0", 2000L)

        io.mockk.coEvery { memoryDao.getRecentMemories(100) } returns listOf(entity1, entity2)

        val results = repository.findSimilarMemories(queryEmbedding, searchPoolLimit = 100, limit = 2)

        assertEquals(2, results.size)
        assertEquals(1L, results[0].first.id) // Most similar first
        assertEquals(1.0f, results[0].second, 0.001f)
        assertEquals(2L, results[1].first.id)
        assertEquals(0.0f, results[1].second, 0.001f)
    }

    @Test
    fun `compactMemory calls dao deleteOldestMemories`() = kotlinx.coroutines.test.runTest {
        repository.compactMemory(500)
        io.mockk.coVerify(exactly = 1) { memoryDao.deleteOldestMemories(500) }
    }

    @Test
    fun `getRecentMemorySummaries forwards limit to dao and returns projections`() = kotlinx.coroutines.test.runTest {
        val expected = listOf(
            ai.agent.android.domain.models.MemorySummary(id = 2L, text = "newer", timestamp = 200L),
            ai.agent.android.domain.models.MemorySummary(id = 1L, text = "older", timestamp = 100L),
        )
        io.mockk.coEvery { memoryDao.getRecentMemorySummaries(2) } returns expected

        val result = repository.getRecentMemorySummaries(2)

        assertEquals(expected, result)
        io.mockk.coVerify(exactly = 1) { memoryDao.getRecentMemorySummaries(2) }
    }

    @Test
    fun `getRecentMemorySummaries with non positive limit short-circuits without dao call`() =
        kotlinx.coroutines.test.runTest {
            val result = repository.getRecentMemorySummaries(0)

            assertEquals(emptyList<ai.agent.android.domain.models.MemorySummary>(), result)
            io.mockk.coVerify(exactly = 0) { memoryDao.getRecentMemorySummaries(any()) }
        }
}
