package app.knotwork.android.domain.report

/**
 * Renders a [ContentReport] into the plain-text report the user hands to the
 * maintainer.
 *
 * Pure and framework-free so the exact shape of a report is unit-testable: the
 * reported text is the one piece of user content that deliberately leaves the
 * app, and "what exactly is in it" must be answerable without launching the UI.
 *
 * Two invariants the tests pin down:
 *
 * 1. **The quoted output is bounded.** A model can emit far more text than a
 *    report body can carry, so the quote is truncated at [MAX_QUOTED_CHARS] and
 *    the omission is stated in the report rather than hidden — a silently
 *    clipped quote would misrepresent what the model said.
 * 2. **Only the declared fields appear.** Build metadata is limited to version,
 *    build identifier, device, Android version and model identifier; nothing
 *    reads settings, keys or history.
 */
object ContentReportComposer {

    /** Upper bound on the quoted model output, in characters. */
    const val MAX_QUOTED_CHARS: Int = 3000

    /**
     * Builds the one-line report subject.
     *
     * @param report The user's flag.
     * @return Subject line, e.g. `Content report: misleading output`.
     */
    fun subject(report: ContentReport): String = "Content report: ${label(report.reason)}"

    /**
     * Builds the report body in Markdown.
     *
     * @param report The user's flag.
     * @return Multi-line body: the user's detail, the category, the quoted
     *   model output (truncated when oversized), and the build metadata.
     */
    fun body(report: ContentReport): String = buildString {
        appendLine("### What is wrong")
        appendLine()
        appendLine(report.note.trim().ifBlank { NO_DETAIL })
        appendLine()
        appendLine("**Category:** ${label(report.reason)}")
        appendLine()
        appendLine("### Reported response")
        appendLine()
        appendLine(quote(report.messageText))
        appendLine()
        appendLine("### Build")
        appendLine()
        appendLine("- App: ${report.appVersion} (${report.buildIdentifier})")
        appendLine("- Device: ${report.device} · Android ${report.androidVersion}")
        report.modelIdentifier?.takeIf { it.isNotBlank() }?.let { appendLine("- Model: $it") }
    }.trimEnd()

    /**
     * Quotes model output as a Markdown block quote, truncating oversized text.
     *
     * @param text The model output as shown in the chat.
     * @return Block-quoted text, with an explicit truncation marker when the
     *   input exceeded [MAX_QUOTED_CHARS].
     */
    private fun quote(text: String): String {
        val trimmed = text.trim()
        val omitted = trimmed.length - MAX_QUOTED_CHARS
        val kept = if (omitted > 0) trimmed.take(MAX_QUOTED_CHARS) else trimmed
        val quoted = kept.lineSequence().joinToString(separator = "\n") { line -> "> $line" }
        return if (omitted > 0) "$quoted\n>\n> _[truncated — $omitted more characters]_" else quoted
    }

    /**
     * Maps a reason to the English label used in the report text.
     *
     * The report is read by the maintainer, so it is English regardless of
     * device locale; the localized labels belong to the dialog that collects
     * the report.
     *
     * @param reason The reported category.
     * @return Human-readable label for the report body and subject.
     */
    private fun label(reason: ContentReportReason): String = when (reason) {
        ContentReportReason.HARMFUL_OR_UNSAFE -> "harmful or unsafe output"
        ContentReportReason.SEXUALLY_EXPLICIT -> "sexually explicit output"
        ContentReportReason.HATE_OR_HARASSMENT -> "hate or harassment"
        ContentReportReason.MISLEADING -> "misleading output"
        ContentReportReason.OTHER -> "other"
    }

    /** Placeholder used when the user submits without typing a note. */
    private const val NO_DETAIL = "_(no additional detail provided)_"
}
