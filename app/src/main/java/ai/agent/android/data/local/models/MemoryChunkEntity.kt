package ai.agent.android.data.local.models

import ai.agent.android.domain.models.MemorySource
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a chunk of long-term memory (a text snippet and its vector embedding).
 *
 * @property id The unique auto-generated identifier for this memory chunk.
 * @property text The raw text content of the memory.
 * @property embedding The float array representing the text embedding, stored as a serialized string (e.g., comma-separated values).
 * @property timestamp The time the memory was recorded, in milliseconds since epoch.
 * @property isPinned When `true`, the user marked this chunk so it should sort
 *   ahead of unpinned rows in the memory surface and survive future
 *   `compactMemory()` passes that prune older entries.
 * @property source Provenance of the chunk, persisted as a compact JSON string
 *   via [ai.agent.android.data.local.Converters.fromMemorySource]. Legacy rows
 *   created before the version 25 → 26 migration backfill to
 *   [MemorySource.Unknown].
 */
@Entity(tableName = "memory_chunks")
data class MemoryChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val embedding: String,
    val timestamp: Long,
    val isPinned: Boolean = false,
    @ColumnInfo(name = "source", defaultValue = MemoryChunkEntity.SOURCE_DEFAULT_JSON)
    val source: MemorySource = MemorySource.Unknown,
) {
    companion object {
        /**
         * SQL-level default for the `source` column. Mirrors the JSON the
         * version 25 → 26 migration writes for legacy rows and the encoding
         * produced by [ai.agent.android.data.local.Converters.fromMemorySource]
         * for [MemorySource.Unknown]. Kept in lock-step so the Room-generated
         * schema and the hand-written migration agree.
         */
        const val SOURCE_DEFAULT_JSON: String = "{\"type\":\"unknown\"}"
    }
}
