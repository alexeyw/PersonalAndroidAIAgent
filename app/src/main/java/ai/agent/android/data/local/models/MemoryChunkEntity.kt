package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a chunk of long-term memory (a text snippet and its vector embedding).
 *
 * @property id The unique auto-generated identifier for this memory chunk.
 * @property text The raw text content of the memory.
 * @property embedding The float array representing the text embedding, stored as a serialized string (e.g., comma-separated values).
 * @property timestamp The time the memory was recorded, in milliseconds since epoch.
 */
@Entity(tableName = "memory_chunks")
data class MemoryChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val embedding: String,
    val timestamp: Long
)
