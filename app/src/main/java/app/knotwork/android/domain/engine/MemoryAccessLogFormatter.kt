package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.MemoryChunk
import java.util.Locale

/**
 * Pure, clock-free formatter for the human-readable `MemoryAccess` console
 * message emitted by [GraphExecutionEngine] on every long-term-memory
 * retrieval.
 *
 * Two verbosity levels are produced from the same scored hit list:
 *
 *  - **Terse** (default): a single line carrying the truncated query, the hit
 *    count, and the per-hit similarity scores in retrieval order, e.g.
 *    `Memory: query='what is my ui preference' → 3 hits (0.82, 0.71, 0.55)`.
 *  - **Verbose** (Settings → Privacy → Verbose memory logging): the terse line
 *    plus one indented line per hit with its score and a snippet of the chunk
 *    text, e.g.
 *    ```
 *    Memory: query='…' → 2 hits (0.82, 0.71)
 *      1. [0.82] User prefers dark mode across the whole app.
 *      2. [0.71] User's name is Alex.
 *    ```
 *
 * Scores are formatted with [Locale.ROOT] so the decimal separator is always a
 * dot, independent of the device locale — the console is a developer-facing
 * diagnostic, not localised UI. The object is deterministic and free of Android
 * dependencies so it is trivially unit-testable.
 */
object MemoryAccessLogFormatter {

    /** Maximum number of query characters retained before an ellipsis is appended. */
    const val QUERY_MAX_LENGTH: Int = 60

    /** Maximum number of chunk-text characters retained in a verbose snippet. */
    const val SNIPPET_MAX_LENGTH: Int = 100

    /**
     * Builds the console message for one memory retrieval.
     *
     * @param query The original user prompt the memory was retrieved for. Newlines
     *   are collapsed to spaces and the result is truncated to [QUERY_MAX_LENGTH].
     * @param hits The scored hits returned by the retrieval, in result order
     *   (best first). Each pair is the chunk and its final post-rerank score.
     * @param verbose When `true`, append one indented snippet line per hit.
     * @return A single (possibly multi-line) message ready to drop into a
     *   `ConsoleEvent`.
     */
    fun format(query: String, hits: List<Pair<MemoryChunk, Float>>, verbose: Boolean): String {
        val header = buildString {
            append("Memory: query='")
            append(truncate(collapseWhitespace(query), QUERY_MAX_LENGTH))
            append("' → ")
            append(hits.size)
            append(" hits")
            if (hits.isNotEmpty()) {
                append(" (")
                append(hits.joinToString(", ") { formatScore(it.second) })
                append(")")
            }
        }

        if (!verbose || hits.isEmpty()) return header

        val detail = hits.mapIndexed { index, (chunk, score) ->
            "  ${index + 1}. [${formatScore(score)}] ${truncate(collapseWhitespace(chunk.text), SNIPPET_MAX_LENGTH)}"
        }.joinToString("\n")

        return "$header\n$detail"
    }

    /** Formats a similarity score to two fixed decimals with a dot separator. */
    private fun formatScore(score: Float): String = String.format(Locale.ROOT, "%.2f", score)

    /** Collapses any run of whitespace (including newlines) into a single space and trims. */
    private fun collapseWhitespace(text: String): String = text.replace(WHITESPACE_RUN, " ").trim()

    /** Truncates [text] to [max] characters, appending an ellipsis when it was cut. */
    private fun truncate(text: String, max: Int): String = if (text.length <= max) text else text.take(max) + "…"

    private val WHITESPACE_RUN = Regex("\\s+")
}
