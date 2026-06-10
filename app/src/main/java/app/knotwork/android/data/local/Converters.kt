package app.knotwork.android.data.local

import androidx.room.TypeConverter
import app.knotwork.android.domain.memoryio.MemorySourceJson
import app.knotwork.android.domain.models.MemorySource
import app.knotwork.android.domain.models.NodeContextConfig
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

/**
 * Provides TypeConverters for Room to handle custom data types.
 *
 * Currently handles:
 * - [FloatArray] ↔ little-endian [ByteArray] for vector embeddings (BLOB column);
 * - [NodeContextConfig] ↔ JSON [String] for per-node pipeline context flags;
 * - [MemorySource] ↔ JSON [String] for memory-chunk provenance.
 */
class Converters {

    /**
     * Encodes a [FloatArray] embedding into its binary BLOB form (little-endian
     * IEEE-754, 4 bytes per component — see [EmbeddingBlobCodec]).
     *
     * @param array The float array to encode.
     * @return The encoded bytes, or null if the array is null.
     */
    @TypeConverter
    fun fromFloatArray(array: FloatArray?): ByteArray? = array?.let(EmbeddingBlobCodec::encode)

    /**
     * Decodes an embedding BLOB produced by [fromFloatArray] back into a
     * [FloatArray]. Total — never throws.
     *
     * @param value The stored bytes.
     * @return The decoded [FloatArray], or null if the input is null, empty
     *   (the migration's "no usable embedding" marker), or not a valid float
     *   sequence.
     */
    @TypeConverter
    fun toFloatArray(value: ByteArray?): FloatArray? = value?.let(EmbeddingBlobCodec::decode)

    /**
     * Serialises a [NodeContextConfig] to a compact JSON string suitable for
     * storage in a single Room TEXT column. The matching `pipeline_nodes`
     * column is `NOT NULL`, so both directions of this converter are non-null.
     *
     * @param config The config to serialise.
     * @return A JSON object string with all five boolean flags.
     */
    @TypeConverter
    fun fromNodeContextConfig(config: NodeContextConfig): String = JSONObject().apply {
        put(KEY_CHAT_HISTORY, config.chatHistory)
        put(KEY_ORIGINAL_TASK, config.originalTask)
        put(KEY_NODE_INPUT, config.nodeInput)
        put(KEY_LONG_TERM_MEMORY, config.longTermMemory)
        put(KEY_TOOL_RESULTS, config.toolResults)
    }.toString()

    /**
     * Deserialises a JSON string produced by [fromNodeContextConfig] back into
     * a [NodeContextConfig].
     *
     * Although the schema makes `context_config` `NOT NULL`, this converter
     * still defends against blank or malformed payloads — both can be produced
     * by a hypothetical hand-edited DB. In every recoverable case the
     * converter falls back to [NodeContextConfig.ALL_ENABLED], which is the
     * same value the migration writes for legacy rows; this preserves the
     * "everything enabled by default" backward-compatibility contract.
     *
     * @param value The stored JSON string.
     * @return The parsed configuration, or [NodeContextConfig.ALL_ENABLED] on
     * any error / missing data.
     */
    @TypeConverter
    fun toNodeContextConfig(value: String): NodeContextConfig {
        if (value.isBlank()) return NodeContextConfig.ALL_ENABLED
        return try {
            val json = JSONObject(value)
            NodeContextConfig(
                chatHistory = json.optBoolean(KEY_CHAT_HISTORY, true),
                originalTask = json.optBoolean(KEY_ORIGINAL_TASK, true),
                nodeInput = json.optBoolean(KEY_NODE_INPUT, true),
                longTermMemory = json.optBoolean(KEY_LONG_TERM_MEMORY, true),
                toolResults = json.optBoolean(KEY_TOOL_RESULTS, true),
            )
        } catch (e: JSONException) {
            Timber.w(e, "Failed to parse NodeContextConfig from value=%s, using ALL_ENABLED", value)
            NodeContextConfig.ALL_ENABLED
        }
    }

    /**
     * Serialises a [MemorySource] to a compact JSON string for the single
     * `memory_chunks.source` TEXT column. Delegates the wire shape to
     * [MemorySourceJson] so the column encoding stays byte-identical to the
     * memory export file. The column is `NOT NULL`, so both directions are
     * non-null.
     *
     * @param source The provenance to serialise.
     * @return A JSON object string carrying the [MemorySource.type] key and any
     * variant payload.
     */
    @TypeConverter
    fun fromMemorySource(source: MemorySource): String = MemorySourceJson.encode(source).toString()

    /**
     * Deserialises a JSON string produced by [fromMemorySource] back into a
     * [MemorySource].
     *
     * Defends against blank or malformed payloads (e.g. a hand-edited DB or a
     * future wire key this build does not recognise): in every unrecoverable
     * case it returns [MemorySource.Unknown], matching the JSON the migration
     * writes for legacy rows. This keeps reads total — a single corrupt row
     * never aborts a whole memory load.
     *
     * @param value The stored JSON string.
     * @return The parsed provenance, or [MemorySource.Unknown] on any error.
     */
    @TypeConverter
    fun toMemorySource(value: String): MemorySource {
        if (value.isBlank()) return MemorySource.Unknown
        return try {
            MemorySourceJson.decode(JSONObject(value))
        } catch (e: JSONException) {
            Timber.w(e, "Failed to parse MemorySource from value=%s, using Unknown", value)
            MemorySource.Unknown
        }
    }

    private companion object {
        const val KEY_CHAT_HISTORY = "chatHistory"
        const val KEY_ORIGINAL_TASK = "originalTask"
        const val KEY_NODE_INPUT = "nodeInput"
        const val KEY_LONG_TERM_MEMORY = "longTermMemory"
        const val KEY_TOOL_RESULTS = "toolResults"
    }
}
