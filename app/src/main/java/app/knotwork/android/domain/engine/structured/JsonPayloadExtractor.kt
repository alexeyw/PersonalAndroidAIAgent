package app.knotwork.android.domain.engine.structured

/**
 * Isolates the most likely JSON payload from free-form LLM output.
 *
 * Local models routinely wrap structured answers in a ```json fenced block,
 * bury them in explanatory prose, or emit them bare. Historically every
 * consumer grew its own extraction helper — object-only
 * (`ToolCallParser.extractJsonBlock`) or array-only
 * (`MemoryExtractionUseCase.extractJsonArray`) — which drifted in leniency.
 * This object is the single, shape-agnostic extraction used by
 * [StructuredOutputGate]; both object (`{ … }`) and array (`[ … ]`) payloads
 * are recovered by the same logic so the gate can validate either against a
 * `kotlinx.serialization` deserializer.
 */
object JsonPayloadExtractor {

    /**
     * Matches a fenced code block, optionally tagged `json`, capturing its inner
     * body. `DOT_MATCHES_ALL` lets the body span multiple lines; the lazy `.*?`
     * stops at the first closing fence.
     */
    private val FENCED_BLOCK_REGEX =
        """```(?:json)?\s*(.*?)\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)

    /**
     * Returns the best-effort JSON substring of [raw]:
     *  1. the inner body of a fenced ```json block when present, otherwise
     *  2. the span from the first opening bracket (`{` or `[`, whichever comes
     *     first) to the matching last closing bracket (`}` or `]`), otherwise
     *  3. the trimmed input as-is (lets the caller's deserializer produce the
     *     definitive parse error for genuinely non-JSON output).
     *
     * @param raw Raw model output.
     * @return The extracted JSON candidate string (never `null`; may still be
     *   invalid JSON, which is the gate's validation step to detect).
     */
    fun extract(raw: String): String {
        FENCED_BLOCK_REGEX.find(raw)?.groupValues?.get(1)?.let { fenced ->
            if (fenced.isNotBlank()) return fenced.trim()
        }

        val firstObject = raw.indexOf('{')
        val firstArray = raw.indexOf('[')
        val start = earliestPresent(firstObject, firstArray)
        if (start == -1) return raw.trim()

        val end = if (raw[start] == '{') raw.lastIndexOf('}') else raw.lastIndexOf(']')
        return if (end > start) raw.substring(start, end + 1) else raw.trim()
    }

    /**
     * Returns the smaller of two indices ignoring "absent" (`-1`) sentinels, or
     * `-1` when both are absent. Picks whichever bracket type opens first so an
     * object embedded before an array (or vice versa) is the one extracted.
     */
    private fun earliestPresent(a: Int, b: Int): Int = when {
        a == -1 -> b
        b == -1 -> a
        else -> minOf(a, b)
    }
}
