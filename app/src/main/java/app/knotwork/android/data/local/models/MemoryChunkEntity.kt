package app.knotwork.android.data.local.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.knotwork.android.domain.models.MemorySource

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
 *   via [app.knotwork.android.data.local.Converters.fromMemorySource]. Legacy rows
 *   created before the version 25 → 26 migration backfill to
 *   [MemorySource.Unknown].
 * @property tagsCsv Comma-separated tag list (lower-case, kebab-case). Empty
 *   string when the chunk has no tags. Added by the version 26 → 27 migration
 *   (default `''`).
 * @property useCount Number of times this chunk has been injected into a
 *   pipeline run's Long-Term Memory context block. Added by the 26 → 27
 *   migration (default `0`).
 * @property lastUsedAt Epoch-millis of the most recent retrieval, or `null`
 *   if never used. Added by the 26 → 27 migration (nullable, default `NULL`).
 * @property needsReembedding When `true`, the chunk's stored [embedding] was
 *   produced by a different embedding provider (it was imported from a device
 *   whose active provider differed) and lives in an incompatible vector space.
 *   It is therefore re-computed lazily with the active provider on the next
 *   retrieval. Added by the 27 → 28 migration (default `0`).
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
    @ColumnInfo(name = "tagsCsv", defaultValue = "")
    val tagsCsv: String = "",
    @ColumnInfo(name = "useCount", defaultValue = "0")
    val useCount: Int = 0,
    @ColumnInfo(name = "lastUsedAt")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "needsReembedding", defaultValue = "0")
    val needsReembedding: Boolean = false,
) {
    companion object {
        /**
         * SQL-level default for the `source` column. Mirrors the JSON the
         * version 25 → 26 migration writes for legacy rows and the encoding
         * produced by [app.knotwork.android.data.local.Converters.fromMemorySource]
         * for [MemorySource.Unknown]. Kept in lock-step so the Room-generated
         * schema and the hand-written migration agree.
         */
        const val SOURCE_DEFAULT_JSON: String = "{\"type\":\"unknown\"}"
    }
}
