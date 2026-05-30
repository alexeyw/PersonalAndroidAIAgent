package ai.agent.android.data.repositories

import ai.agent.android.data.local.Converters
import ai.agent.android.data.local.dao.MemoryDao
import ai.agent.android.data.local.models.MemoryChunkEntity
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.models.MemorySummary
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
        val entity1 = MemoryChunkEntity(1, "Text 1", "1.0,0.0", 1000L)
        val entity2 = MemoryChunkEntity(2, "Text 2", "0.0,1.0", 2000L)

        io.mockk.coEvery { memoryDao.getRecentMemories(100) } returns listOf(entity1, entity2)

        val results = repository.findSimilarMemories(queryEmbedding, searchPoolLimit = 100, limit = 2)

        assertEquals(2, results.size)
        assertEquals(1L, results[0].first.id) // Most similar first
        assertEquals(1.0f, results[0].second, 0.001f)
        assertEquals(2L, results[1].first.id)
        assertEquals(0.0f, results[1].second, 0.001f)
    }

    @Test
    fun `given three chunks when query is close to one then that chunk ranks first by real cosine`() =
        kotlinx.coroutines.test.runTest {
            // Query points along the first axis; only the "Berlin" chunk lies
            // near it, so the real cosine math must surface it on top with a
            // near-1.0 score while the orthogonal/noisy chunks score far lower.
            val queryEmbedding = floatArrayOf(1f, 0f, 0f)
            val berlin = MemoryChunkEntity(1, "user lives in Berlin", "0.9,0.1,0.0", 3000L)
            val coffee = MemoryChunkEntity(2, "user likes coffee", "0.0,1.0,0.0", 2000L)
            val sky = MemoryChunkEntity(3, "the sky is blue", "0.2,0.2,0.95", 1000L)

            io.mockk.coEvery { memoryDao.getRecentMemories(100) } returns listOf(coffee, sky, berlin)

            val results = repository.findSimilarMemories(queryEmbedding, searchPoolLimit = 100, limit = 3)

            assertEquals(3, results.size)
            // Highest-similarity chunk first.
            assertEquals(1L, results[0].first.id)
            assertEquals("user lives in Berlin", results[0].first.text)
            assertEquals(0.994f, results[0].second, 0.01f)
            // The orthogonal "coffee" chunk scores ~0 and ranks last.
            assertEquals(2L, results[2].first.id)
            assertEquals(0.0f, results[2].second, 0.001f)
        }

    @Test
    fun `compactMemory calls dao deleteOldestMemories`() = kotlinx.coroutines.test.runTest {
        repository.compactMemory(500)
        io.mockk.coVerify(exactly = 1) { memoryDao.deleteOldestMemories(500) }
    }

    @Test
    fun `getRecentMemorySummaries forwards limit to dao and returns projections`() = kotlinx.coroutines.test.runTest {
        val expected = listOf(
            MemorySummary(id = 2L, text = "newer", timestamp = 200L),
            MemorySummary(id = 1L, text = "older", timestamp = 100L),
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

            assertEquals(emptyList<MemorySummary>(), result)
            io.mockk.coVerify(exactly = 0) { memoryDao.getRecentMemorySummaries(any()) }
        }

    @Test
    fun `updateMemory serializes embedding and forwards to dao`() = kotlinx.coroutines.test.runTest {
        val newEmbedding = floatArrayOf(0.25f, -0.5f, 0.75f)
        val expectedSerialized = converters.fromFloatArray(newEmbedding)

        repository.updateMemory(id = 42L, text = "edited text", embedding = newEmbedding)

        io.mockk.coVerify(exactly = 1) {
            memoryDao.updateMemory(id = 42L, text = "edited text", embedding = expectedSerialized!!)
        }
    }

    @Test
    fun `setMemoryPinned forwards pinned true to dao`() = kotlinx.coroutines.test.runTest {
        repository.setMemoryPinned(id = 7L, pinned = true)
        io.mockk.coVerify(exactly = 1) { memoryDao.setMemoryPinned(id = 7L, isPinned = true) }
    }

    @Test
    fun `setMemoryPinned forwards pinned false to dao`() = kotlinx.coroutines.test.runTest {
        repository.setMemoryPinned(id = 7L, pinned = false)
        io.mockk.coVerify(exactly = 1) { memoryDao.setMemoryPinned(id = 7L, isPinned = false) }
    }

    @Test
    fun `getCompactionCandidates forwards cutoff and maps entities`() = kotlinx.coroutines.test.runTest {
        val a = MemoryChunkEntity(1, "older fact", "1.0,0.0", 500L)
        val b = MemoryChunkEntity(2, "another fact", "0.0,1.0", 400L)
        io.mockk.coEvery { memoryDao.getCompactionCandidates(1000L) } returns listOf(a, b)

        val result = repository.getCompactionCandidates(olderThanMillis = 1000L)

        assertEquals(2, result.size)
        assertEquals(listOf(1L, 2L), result.map { it.id })
        io.mockk.coVerify(exactly = 1) { memoryDao.getCompactionCandidates(1000L) }
    }

    @Test
    fun `countMemories forwards to dao`() = kotlinx.coroutines.test.runTest {
        io.mockk.coEvery { memoryDao.countMemories() } returns 42

        val result = repository.countMemories()

        assertEquals(42, result)
        io.mockk.coVerify(exactly = 1) { memoryDao.countMemories() }
    }

    @Test
    fun `getExistingMemoryIds returns the dao id set`() = kotlinx.coroutines.test.runTest {
        io.mockk.coEvery { memoryDao.getAllIds() } returns listOf(1L, 2L, 3L)

        val ids = repository.getExistingMemoryIds()

        assertEquals(setOf(1L, 2L, 3L), ids)
    }

    @Test
    fun `insertImportedMemories preserves id, provenance, pin state and flags re-embedding`() =
        kotlinx.coroutines.test.runTest {
            val captured = io.mockk.slot<List<MemoryChunkEntity>>()
            io.mockk.coEvery { memoryDao.insertMemories(capture(captured)) } returns Unit
            val chunk = MemoryChunk(
                id = 9,
                text = "imported",
                embedding = floatArrayOf(0.1f, 0.2f),
                timestamp = 1_234L,
                isPinned = true,
                source = MemorySource.Manual,
                tags = listOf("preference"),
            )

            repository.insertImportedMemories(listOf(chunk), needsReembedding = true)

            val entity = captured.captured.single()
            assertEquals(9L, entity.id)
            assertEquals("imported", entity.text)
            assertEquals(1_234L, entity.timestamp)
            assertEquals(true, entity.isPinned)
            assertEquals(MemorySource.Manual, entity.source)
            assertEquals("preference", entity.tagsCsv)
            assertEquals(true, entity.needsReembedding)
            assertEquals(converters.fromFloatArray(floatArrayOf(0.1f, 0.2f)), entity.embedding)
        }

    @Test
    fun `insertImportedMemories is a no-op for an empty list`() = kotlinx.coroutines.test.runTest {
        repository.insertImportedMemories(emptyList(), needsReembedding = false)
        io.mockk.coVerify(exactly = 0) { memoryDao.insertMemories(any()) }
    }

    @Test
    fun `countMemoriesNeedingReembedding forwards to dao`() = kotlinx.coroutines.test.runTest {
        io.mockk.coEvery { memoryDao.countNeedingReembedding() } returns 4

        assertEquals(4, repository.countMemoriesNeedingReembedding())
    }

    @Test
    fun `markMemoryReembedded serializes embedding and forwards to dao`() = kotlinx.coroutines.test.runTest {
        val embedding = floatArrayOf(0.5f, 0.6f)
        val expected = converters.fromFloatArray(embedding)

        repository.markMemoryReembedded(id = 3L, embedding = embedding)

        io.mockk.coVerify(exactly = 1) { memoryDao.markReembedded(id = 3L, embedding = expected!!) }
    }
}
