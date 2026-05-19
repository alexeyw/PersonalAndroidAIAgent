package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySummary

/**
 * Repository interface for managing and searching long-term memory.
 */
interface MemoryRepository {

    /**
     * Saves a new text snippet and its vector embedding to the database.
     *
     * @param text The raw text to save.
     * @param embedding The float array vector representing the text.
     * @return The ID of the saved memory chunk.
     */
    suspend fun saveMemory(text: String, embedding: FloatArray): Long

    /**
     * Retrieves all saved memories.
     *
     * @return A list of [MemoryChunk] objects.
     */
    suspend fun getAllMemories(): List<MemoryChunk>

    /**
     * Retrieves the most recent memories as a lightweight [MemorySummary]
     * projection that omits embeddings.
     *
     * Use this when only the textual content / ordering is needed (e.g. the
     * `$MEMORY_SUMMARY` prompt variable). Pulling a few hundred rows of
     * `getAllMemories()` just to take the top N would needlessly deserialise
     * every embedding from its column-encoded string form.
     *
     * @param limit Maximum number of recent memories to return; values `<= 0`
     * yield an empty list.
     * @return Recent memories ordered newest-first.
     */
    suspend fun getRecentMemorySummaries(limit: Int): List<MemorySummary>

    /**
     * Finds the most semantically similar memories to a given query embedding
     * using cosine similarity.
     *
     * @param queryEmbedding The vector embedding of the user's query.
     * @param searchPoolLimit The maximum number of recent memories to load into memory for searching.
     * @param limit The maximum number of results to return.
     * @return A list of pairs containing the [MemoryChunk] and its similarity score (0.0 to 1.0),
     *         sorted by similarity in descending order (highest first).
     */
    suspend fun findSimilarMemories(
        queryEmbedding: FloatArray,
        searchPoolLimit: Int,
        limit: Int = 5,
    ): List<Pair<MemoryChunk, Float>>

    /**
     * Deletes older memory chunks, keeping only the specified number of the most recent ones.
     *
     * @param keepLimit The number of recent memory chunks to keep.
     */
    suspend fun compactMemory(keepLimit: Int)

    /**
     * Deletes a memory chunk by its unique ID.
     *
     * @param id The ID of the memory chunk to delete.
     */
    suspend fun deleteMemory(id: Long)

    /**
     * Replaces the text content and embedding of an existing memory chunk.
     *
     * The caller is responsible for regenerating the embedding for the new
     * text — the repository will not derive it implicitly. The chunk's
     * `timestamp` is intentionally left untouched so the entry keeps its
     * original chronological position.
     *
     * @param id Identifier of the chunk to update.
     * @param text The new raw text content.
     * @param embedding The new vector embedding produced from [text].
     */
    suspend fun updateMemory(id: Long, text: String, embedding: FloatArray)

    /**
     * Flips the pinned state of a single memory chunk. Pinned chunks sort
     * ahead of unpinned chunks on the memory surface and are exempt from
     * future compaction passes.
     *
     * @param id Identifier of the chunk to update.
     * @param pinned `true` to pin the chunk, `false` to unpin it.
     */
    suspend fun setMemoryPinned(id: Long, pinned: Boolean)
}
