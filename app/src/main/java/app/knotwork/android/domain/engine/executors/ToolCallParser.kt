package app.knotwork.android.domain.engine.executors

import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

/**
 * Extracts a structured tool call from free-form LLM output.
 *
 * Both [ToolNodeExecutor] (tool-selection / argument-generation passes) and
 * [SkillNodeExecutor] (a skill that initiates a tool call from its inference
 * output) need to recover a `{ "tool": ..., "arguments": ... }` object that a
 * model may have wrapped in a ```json fenced block, surrounded with prose, or
 * emitted bare. Centralising the parsing here keeps the two executors from
 * drifting in how leniently they read model output.
 */
object ToolCallParser {

    private val JSON_BLOCK_REGEX = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)

    /**
     * Parses a tool selection of the form `{"tool": "name", "arguments": {...}}`.
     *
     * Tolerates a fenced ```json block, a JSON object embedded in surrounding
     * prose (first `{` … last `}`), or a bare object. Returns `null` when no
     * object with both a `tool` and an `arguments` field can be recovered.
     *
     * @param response Raw model output.
     * @return The `(toolName, argumentsJson)` pair, or `null` when absent / malformed.
     */
    fun parseToolSelection(response: String): Pair<String, String>? {
        val jsonBlock = extractJsonBlock(response)
        return try {
            val jsonObject = JSONObject(jsonBlock)
            val tool = if (jsonObject.has("tool")) jsonObject.getString("tool") else null
            // `toString()` is polymorphic: a JSONObject / JSONArray serialises to its
            // compact JSON form, a primitive to its raw value — so a single call covers
            // every `arguments` shape the model might emit.
            val args = if (jsonObject.has("arguments")) jsonObject.get("arguments").toString() else null

            if (tool != null && args != null) Pair(tool, args) else null
        } catch (e: JSONException) {
            Timber.tag("PipelineDebug").e(e, "Error parsing tool selection JSON")
            null
        }
    }

    /**
     * Parses just the `arguments` payload of a tool call, used when the tool
     * name is already fixed by the node and only the argument object needs
     * recovering.
     *
     * @param response Raw model output.
     * @return The `arguments` value as a JSON string, or `null` when absent / malformed.
     */
    fun parseToolArguments(response: String): String? {
        val jsonBlock = extractJsonBlock(response)
        return try {
            val jsonObject = JSONObject(jsonBlock)
            if (!jsonObject.has("arguments")) return null
            jsonObject.get("arguments").toString()
        } catch (e: JSONException) {
            Timber.tag("PipelineDebug").e(e, "Error parsing tool arguments JSON")
            null
        }
    }

    /**
     * Isolates the most likely JSON object substring from [response]: a fenced
     * ```json block when present, otherwise the span between the first `{` and
     * the last `}`, otherwise the trimmed input as-is.
     */
    private fun extractJsonBlock(response: String): String {
        JSON_BLOCK_REGEX.find(response)?.groups?.get(1)?.value?.let { return it }
        val startIndex = response.indexOf('{')
        val endIndex = response.lastIndexOf('}')
        return if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            response.substring(startIndex, endIndex + 1)
        } else {
            response
        }
    }
}
