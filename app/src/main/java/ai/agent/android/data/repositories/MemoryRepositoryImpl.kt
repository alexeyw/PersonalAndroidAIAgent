package ai.agent.android.data.repositories

import ai.agent.android.data.local.Converters
import ai.agent.android.data.local.dao.MemoryDao
import ai.agent.android.data.local.models.MemoryChunkEntity
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySummary
import ai.agent.android.domain.repositories.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Implementation of [MemoryRepository] that uses Room as a local storage and performs
 * in-memory cosine similarity search for vector embeddings.
 */
@Singleton
class MemoryRepositoryImpl @Inject constructor(private val memoryDao: MemoryDao, private val converters: Converters) :
    MemoryRepository {

    override suspend fun saveMemory(text: String, embedding: FloatArray): Long = withContext(Dispatchers.IO) {
        val embeddingString = converters.fromFloatArray(embedding)
            ?: throw IllegalArgumentException("Failed to serialize embedding")

        val entity = MemoryChunkEntity(
            text = text,
            embedding = embeddingString,
            timestamp = System.currentTimeMillis(),
        )
        memoryDao.insertMemory(entity)
    }

    override suspend fun getAllMemories(): List<MemoryChunk> = withContext(Dispatchers.IO) {
        memoryDao.getAllMemories().mapNotNull { entity ->
            val embeddingArray = converters.toFloatArray(entity.embedding)
            if (embeddingArray != null) {
                MemoryChunk(
                    id = entity.id,
                    text = entity.text,
                    embedding = embeddingArray,
                    timestamp = entity.timestamp,
                    isPinned = entity.isPinned,
                )
            } else {
                null
            }
        }
    }

    override suspend fun getRecentMemorySummaries(limit: Int): List<MemorySummary> = if (limit <= 0) {
        emptyList()
    } else {
        withContext(Dispatchers.IO) {
            memoryDao.getRecentMemorySummaries(limit)
        }
    }

    override suspend fun findSimilarMemories(
        queryEmbedding: FloatArray,
        searchPoolLimit: Int,
        limit: Int,
    ): List<Pair<MemoryChunk, Float>> = withContext(Dispatchers.Default) {
        val recentMemories = memoryDao.getRecentMemories(searchPoolLimit).mapNotNull { entity ->
            val embeddingArray = converters.toFloatArray(entity.embedding)
            if (embeddingArray != null) {
                MemoryChunk(
                    id = entity.id,
                    text = entity.text,
                    embedding = embeddingArray,
                    timestamp = entity.timestamp,
                    isPinned = entity.isPinned,
                )
            } else {
                null
            }
        }

        recentMemories.map { memory ->
            val similarity = cosineSimilarity(queryEmbedding, memory.embedding)
            memory to similarity
        }
            .sortedByDescending { it.second }
            .take(limit)
    }

    override suspend fun compactMemory(keepLimit: Int) = withContext(Dispatchers.IO) {
        memoryDao.deleteOldestMemories(keepLimit)
    }

    override suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) {
        memoryDao.deleteMemoryById(id)
    }

    override suspend fun updateMemory(id: Long, text: String, embedding: FloatArray) = withContext(Dispatchers.IO) {
        val embeddingString = converters.fromFloatArray(embedding)
            ?: throw IllegalArgumentException("Failed to serialize embedding")
        memoryDao.updateMemory(id = id, text = text, embedding = embeddingString)
    }

    override suspend fun setMemoryPinned(id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        memoryDao.setMemoryPinned(id = id, isPinned = pinned)
    }

    /**
     * Calculates the cosine similarity between two vectors.
     *
     * @param vectorA The first vector.
     * @param vectorB The second vector.
     * @return The cosine similarity score, ranging from -1.0 (opposite) to 1.0 (identical).
     *         Returns 0.0 if either vector has a magnitude of 0 or if their sizes don't match.
     */
    internal fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in vectorA.indices) {
            val a = vectorA[i]
            val b = vectorB[i]
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        if (normA == 0f || normB == 0f) return 0f

        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}
