package app.knotwork.design.screens.chat

/**
 * Preview fixtures for the content-report dialog.
 *
 * The five categories mirror the ones the application offers. A shorter list
 * would photograph a chip row that never wraps, and wrapping is exactly what
 * `FlowRow` is there to handle.
 */
object ReportResponseDialogPreview {

    /** The dialog as it opens, with the neutral category selected. */
    fun dialog(): ReportResponseDialogUi = ReportResponseDialogUi(
        title = "Report this response",
        body = "Tell us what was wrong with it. The report is written here and goes nowhere until you send it.",
        reasons = listOf(
            ReportReasonOptionUi(id = "HARMFUL_OR_UNSAFE", label = "Harmful or unsafe"),
            ReportReasonOptionUi(id = "SEXUALLY_EXPLICIT", label = "Sexually explicit"),
            ReportReasonOptionUi(id = "HATE_OR_HARASSMENT", label = "Hate or harassment"),
            ReportReasonOptionUi(id = "MISLEADING", label = "Misleading"),
            ReportReasonOptionUi(id = "OTHER", label = "Something else"),
        ),
        notePlaceholder = "What happened? (optional)",
        disclosure = "Nothing is sent from this screen. You choose whether to copy the report or open an issue.",
        copyLabel = "Copy report",
        openIssueLabel = "Open an issue",
        cancelLabel = "Cancel",
    )
}
