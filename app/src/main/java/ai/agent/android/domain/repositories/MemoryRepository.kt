package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySource
import ai.agent.android.domain.models.MemoryStats
import ai.agent.android.domain.models.MemorySummary
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing and searching long-term memory.
 */
interface MemoryRepository {

    /**
     * Saves a new text snippet and its vector embedding to the database.
     *
     * @param text The raw text to save.
     * @param embedding The float array vector representing the text.
     * @param source Provenance of the chunk (which conversation / action it
     *   came from). Defaults to [MemorySource.Manual] so existing direct-save
     *   call sites — which represent a deliberate, user-attributable write —
     *   keep a sensible attribution without each having to spell it out.
     * @param tags Optional labels to attach to the chunk (e.g. the
     *   auto-extraction fact type). Defaults to empty.
     * @return The ID of the saved memory chunk.
     */
    suspend fun saveMemory(
        text: String,
        embedding: FloatArray,
        source: MemorySource = MemorySource.Manual,
        tags: List<String> = emptyList(),
    ): Long

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
     * Retrieves the non-pinned chunks older than [olderThanMillis] — the
     * candidate set for the background compaction worker. Pinned chunks are
     * never returned (they are exempt from consolidation), and chunks younger
     * than the cutoff are excluded so recent facts keep their exact wording.
     *
     * @param olderThanMillis Exclusive upper bound on a chunk's timestamp.
     * @return Candidate chunks ordered newest-first.
     */
    suspend fun getCompactionCandidates(olderThanMillis: Long): List<MemoryChunk>

    /**
     * One-shot total count of stored chunks (pinned and unpinned). Backs the
     * compaction scheduler's hard-limit check.
     *
     * @return The number of stored memory chunks.
     */
    suspend fun countMemories(): Int

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
     * Atomically replaces the text, embedding, and tags of a chunk in a single
     * transaction, so an inline edit can never half-apply (e.g. text committed
     * but tags not). The chunk's `timestamp` is left untouched.
     *
     * @param id Identifier of the chunk to update.
     * @param text The new raw text content.
     * @param embedding The new vector embedding produced from [text].
     * @param tags The new tag list (empty clears all tags).
     */
    suspend fun updateMemoryWithTags(id: Long, text: String, embedding: FloatArray, tags: List<String>)

    /**
     * Flips the pinned state of a single memory chunk. Pinned chunks sort
     * ahead of unpinned chunks on the memory surface and are exempt from
     * future compaction passes.
     *
     * @param id Identifier of the chunk to update.
     * @param pinned `true` to pin the chunk, `false` to unpin it.
     */
    suspend fun setMemoryPinned(id: Long, pinned: Boolean)

    /**
     * Replaces the tag list of a single chunk. Does not touch the text or
     * embedding (tag edits never require re-embedding).
     *
     * @param id Identifier of the chunk to update.
     * @param tags New tag list (empty clears all tags).
     */
    suspend fun setMemoryTags(id: Long, tags: List<String>)

    /**
     * Records that the given chunks were just retrieved into a pipeline run's
     * Long-Term Memory context: increments each chunk's use count and stamps
     * the most-recent-use time. Best-effort; a no-op for an empty [ids].
     *
     * @param ids Identifiers of the chunks that were injected.
     * @param atMillis Epoch-millis to stamp as the most-recent use time.
     */
    suspend fun recordUsage(ids: List<Long>, atMillis: Long)

    /**
     * Removes every memory chunk — including pinned entries — from the
     * underlying table. Backs the Settings → Memory → Clear destructive
     * action (typed-confirm dialog).
     */
    suspend fun deleteAllMemories()

    /**
     * Live snapshot of the aggregate stats rendered in the Settings →
     * Memory card. Emits a fresh value whenever the underlying table
     * mutates; consumers should `collectAsState` or `stateIn` it.
     *
     * Thread count and average similarity score are best-effort: the v0.1
     * implementation returns `0` for threads (thread-attribution lands in
     * a follow-up) and `null` for averageSimilarityScore until a
     * similarity-search call has been recorded.
     */
    fun observeStats(): Flow<MemoryStats>
}
