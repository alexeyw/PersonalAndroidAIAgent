package ai.agent.android.data.local.dao

import ai.agent.android.data.local.models.MemoryChunkEntity
import ai.agent.android.domain.models.MemorySummary
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
     * Deletes older memory chunks, keeping only the specified number of the most recent ones.
     *
     * @param keepLimit The number of recent memory chunks to keep.
     */
    @Query(
        "DELETE FROM memory_chunks WHERE id NOT IN (SELECT id FROM memory_chunks ORDER BY timestamp DESC LIMIT :keepLimit)",
    )
    suspend fun deleteOldestMemories(keepLimit: Int)
}
