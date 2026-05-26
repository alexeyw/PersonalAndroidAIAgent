package ai.agent.android.data.local.dao

import ai.agent.android.data.local.models.MemoryChunkEntity
import ai.agent.android.domain.models.MemorySummary
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing long-term memory chunks in the local database.
 */
@Dao
interface MemoryDao {

    /**
     * Inserts a new memory chunk into the database.
     *
     * @param memoryChunk The [MemoryChunkEntity] to insert.
     * @return The row ID of the newly inserted chunk.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memoryChunk: MemoryChunkEntity): Long

    /**
     * Retrieves all memory chunks from the database.
     * This is used to load embeddings into memory for vector similarity search.
     *
     * @return A list of all [MemoryChunkEntity] items.
     */
    @Query("SELECT * FROM memory_chunks")
    suspend fun getAllMemories(): List<MemoryChunkEntity>

    /**
     * Deletes a memory chunk by its unique ID.
     *
     * @param id The ID of the memory chunk to delete.
     */
    @Query("DELETE FROM memory_chunks WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    /**
     * Replaces the text content and embedding of an existing memory chunk.
     *
     * Used by the in-app memory editor: when the user commits an edit the
     * embedding must be regenerated for the new text, so the DAO writes both
     * columns atomically. The `timestamp` is intentionally left untouched —
     * an edit is not a fresh entry and should keep its original position in
     * the time-ordered history.
     *
     * @param id Identifier of the chunk to update.
     * @param text New raw text content.
     * @param embedding Serialized embedding vector (comma-encoded floats).
     */
    @Query("UPDATE memory_chunks SET text = :text, embedding = :embedding WHERE id = :id")
    suspend fun updateMemory(id: Long, text: String, embedding: String)

    /**
     * Sets the `isPinned` flag on a single memory chunk.
     *
     * @param id Identifier of the chunk to update.
     * @param isPinned `true` to pin the chunk, `false` to unpin it.
     */
    @Query("UPDATE memory_chunks SET isPinned = :isPinned WHERE id = :id")
    suspend fun setMemoryPinned(id: Long, isPinned: Boolean)

    /**
     * Retrieves a limited number of the most recent memory chunks from the database.
     * This is used to load a bounded number of embeddings into memory for vector similarity search.
     *
     * @param limit The maximum number of recent chunks to return.
     * @return A list of the most recent [MemoryChunkEntity] items.
     */
    @Query("SELECT * FROM memory_chunks ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<MemoryChunkEntity>

    /**
     * Retrieves the [limit] most recent memory chunks projected to text/timestamp
     * fields only — without the (potentially large) embedding payload.
     *
     * Intended for read paths that just display or quote the memory text (e.g.
     * the `$MEMORY_SUMMARY` prompt variable), so they avoid the cost of pulling
     * and deserialising every stored embedding string on every render.
     *
     * @param limit Maximum number of recent rows to return.
     * @return Recent rows ordered newest-first as [MemorySummary] projections.
     */
    @Query("SELECT id, text, timestamp FROM memory_chunks ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemorySummaries(limit: Int): List<MemorySummary>

    /**
     * Deletes older unpinned memory chunks, keeping only the specified number
     * of the most recent ones.
     *
     * Pinned chunks (`isPinned = 1`) are exempt — they survive compaction
     * regardless of `keepLimit`, matching the contract advertised on
     * [ai.agent.android.data.local.models.MemoryChunkEntity.isPinned]. The
     * `keepLimit` window is therefore evaluated over the *unpinned* subset
     * only.
     *
     * @param keepLimit The number of recent unpinned memory chunks to keep.
     */
    @Query(
        "DELETE FROM memory_chunks WHERE isPinned = 0 AND id NOT IN " +
            "(SELECT id FROM memory_chunks WHERE isPinned = 0 " +
            "ORDER BY timestamp DESC LIMIT :keepLimit)",
    )
    suspend fun deleteOldestMemories(keepLimit: Int)

    /**
     * Removes every memory chunk from the table — including pinned entries.
     * Backs the Settings → Memory → Clear destructive action. Pinned-or-not
     * is intentionally ignored because the user has explicitly confirmed
     * the wipe via the typed-confirm dialog.
     */
    @Query("DELETE FROM memory_chunks")
    suspend fun deleteAllMemories()

    /**
     * Live count of stored memory chunks. Powers the Settings → Memory
     * "CHUNKS" stat cell.
     */
    @Query("SELECT COUNT(*) FROM memory_chunks")
    fun observeChunkCount(): Flow<Int>

    /**
     * Live aggregate byte size of the table content. Uses `LENGTH(text)` +
     * `LENGTH(embedding)` so the estimate matches what the SQLite VFS
     * actually stores rather than a raw row count. Powers the
     * Settings → Memory "SIZE" stat cell.
     */
    @Query("SELECT COALESCE(SUM(LENGTH(text) + LENGTH(embedding)), 0) FROM memory_chunks")
    fun observeTotalBytes(): Flow<Long>
}
