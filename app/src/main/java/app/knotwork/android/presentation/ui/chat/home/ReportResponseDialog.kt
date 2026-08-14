package app.knotwork.android.presentation.ui.chat.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.knotwork.android.R
import app.knotwork.android.domain.report.ContentReportReason
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.KnotworkFilterChip
import app.knotwork.design.components.controls.KnotworkTextArea
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles
import java.net.URLEncoder

/**
 * Dialog that collects a user's flag against a model-authored message.
 *
 * Exists because an app that generates content with a model has to let the user
 * report that content **from inside the app**. The dialog is the reporting
 * surface: it collects the category and the user's own words, and then hands
 * the finished report back to the caller, which offers the two delivery routes
 * (copy it, or open a prefilled issue). Nothing is transmitted by the dialog
 * itself, and the copy on screen says so — an app whose privacy claim is "no
 * server of mine" cannot quietly grow a reporting endpoint.
 *
 * Stateless by design: every piece of state is hoisted to the chat screen, so
 * the dialog re-renders from arguments and stays previewable.
 *
 * @param reason Currently selected category.
 * @param onReasonChange Invoked when the user picks a different category.
 * @param note Free-text detail typed by the user.
 * @param onNoteChange Invoked on every edit of the note.
 * @param onCopy Copies the rendered report to the clipboard.
 * @param onOpenIssue Opens the public issue tracker with the report prefilled.
 * @param onDismiss Closes the dialog without reporting anything.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportResponseDialog(
    reason: ContentReportReason,
    onReasonChange: (ContentReportReason) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    onCopy: () -> Unit,
    onOpenIssue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_report_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.chat_report_dialog_body),
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurface2,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                    verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                ) {
                    ContentReportReason.entries.forEach { entry ->
                        KnotworkFilterChip(
                            label = stringResource(entry.labelRes()),
                            selected = entry == reason,
                            onClick = { onReasonChange(entry) },
                        )
                    }
                }
                KnotworkTextArea(
                    value = note,
                    onValueChange = onNoteChange,
                    placeholder = stringResource(R.string.chat_report_dialog_note_placeholder),
                    monospace = false,
                    highlightVariables = false,
                    minLines = 2,
                    maxLines = 5,
                )
                Text(
                    text = stringResource(R.string.chat_report_dialog_disclosure),
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceDim,
                )
            }
        },
        confirmButton = {
            KnotworkTextButton(
                text = stringResource(R.string.chat_report_dialog_open_issue),
                onClick = onOpenIssue,
            )
        },
        dismissButton = {
            KnotworkTextButton(
                text = stringResource(R.string.chat_report_dialog_copy),
                onClick = onCopy,
            )
        },
    )
}

/**
 * Builds the URL that opens the public issue tracker with a report prefilled.
 *
 * The body travels in the query string, and a query string is not unbounded:
 * an over-long URL is silently refused or clipped by the browser, which would
 * hand the maintainer a half-report. So the body is truncated **here**, to a
 * length measured after percent-encoding, and the truncation is stated in the
 * text — the same rule the report body itself follows.
 *
 * @param subject Issue title.
 * @param body Rendered report body.
 * @return Fully-encoded `issues/new` URL.
 */
internal fun contentReportIssueUrl(subject: String, body: String): String {
    val encodedSubject = encode(subject)
    var candidate = body
    var encodedBody = encode(candidate)
    val budget = MAX_ISSUE_URL_CHARS - ISSUE_TRACKER_NEW_URL.length - encodedSubject.length - QUERY_OVERHEAD_CHARS
    if (encodedBody.length > budget) {
        // Percent-encoding expands by an unknown factor (1x for plain ASCII,
        // up to 9x for a multi-byte character), so the fitting length cannot be
        // computed — it is searched for by halving until the encoded form fits.
        var keep = candidate.length
        while (encodedBody.length > budget && keep > 0) {
            keep /= 2
            candidate = body.take(keep) + ISSUE_TRUNCATION_MARKER
            encodedBody = encode(candidate)
        }
    }
    return "$ISSUE_TRACKER_NEW_URL?title=$encodedSubject&body=$encodedBody"
}

/**
 * Percent-encodes a query-parameter value.
 *
 * @param value Raw text.
 * @return The value encoded for use inside a query string.
 */
private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

/** `issues/new` endpoint of the public repository. */
private const val ISSUE_TRACKER_NEW_URL = "https://github.com/alexeyw/knotwork/issues/new"

/**
 * Upper bound on the generated URL. Browsers differ on where they give up;
 * 8000 characters is below every limit in circulation and far above a report
 * anyone will read.
 */
private const val MAX_ISSUE_URL_CHARS = 8000

/** Room reserved for `?title=` and `&body=` around the encoded values. */
private const val QUERY_OVERHEAD_CHARS = 16

/** Appended to a report body that had to be shortened to fit the URL. */
private const val ISSUE_TRUNCATION_MARKER =
    "\n\n_[report truncated to fit the link — copy the full report instead]_"

/**
 * Maps a report category to its localized chip label.
 *
 * Kept next to the dialog rather than on the enum so the `domain` layer stays
 * free of Android resource identifiers.
 *
 * @return String resource for this category.
 */
private fun ContentReportReason.labelRes(): Int = when (this) {
    ContentReportReason.HARMFUL_OR_UNSAFE -> R.string.chat_report_reason_harmful
    ContentReportReason.SEXUALLY_EXPLICIT -> R.string.chat_report_reason_sexual
    ContentReportReason.HATE_OR_HARASSMENT -> R.string.chat_report_reason_hate
    ContentReportReason.MISLEADING -> R.string.chat_report_reason_misleading
    ContentReportReason.OTHER -> R.string.chat_report_reason_other
}
