package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.Converters
import app.knotwork.android.data.local.TagsCsv
import app.knotwork.android.data.local.dao.MemoryDao
import app.knotwork.android.data.local.models.MemoryChunkEntity
import app.knotwork.android.domain.models.MemoryChunk
import app.knotwork.android.domain.models.MemorySource
import app.knotwork.android.domain.models.MemoryStats
import app.knotwork.android.domain.models.MemorySummary
import app.knotwork.android.domain.repositories.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
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

    /**
     * Rolling average of the cosine similarity scores observed across the
     * most recent `findSimilarMemories` calls. Read by [observeStats] and
     * surfaced as the AVG SCORE stat cell in Settings → Memory.
     *
     * `null` until the user (or the agent on their behalf) performs at
     * least one similarity search. Bounded to the last 32 observations so
     * the value reflects recent behaviour, not the entire app lifetime.
     */
    private val recentSimilarityScores = MutableStateFlow<List<Float>>(emptyList())

    override suspend fun saveMemory(
        text: String,
        embedding: FloatArray,
        source: MemorySource,
        tags: List<String>,
    ): Long = withContext(Dispatchers.IO) {
        val embeddingString = converters.fromFloatArray(embedding)
            ?: throw IllegalArgumentException("Failed to serialize embedding")

        val entity = MemoryChunkEntity(
            text = text,
            embedding = embeddingString,
            timestamp = System.currentTimeMillis(),
            source = source,
            tagsCsv = TagsCsv.encode(tags),
        )
        memoryDao.insertMemory(entity)
    }

    override suspend fun getAllMemories(): List<MemoryChunk> = withContext(Dispatchers.IO) {
        memoryDao.getAllMemories().mapNotNull { entity -> entity.toMemoryChunkOrNull() }
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
            entity.toMemoryChunkOrNull()
        }

        val ranked = recentMemories.map { memory ->
            val similarity = cosineSimilarity(queryEmbedding, memory.embedding)
            memory to similarity
        }
            .sortedByDescending { it.second }
            .take(limit)

        // Record the strongest few scores so the Settings stats card surfaces a
        // representative average. Only the head of the ranked list is sampled
        // (not the whole returned set): retrieval now asks for the full scored
        // pool to feed re-ranking, and folding hundreds of low-similarity tail
        // scores into the window would drag the AVG SCORE cell toward zero.
        val newScores = ranked.take(STATS_SAMPLE_SIZE).map { it.second }
        if (newScores.isNotEmpty()) {
            recentSimilarityScores.update { previous ->
                (previous + newScores).takeLast(RECENT_SIMILARITY_WINDOW)
            }
        }

        ranked
    }

    override suspend fun compactMemory(keepLimit: Int) = withContext(Dispatchers.IO) {
        memoryDao.deleteOldestMemories(keepLimit)
    }

    override suspend fun getCompactionCandidates(olderThanMillis: Long): List<MemoryChunk> =
        withContext(Dispatchers.IO) {
            memoryDao.getCompactionCandidates(olderThanMillis).mapNotNull { entity ->
                entity.toMemoryChunkOrNull()
            }
        }

    override suspend fun countMemories(): Int = withContext(Dispatchers.IO) {
        memoryDao.countMemories()
    }

    override suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) {
        memoryDao.deleteMemoryById(id)
    }

    override suspend fun updateMemory(id: Long, text: String, embedding: FloatArray) = withContext(Dispatchers.IO) {
        val embeddingString = converters.fromFloatArray(embedding)
            ?: throw IllegalArgumentException("Failed to serialize embedding")
        memoryDao.updateMemory(id = id, text = text, embedding = embeddingString)
    }

    override suspend fun updateMemoryWithTags(id: Long, text: String, embedding: FloatArray, tags: List<String>) =
        withContext(Dispatchers.IO) {
            val embeddingString = converters.fromFloatArray(embedding)
                ?: throw IllegalArgumentException("Failed to serialize embedding")
            memoryDao.updateMemoryWithTags(
                id = id,
                text = text,
                embedding = embeddingString,
                tagsCsv = TagsCsv.encode(tags),
            )
        }

    override suspend fun setMemoryPinned(id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        memoryDao.setMemoryPinned(id = id, isPinned = pinned)
    }

    override suspend fun setMemoryTags(id: Long, tags: List<String>) = withContext(Dispatchers.IO) {
        memoryDao.setMemoryTags(id = id, tagsCsv = TagsCsv.encode(tags))
    }

    override suspend fun recordUsage(ids: List<Long>, atMillis: Long) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            memoryDao.recordUsage(ids = ids, atMillis = atMillis)
        }
    }

    override suspend fun deleteAllMemories() = withContext(Dispatchers.IO) {
        memoryDao.deleteAllMemories()
        recentSimilarityScores.value = emptyList()
    }

    override suspend fun getExistingMemoryIds(): Set<Long> = withContext(Dispatchers.IO) {
        memoryDao.getAllIds().toSet()
    }

    override suspend fun insertImportedMemories(chunks: List<MemoryChunk>, needsReembedding: Boolean) {
        if (chunks.isEmpty()) return
        withContext(Dispatchers.IO) {
            memoryDao.insertMemories(chunks.toImportEntities(needsReembedding))
        }
    }

    override suspend fun replaceImportedMemories(chunks: List<MemoryChunk>, needsReembedding: Boolean) =
        withContext(Dispatchers.IO) {
            memoryDao.replaceAll(chunks.toImportEntities(needsReembedding))
        }

    override suspend fun countMemoriesNeedingReembedding(): Int = withContext(Dispatchers.IO) {
        memoryDao.countNeedingReembedding()
    }

    override suspend fun getMemoriesNeedingReembedding(): List<MemoryChunk> = withContext(Dispatchers.IO) {
        // Deliberately lenient (no `mapNotNull` drop): the re-embed pass recomputes
        // the vector from `text` and ignores the stored embedding, so a row whose
        // embedding string is unparseable must still be returned — otherwise it
        // would be dropped here yet keep counting in `countNeedingReembedding`,
        // re-arming the worker on every startup as a permanent no-op. A bad
        // embedding falls back to an empty array (the caller never reads it).
        memoryDao.getMemoriesNeedingReembedding().map { entity ->
            MemoryChunk(
                id = entity.id,
                text = entity.text,
                // runCatching: toFloatArray throws on a non-numeric string and
                // returns null on a blank one; either way a corrupt embedding
                // falls back to empty so the row is still returned and repaired.
                embedding = runCatching { converters.toFloatArray(entity.embedding) }.getOrNull() ?: FloatArray(0),
                timestamp = entity.timestamp,
                isPinned = entity.isPinned,
                source = entity.source,
                tags = TagsCsv.decode(entity.tagsCsv),
                useCount = entity.useCount,
                lastUsedAt = entity.lastUsedAt,
            )
        }
    }

    /**
     * Maps imported domain chunks to entities, preserving id / timestamp /
     * provenance / pin state / tags and resetting per-device usage telemetry.
     * Chunks whose embedding cannot be serialised are dropped (their embedding
     * is already validated non-empty by the import parser, so this is a defensive
     * guard rather than an expected path).
     */
    private fun List<MemoryChunk>.toImportEntities(needsReembedding: Boolean): List<MemoryChunkEntity> =
        mapNotNull { chunk ->
            val embeddingString = converters.fromFloatArray(chunk.embedding) ?: return@mapNotNull null
            MemoryChunkEntity(
                id = chunk.id,
                text = chunk.text,
                embedding = embeddingString,
                timestamp = chunk.timestamp,
                isPinned = chunk.isPinned,
                source = chunk.source,
                tagsCsv = TagsCsv.encode(chunk.tags),
                needsReembedding = needsReembedding,
            )
        }

    override suspend fun markMemoryReembedded(id: Long, embedding: FloatArray) = withContext(Dispatchers.IO) {
        val embeddingString = converters.fromFloatArray(embedding)
            ?: throw IllegalArgumentException("Failed to serialize embedding")
        memoryDao.markReembedded(id = id, embedding = embeddingString)
    }

    override fun observeStats(): Flow<MemoryStats> = combine(
        memoryDao.observeChunkCount(),
        memoryDao.observeTotalBytes(),
        recentSimilarityScores,
    ) { chunkCount, totalBytes, scores ->
        MemoryStats(
            chunkCount = chunkCount,
            totalBytes = totalBytes,
            // Thread attribution lands in a follow-up — v0.1 reports zero
            // and the UI then renders a dash for the THREADS stat cell.
            threadCount = 0,
            averageSimilarityScore = scores.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
        )
    }

    /**
     * Maps a persisted [MemoryChunkEntity] to its domain [MemoryChunk],
     * deserialising the comma-encoded embedding. Returns `null` when the
     * embedding column cannot be parsed so a single corrupt row is skipped
     * rather than aborting the whole load.
     *
     * @return The domain chunk, or `null` if the embedding failed to deserialise.
     */
    private fun MemoryChunkEntity.toMemoryChunkOrNull(): MemoryChunk? {
        val embeddingArray = converters.toFloatArray(embedding) ?: return null
        return MemoryChunk(
            id = id,
            text = text,
            embedding = embeddingArray,
            timestamp = timestamp,
            isPinned = isPinned,
            source = source,
            tags = TagsCsv.decode(tagsCsv),
            useCount = useCount,
            lastUsedAt = lastUsedAt,
        )
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

    private companion object {
        /** Number of recent cosine-similarity scores tracked for the AVG SCORE stat cell. */
        const val RECENT_SIMILARITY_WINDOW: Int = 32

        /**
         * Per-call cap on how many of the ranked scores feed the AVG SCORE
         * window. Keeps the stat anchored to the strongest hits even when a
         * caller requests a large pool for re-ranking.
         */
        const val STATS_SAMPLE_SIZE: Int = 5
    }
}
