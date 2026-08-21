package app.knotwork.android.domain.engine.structured

/**
 * Separates a reasoning-model's private scratchpad from the answer it is
 * wrapped around.
 *
 * Reasoning models (Qwen3, DeepSeek R1 and their derivatives) emit their
 * deliberation inside `<think>` … `</think>` before the text meant for the
 * reader. Left in place, that block is not merely noise in the chat bubble —
 * it is persisted as the agent's message, and from there it is replayed into
 * every later turn under `--- Chat History ---`, fed to history compression,
 * and offered to long-term memory. It also breaks parsing that has nothing to
 * do with chat: [JsonPayloadExtractor] spans from the first `{` to the last
 * `}`, and a model that drafts a candidate tool call while deliberating puts a
 * brace inside the scratchpad, which drags the extraction window across the
 * whole output.
 *
 * So the split happens at the executor boundary — the only layer that knows it
 * is talking to a model — and everything downstream sees the answer alone.
 *
 * The parser is deliberately forgiving, because the shapes below are what the
 * models actually produce rather than what their documentation describes:
 *
 *  - **A closing tag with no opening one.** Qwen3's chat template emits
 *    `<think>` itself as part of the prompt, so the model continues *inside*
 *    the block and the first tag in its output is `</think>`. This is the
 *    common case, not an edge case.
 *  - **An opening tag that never closes.** The generation hit a limit, or the
 *    stream was cut. Treating the whole output as reasoning would leave an
 *    empty answer, so [split] falls back to the raw text: an answer that shows
 *    its own scratchpad is worse than no answer only in tidiness.
 *  - **Several blocks**, interleaved with answer text.
 *  - **Tags inside a fenced code block**, which belong to the answer — someone
 *    asking how reasoning models are formatted must get their example back
 *    intact.
 */
object ReasoningBlockSplitter {

    /**
     * Result of separating an answer from its reasoning block.
     *
     * @property answer The text meant for the reader, with every reasoning block
     *   removed. Never blank when the input was not blank — see the fallbacks in
     *   [ReasoningBlockSplitter].
     * @property reasoning The concatenated reasoning text, or `null` when the
     *   output carried none (or when a fallback returned the raw text as the
     *   answer, in which case nothing was removed and there is nothing to
     *   report).
     */
    data class Split(val answer: String, val reasoning: String?)

    private const val OPEN_TAG = "<think>"
    private const val CLOSE_TAG = "</think>"

    /** Fenced code block, whose contents are answer text however they look. */
    private val FENCED_BLOCK_REGEX =
        """```.*?```""".toRegex(RegexOption.DOT_MATCHES_ALL)

    /**
     * Splits [raw] into the answer and the reasoning it was wrapped around.
     *
     * @param raw The model's unedited output.
     * @return The answer and the reasoning; the answer is [raw] unchanged when
     *   there is no complete reasoning block to remove, or when removing one
     *   would leave nothing behind.
     */
    fun split(raw: String): Split {
        if (raw.isBlank()) return Split(answer = raw, reasoning = null)

        val protectedRanges = FENCED_BLOCK_REGEX.findAll(raw).map { it.range }.toList()
        fun isProtected(index: Int): Boolean = protectedRanges.any { index in it }

        val answer = StringBuilder()
        val reasoning = StringBuilder()
        var cursor = 0
        var removedAny = false

        while (cursor < raw.length) {
            val open = raw.indexOfTagFrom(OPEN_TAG, cursor, ::isProtected)
            val close = raw.indexOfTagFrom(CLOSE_TAG, cursor, ::isProtected)

            // An orphan closing tag: the prompt template opened the block, so
            // everything up to it is scratchpad even though no opener is in sight.
            if (close != -1 && (open == -1 || close < open)) {
                reasoning.appendSeparated(raw.substring(cursor, close))
                cursor = close + CLOSE_TAG.length
                removedAny = true
                continue
            }
            if (open == -1) {
                answer.append(raw, cursor, raw.length)
                break
            }
            if (close == -1) {
                // Unterminated block — the generation was cut. Keep everything.
                return Split(answer = raw, reasoning = null)
            }
            // Reaching here, `close` is past `open`: the orphan branch above
            // already consumed every closing tag that precedes an opening one.
            answer.append(raw, cursor, open)
            reasoning.appendSeparated(raw.substring(open + OPEN_TAG.length, close))
            cursor = close + CLOSE_TAG.length
            removedAny = true
        }

        if (!removedAny) return Split(answer = raw, reasoning = null)

        val cleanedAnswer = answer.toString().trim()
        // Removing the block emptied the output: the model produced scratchpad
        // and nothing else. Returning the raw text keeps the run from ending on
        // a blank bubble, which reads as a failure rather than as an untidy answer.
        if (cleanedAnswer.isBlank()) return Split(answer = raw, reasoning = null)

        return Split(
            answer = cleanedAnswer,
            reasoning = reasoning.toString().trim().takeIf { it.isNotBlank() },
        )
    }

    /**
     * Index of the next [tag] at or after [from] that is not inside a fenced
     * code block, or `-1`.
     *
     * @param tag Literal tag to find, matched case-insensitively.
     * @param from Index to start scanning at.
     * @param isProtected Predicate marking indices that belong to a code fence.
     */
    private fun String.indexOfTagFrom(tag: String, from: Int, isProtected: (Int) -> Boolean): Int {
        var at = indexOf(tag, startIndex = from, ignoreCase = true)
        while (at != -1 && isProtected(at)) {
            at = indexOf(tag, startIndex = at + tag.length, ignoreCase = true)
        }
        return at
    }

    /** Appends [text] to a reasoning accumulator, blank-line separated. */
    private fun StringBuilder.appendSeparated(text: String) {
        if (text.isBlank()) return
        if (isNotEmpty()) append("\n\n")
        append(text.trim())
    }
}
