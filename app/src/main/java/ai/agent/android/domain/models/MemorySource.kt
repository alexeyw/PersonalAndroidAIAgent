package ai.agent.android.domain.models

/**
 * Provenance of a single long-term [MemoryChunk] — where the fact originally
 * came from.
 *
 * Source attribution lets the rest of the memory subsystem reason about a
 * chunk without re-deriving its origin:
 *  - the auto-extraction pipeline tags chunks with [ChatSession] so a later
 *    compaction or export can group facts by the conversation that produced
 *    them;
 *  - the in-app "Save to memory" action tags chunks as [Manual];
 *  - the background consolidation worker (Phase 25 / Task 5) emits
 *    [Compaction] carrying the ids of the chunks it merged;
 *  - rows written before this concept existed (or any payload that fails to
 *    parse) resolve to [Unknown].
 *
 * Each variant carries a stable wire [type] key. These keys are persisted (via
 * the Room `source` column converter and the memory export JSON) and therefore
 * **must never change once shipped** — a renamed key would silently downgrade
 * every existing chunk to [Unknown].
 */
sealed interface MemorySource {

    /**
     * Stable, machine-readable discriminator persisted alongside the chunk.
     * Used by the Room converter and the export/import schema to round-trip
     * the concrete variant.
     */
    val type: String

    /**
     * The chunk was automatically extracted from a chat conversation.
     *
     * @property sessionId Identifier of the [ChatSession] the fact was distilled
     *   from. Lets downstream features (compaction, export, observability) link
     *   a memory back to its originating dialogue.
     */
    data class ChatSession(val sessionId: String) : MemorySource {
        override val type: String get() = TYPE_CHAT_SESSION
    }

    /**
     * The chunk was saved explicitly by the user (e.g. the "Save to memory"
     * long-press action on a chat message — Phase 25 / Task 7).
     */
    data object Manual : MemorySource {
        override val type: String get() = TYPE_MANUAL
    }

    /**
     * The chunk is the consolidated summary of several older chunks produced by
     * the background compaction worker (Phase 25 / Task 5).
     *
     * @property originalChunkIds Ids of the chunks that were merged into this
     *   one. Retained so observability can explain a compaction and so a future
     *   "undo compaction" could, in principle, be reconstructed.
     */
    data class Compaction(val originalChunkIds: List<Long>) : MemorySource {
        override val type: String get() = TYPE_COMPACTION
    }

    /**
     * The chunk's provenance is not known — the canonical value for rows
     * created before source attribution existed (backfilled by the
     * version 25 → 26 migration) and the safe fallback for any payload that
     * fails to parse.
     */
    data object Unknown : MemorySource {
        override val type: String get() = TYPE_UNKNOWN
    }

    /**
     * Canonical wire keys for every [MemorySource] variant.
     *
     * Declared as compile-time constants so the Room converter, the export
     * schema, and any test fixtures all reference the same single source of
     * truth instead of re-typing the literals.
     */
    companion object {
        /** Wire key for [ChatSession]. */
        const val TYPE_CHAT_SESSION: String = "chat_session"

        /** Wire key for [Manual]. */
        const val TYPE_MANUAL: String = "manual"

        /** Wire key for [Compaction]. */
        const val TYPE_COMPACTION: String = "compaction"

        /** Wire key for [Unknown]. */
        const val TYPE_UNKNOWN: String = "unknown"
    }
}
