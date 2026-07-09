package app.knotwork.android.domain.pipelineio

import app.knotwork.android.domain.models.PipelineSamplePrompt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Single source of truth for the JSON wire shape of a pipeline's
 * [PipelineSamplePrompt] list. Both the Room `Converters` (the
 * `pipelines.samplePrompts` TEXT column) and [PipelineJsonSerializer]
 * (export / import / preset / bundle) delegate here, so the encode/decode rules
 * cannot drift between the two representations.
 *
 * Wire shape: a JSON array of `{ "title": String, "toolsHint"?: String }`
 * objects. `toolsHint` is omitted when `null`; entries without a non-blank
 * `title` are skipped on decode; a blank `toolsHint` decodes back to `null`.
 * Decoding is total — malformed or blank input yields an empty list rather than
 * throwing, so a corrupt column or document never aborts a pipeline load.
 */
object PipelineSamplePromptJson {

    private const val KEY_TITLE = "title"
    private const val KEY_TOOLS_HINT = "toolsHint"

    /**
     * Encodes [prompts] into a [JSONArray] of `{title, toolsHint?}` objects.
     *
     * @param prompts The sample prompts to encode.
     * @return A [JSONArray]; empty when [prompts] is empty.
     */
    fun encodeToArray(prompts: List<PipelineSamplePrompt>): JSONArray {
        val array = JSONArray()
        prompts.forEach { prompt ->
            val obj = JSONObject().put(KEY_TITLE, prompt.title)
            prompt.toolsHint?.let { obj.put(KEY_TOOLS_HINT, it) }
            array.put(obj)
        }
        return array
    }

    /**
     * Decodes a [JSONArray] produced by [encodeToArray] back into a list of
     * [PipelineSamplePrompt]. Entries missing a non-blank title are skipped; a
     * missing or blank `toolsHint` decodes to `null`.
     *
     * @param array The array to decode, or `null` (absent key) → empty list.
     * @return The decoded prompts.
     */
    fun decodeFromArray(array: JSONArray?): List<PipelineSamplePrompt> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val title = obj.optString(KEY_TITLE).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PipelineSamplePrompt(title = title, toolsHint = obj.optString(KEY_TOOLS_HINT).takeIf { it.isNotBlank() })
        }
    }

    /**
     * Encodes [prompts] into a compact JSON array string for single-column
     * storage.
     *
     * @param prompts The sample prompts to encode.
     * @return A JSON array string (`[]` when empty).
     */
    fun encodeToString(prompts: List<PipelineSamplePrompt>): String = encodeToArray(prompts).toString()

    /**
     * Decodes a JSON array string produced by [encodeToString] back into a list
     * of [PipelineSamplePrompt]. Blank or malformed input yields an empty list.
     *
     * @param value The stored JSON array string.
     * @return The decoded prompts, or an empty list on any error / missing data.
     */
    fun decodeFromString(value: String): List<PipelineSamplePrompt> {
        if (value.isBlank()) return emptyList()
        return try {
            decodeFromArray(JSONArray(value))
        } catch (_: JSONException) {
            emptyList()
        }
    }
}
