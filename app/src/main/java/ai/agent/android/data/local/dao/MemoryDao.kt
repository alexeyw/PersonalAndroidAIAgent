package ai.agent.android.data.local.dao

import ai.agent.android.data.local.models.MemoryChunkEntity
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
}
