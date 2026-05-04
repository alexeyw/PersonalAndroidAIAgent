package ai.agent.android.data.local

import androidx.room.TypeConverter
import ai.agent.android.domain.models.NodeContextConfig
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

/**
 * Provides TypeConverters for Room to handle custom data types.
 *
 * Currently handles:
 * - [FloatArray] ↔ comma-separated [String] for vector embeddings;
 * - [NodeContextConfig] ↔ JSON [String] for per-node pipeline context flags.
 */
class Converters {

    /**
     * Converts a [FloatArray] to a comma-separated [String].
     *
     * @param array The float array to convert.
     * @return A comma-separated string representation of the array, or null if the array is null.
     */
    @TypeConverter
    fun fromFloatArray(array: FloatArray?): String? {
        return array?.joinToString(separator = ",")
    }

    /**
     * Converts a comma-separated [String] back into a [FloatArray].
     *
     * @param value The string to convert.
     * @return The resulting [FloatArray], or null if the input is null or empty.
     */
    @TypeConverter
    fun toFloatArray(value: String?): FloatArray? {
        if (value.isNullOrBlank()) return null
        return value.split(",").map { it.toFloat() }.toFloatArray()
    }

    /**
     * Serialises a [NodeContextConfig] to a compact JSON string suitable for
     * storage in a single Room TEXT column. The matching `pipeline_nodes`
     * column is `NOT NULL`, so both directions of this converter are non-null.
     *
     * @param config The config to serialise.
     * @return A JSON object string with all five boolean flags.
     */
    @TypeConverter
    fun fromNodeContextConfig(config: NodeContextConfig): String {
        return JSONObject().apply {
            put(KEY_CHAT_HISTORY, config.chatHistory)
            put(KEY_ORIGINAL_TASK, config.originalTask)
            put(KEY_NODE_INPUT, config.nodeInput)
            put(KEY_LONG_TERM_MEMORY, config.longTermMemory)
            put(KEY_TOOL_RESULTS, config.toolResults)
        }.toString()
    }

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

    private companion object {
        const val KEY_CHAT_HISTORY = "chatHistory"
        const val KEY_ORIGINAL_TASK = "originalTask"
        const val KEY_NODE_INPUT = "nodeInput"
        const val KEY_LONG_TERM_MEMORY = "longTermMemory"
        const val KEY_TOOL_RESULTS = "toolResults"
    }
}
