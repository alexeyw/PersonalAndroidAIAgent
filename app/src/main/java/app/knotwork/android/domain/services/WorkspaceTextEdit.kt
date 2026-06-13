package app.knotwork.android.domain.services

/**
 * Pure find-and-replace primitive backing the `edit_file` workspace tool.
 *
 * Editing a file by a unique textual anchor — rather than by line number or by
 * re-emitting the whole file — is the pattern proven in agentic coding systems:
 * it forces the model to address exactly the fragment it means to change and
 * fails loudly when the anchor is ambiguous, instead of silently mutating the
 * wrong occurrence or clobbering the entire file.
 *
 * The matching contract:
 *
 *  - `oldText` must occur **exactly once** in the content. Zero matches yields
 *    [Outcome.AnchorNotFound]; two or more yields [Outcome.AnchorNotUnique]
 *    carrying the count, so the caller can ask for a longer anchor.
 *  - Occurrences are counted **non-overlapping** and matched **literally** (no
 *    regex / glob interpretation of `oldText`).
 *  - A blank `oldText` is not this object's concern — the caller rejects it
 *    before calling, since "replace the empty string" has no single target.
 *  - An empty `newText` is allowed and deletes the matched fragment.
 *
 * The logic is kept dependency-free in the `domain` layer (no Android, no
 * filesystem) so it is exhaustively unit-testable; the I/O of reading and
 * atomically rewriting the file lives in
 * [AgentWorkspace.editText][AgentWorkspace.editText].
 */
object WorkspaceTextEdit {

    /**
     * Outcome of applying an edit to a file's content.
     */
    sealed interface Outcome {
        /**
         * The anchor matched exactly once and was replaced.
         *
         * @property newContent The full file content after the single replacement.
         */
        data class Replaced(val newContent: String) : Outcome

        /** The anchor (`oldText`) does not occur in the content. */
        data object AnchorNotFound : Outcome

        /**
         * The anchor occurs more than once, so the target is ambiguous.
         *
         * @property count The number of non-overlapping occurrences found.
         */
        data class AnchorNotUnique(val count: Int) : Outcome
    }

    /**
     * Replaces the single occurrence of [oldText] in [content] with [newText].
     *
     * @param content The current full text of the file.
     * @param oldText The unique anchor fragment to replace. Callers must ensure
     *   it is non-empty; an empty anchor has no single target and is rejected
     *   upstream.
     * @param newText The replacement text. May be empty to delete the fragment.
     * @return [Outcome.Replaced] with the rewritten content when the anchor is
     *   unique, [Outcome.AnchorNotFound] when it is absent, or
     *   [Outcome.AnchorNotUnique] (with the count) when it is ambiguous.
     */
    fun apply(content: String, oldText: String, newText: String): Outcome {
        val count = countOccurrences(content, oldText)
        return when {
            count == 0 -> Outcome.AnchorNotFound
            count > 1 -> Outcome.AnchorNotUnique(count)
            // Exactly one occurrence: a literal first-match replacement rewrites it.
            else -> Outcome.Replaced(content.replaceFirst(oldText, newText))
        }
    }

    /**
     * Counts the non-overlapping occurrences of [needle] in [haystack],
     * scanning literally from the start and advancing past each whole match.
     *
     * @param haystack The text to scan.
     * @param needle The literal fragment to count.
     * @return The number of non-overlapping occurrences.
     */
    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val index = haystack.indexOf(needle, from)
            if (index < 0) break
            count++
            from = index + needle.length
        }
        return count
    }
}
