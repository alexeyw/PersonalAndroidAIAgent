package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.engine.structured.JsonPayloadExtractor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/**
 * Renders a tool-call `arguments` payload as the string handed to the tool layer.
 *
 * A string primitive is unwrapped to its raw content (`"how to build…"` →
 * `how to build…`), matching the historical `org.json` behaviour the downstream
 * argument parser expects; every other shape (object, array, number, boolean) is
 * rendered as its compact JSON literal. Centralised so the gate path
 * ([app.knotwork.android.domain.engine.executors.ToolNodeExecutor]) and the
 * non-repair path ([ToolCallParser.parseToolSelection]) stay identical.
 *
 * @receiver The `arguments` JSON value.
 * @return The arguments string for tool dispatch.
 */
internal fun JsonElement.asArgumentString(): String = when (this) {
    is JsonPrimitive -> if (isString) content else toString()
    else -> toString()
}

/**
 * Extracts a structured tool call from free-form LLM output.
 *
 * [SkillNodeExecutor] (a skill that initiates a tool call from its already-generated
 * inference output) needs to recover a `{ "tool": ..., "arguments": ... }` object that
 * a model may have wrapped in a ```json fenced block, surrounded with prose, or emitted
 * bare. JSON isolation is delegated to the shared [JsonPayloadExtractor] (the same
 * extractor the [app.knotwork.android.domain.engine.structured.StructuredOutputGate]
 * uses), so this parser and the gate never drift in how leniently they read model output.
 *
 * [ToolNodeExecutor] no longer parses post-hoc here — it runs the inference through the
 * gate and validates [ToolCall] / a raw arguments object with repair. This parser is the
 * non-repair path used where re-inference is not possible (the skill output is already
 * produced).
 */
object ToolCallParser {

    /**
     * Lenient reader matching the gate's: tolerant of commentary fields and the
     * relaxed quoting local models emit, while still rejecting genuinely wrong shapes.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Wire shape of a tool call: the tool's name and its argument payload. Both are
     * required — a partial object fails deserialization, which the gate turns into a
     * repair attempt and this non-repair parser turns into a `null` result.
     *
     * @property tool Exact name of the selected tool.
     * @property arguments Argument payload of any JSON shape (object or primitive).
     */
    @Serializable
    data class ToolCall(val tool: String, val arguments: JsonElement)

    /**
     * Parses a tool selection of the form `{"tool": "name", "arguments": {...}}`.
     *
     * Tolerates a fenced ```json block, a JSON object embedded in surrounding
     * prose, or a bare object (via [JsonPayloadExtractor]). Returns `null` when no
     * object with both a `tool` and an `arguments` field can be recovered.
     *
     * @param response Raw model output.
     * @return The `(toolName, argumentsJson)` pair, or `null` when absent / malformed.
     */
    fun parseToolSelection(response: String): Pair<String, String>? {
        val payload = JsonPayloadExtractor.extract(response)
        return try {
            val call = json.decodeFromString(ToolCall.serializer(), payload)
            Pair(call.tool, call.arguments.asArgumentString())
        } catch (e: IllegalArgumentException) {
            // `decodeFromString` raises `SerializationException` (a subtype) for shape /
            // encoding errors and a plain `IllegalArgumentException` for unmappable values;
            // catching the supertype routes every format problem to a `null` result. Safe
            // w.r.t. cancellation: `decodeFromString` is non-suspend and cannot raise a
            // coroutine `CancellationException`.
            Timber.tag("PipelineDebug").e(e, "Error parsing tool selection JSON")
            null
        }
    }
}
