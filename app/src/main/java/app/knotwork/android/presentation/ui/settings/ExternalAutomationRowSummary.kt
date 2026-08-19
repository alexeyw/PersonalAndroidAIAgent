package app.knotwork.android.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.knotwork.android.R
import app.knotwork.android.domain.models.ExternalAutomationJournalEntry
import app.knotwork.android.domain.models.ExternalAutomationStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Summary copy for the three external-automation rows of the Background settings
// category. Split out of SettingsViewStateBuilders because it is the only slice
// of that file with its own vocabulary — the request-status dictionary — and the
// two questions it answers ("is this switched on but inert?" and "what happened
// to the last request?") are the ones the rows exist to answer at a glance.

/**
 * Label of the "pipeline other apps may run" row.
 *
 * An unbound-while-on surface gets the fuller sentence rather than the bare
 * "Not set" the other two entry surfaces use, because here the missing value is
 * not an untouched default — it is a switched-on entry point that accepts a
 * request and then refuses it. The row's warning glyph carries the same fact for
 * a reader who cannot see the tint.
 */
@Composable
internal fun externalAutomationBindingLabel(uiState: SettingsUiState): String = when {
    uiState.isExternalAutomationUnbound() -> stringResource(R.string.settings_external_pipeline_unbound)
    else -> uiState.bindablePipelines.firstOrNull { it.id == uiState.externalAutomationPipelineId }?.name
        ?: stringResource(R.string.settings_pipeline_not_set)
}

/**
 * Whether the external-automation surface is switched on with nothing it could
 * actually run — the state that accepts a request and refuses it, and therefore
 * the one the row has to flag rather than render as an untouched default.
 *
 * A binding that no longer resolves counts as unbound: deleting the bound
 * pipeline leaves an id behind that the authorizer cannot honour. The
 * unresolvable case is only reported once the pipeline list has actually loaded,
 * so the row does not flash a warning during the first frames of the screen.
 */
internal fun SettingsUiState.isExternalAutomationUnbound(): Boolean {
    if (!externalAutomationEnabled) return false
    val boundId = externalAutomationPipelineId ?: return true
    return bindablePipelines.isNotEmpty() && bindablePipelines.none { it.id == boundId }
}

/**
 * Summarises the newest inbound external request for the Background row.
 *
 * Names the **last event** rather than a count, because the reason to open this
 * row is almost always to diagnose one ("did my profile reach the app, and what
 * did it say?"), and a running total answers neither half of that question.
 *
 * The moment is day-scoped rather than relative ("14:32", or a dated form for an
 * earlier day) so the label cannot go stale while the screen sits open: the live
 * "12m ago" wording belongs on the journal screen, which ticks.
 */
@Composable
internal fun externalJournalSummary(latest: ExternalAutomationJournalEntry?): String {
    if (latest == null) return stringResource(R.string.settings_external_journal_none)
    val locale = LocalConfiguration.current.locales[0]
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    // Captured once per entry rather than read on every recomposition: the label
    // only distinguishes "today" from "not today", so a fresh read would change
    // nothing except determinism.
    val nowMillis = remember(latest.id) { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }
    val moment = Instant.ofEpochMilli(latest.receivedAt).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val clock = "%02d:%02d".format(moment.hour, moment.minute)
    val momentLabel = if (moment.toLocalDate() == today) {
        clock
    } else {
        stringResource(
            R.string.settings_external_journal_moment_dated,
            moment.toLocalDate().format(dayFormatter),
            clock,
        )
    }
    return stringResource(R.string.settings_external_journal_summary, externalStatusLabel(latest.status), momentLabel)
}

/** The status word shown on the Background row, from the shared status vocabulary. */
@Composable
private fun externalStatusLabel(status: ExternalAutomationStatus): String = stringResource(
    when (status) {
        ExternalAutomationStatus.Accepted -> R.string.external_automation_status_accepted
        ExternalAutomationStatus.Completed -> R.string.external_automation_status_completed
        ExternalAutomationStatus.Failed -> R.string.external_automation_status_failed
        is ExternalAutomationStatus.Rejected -> R.string.external_automation_status_rejected
        is ExternalAutomationStatus.Blocked -> R.string.external_automation_status_blocked
    },
)
