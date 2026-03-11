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
}
