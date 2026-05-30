package ai.agent.android.domain.memoryio

import ai.agent.android.domain.models.MemorySource
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for the `MemorySource` ↔ JSON wire shape.
 *
 * Both the Room `memory_chunks.source` column converter
 * ([ai.agent.android.data.local.Converters]) and the memory export/import
 * gateway ([MemoryJsonSerializer]) round-trip provenance through this object,
 * so the on-disk encoding stays byte-identical no matter which path writes it.
 *
 * The discriminator lives under [KEY_TYPE]; variant-specific payload is added
 * alongside it ([KEY_SESSION_ID] for [MemorySource.ChatSession], [KEY_IDS] for
 * [MemorySource.Compaction]).
 *
 * The wire keys here are persisted forever — renaming one would silently
 * downgrade every existing chunk to [MemorySource.Unknown] on the next read.
 */
object MemorySourceJson {

    /** Discriminator key carrying [MemorySource.type]. */
    const val KEY_TYPE: String = "type"

    /** Payload key for [MemorySource.ChatSession.sessionId]. */
    const val KEY_SESSION_ID: String = "sessionId"

    /** Payload key for [MemorySource.Compaction.originalChunkIds]. */
    const val KEY_IDS: String = "ids"

    /**
     * Encodes [source] into a fresh [JSONObject] carrying its [MemorySource.type]
     * key and any variant payload.
     *
     * @param source The provenance to serialise.
     * @return A JSON object ready to be nested in a larger document or
     *   stringified for column storage.
     */
    fun encode(source: MemorySource): JSONObject = JSONObject().apply {
        put(KEY_TYPE, source.type)
        when (source) {
            is MemorySource.ChatSession -> put(KEY_SESSION_ID, source.sessionId)
            is MemorySource.Compaction -> put(KEY_IDS, JSONArray(source.originalChunkIds))
            MemorySource.Manual, MemorySource.Unknown -> Unit
        }
    }

    /**
     * Decodes a [JSONObject] produced by [encode] back into a [MemorySource].
     *
     * Total by design: a `null` object, a missing / unrecognised [KEY_TYPE],
     * or a malformed payload all resolve to [MemorySource.Unknown] rather than
     * throwing, so a single corrupt row (or a future wire key this build does
     * not recognise) never aborts a whole memory load.
     *
     * @param json The JSON object to parse, or `null`.
     * @return The parsed provenance, or [MemorySource.Unknown] on any
     *   unrecognised / missing input.
     */
    fun decode(json: JSONObject?): MemorySource {
        if (json == null) return MemorySource.Unknown
        return when (json.optString(KEY_TYPE)) {
            MemorySource.TYPE_CHAT_SESSION -> MemorySource.ChatSession(json.optString(KEY_SESSION_ID))
            MemorySource.TYPE_MANUAL -> MemorySource.Manual
            MemorySource.TYPE_COMPACTION -> {
                val idsJson = json.optJSONArray(KEY_IDS)
                val ids = buildList {
                    if (idsJson != null) {
                        // optLong (not getLong) keeps decode total: a non-numeric
                        // entry resolves to 0 instead of throwing, so a malformed
                        // `ids` array never breaks the caller's "never throws"
                        // contract (e.g. MemoryJsonSerializer.parse).
                        for (i in 0 until idsJson.length()) add(idsJson.optLong(i))
                    }
                }
                MemorySource.Compaction(ids)
            }
            else -> MemorySource.Unknown
        }
    }
}
