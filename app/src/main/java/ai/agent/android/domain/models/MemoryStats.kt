package ai.agent.android.domain.models

/**
 * Aggregate counters that power the Settings → Memory card's stats grid.
 *
 * All fields are best-effort snapshots — the underlying query is a single
 * pass over the `memory_chunks` table, so callers that need live updates
 * should observe the repository [Flow] rather than calling once.
 *
 * @property chunkCount Total number of stored memory chunks (pinned and
 *   unpinned).
 * @property totalBytes Sum of `LENGTH(text) + LENGTH(embedding)` across
 *   every chunk. Approximates the on-device storage cost of the memory
 *   table without pulling the actual blobs into memory.
 * @property threadCount Number of distinct chat sessions that have at
 *   least one associated memory chunk. v0.1 uses the chunk's originating
 *   session id when available; until thread-attribution lands this is
 *   exposed as `0` and the UI renders a dash.
 * @property averageSimilarityScore Mean cosine-similarity score across the
 *   last similarity-search call, in `0f..1f`. `null` when no search has
 *   been performed in the current session — the UI then renders a dash.
 */
data class MemoryStats(
    val chunkCount: Int,
    val totalBytes: Long,
    val threadCount: Int,
    val averageSimilarityScore: Float?,
) {
    /** Holds the [EMPTY] sentinel used while the first observation is in flight. */
    companion object {
        /** Empty stats projection — used while the first observation is in flight. */
        val EMPTY: MemoryStats = MemoryStats(
            chunkCount = 0,
            totalBytes = 0L,
            threadCount = 0,
            averageSimilarityScore = null,
        )
    }
}
