package app.knotwork.android.domain.models

/**
 * Lightweight projection of a long-term memory chunk that omits the vector
 * embedding.
 *
 * Used by surfaces that only need to display or render the textual content of
 * recent memories (for example the `$MEMORY_SUMMARY` prompt variable). Loading
 * embeddings just to throw them away is wasteful — embeddings are large
 * comma-encoded float arrays — so this projection lets callers fetch only the
 * columns they actually consume.
 *
 * @property id The unique identifier of the memory chunk; matches
 * [MemoryChunk.id] for the same row.
 * @property text The raw text of the memory.
 * @property timestamp The time the memory was created, in milliseconds since
 * the Unix epoch.
 */
data class MemorySummary(val id: Long, val text: String, val timestamp: Long)
