package app.knotwork.android.presentation.ui.settings.usage

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.android.R
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.UsageTelemetrySummary
import app.knotwork.design.screens.settings.UsageStatRow
import app.knotwork.design.screens.settings.UsageTelemetryCallbacks
import app.knotwork.design.screens.settings.UsageTelemetryContent
import app.knotwork.design.screens.settings.UsageTelemetryViewState
import kotlin.math.roundToInt

/** MIME type of the JSON usage-statistics export document. */
private const val MIME_JSON = "application/json"

/**
 * On-device Usage statistics screen (Settings → Privacy → Usage statistics).
 *
 * Renders the live local statistics, the recording opt-in, and the voluntary
 * text/JSON export. The JSON export is written through a Storage Access
 * Framework document picker; the text share goes through the system share sheet.
 * Nothing here leaves the device automatically.
 *
 * @param onBack Up navigation.
 * @param viewModel The screen ViewModel (Hilt-provided).
 */
@Composable
fun UsageTelemetryScreen(onBack: () -> Unit, viewModel: UsageTelemetryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }

    val exportFilename = stringResource(R.string.settings_usage_export_filename)
    val shareTitle = stringResource(R.string.settings_usage_share_title)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (stream != null) viewModel.writeJsonExport(stream)
    }

    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UsageTelemetryEvent.ShareText -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                        putExtra(Intent.EXTRA_TEXT, event.text)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, shareTitle))
                }
            }
        }
    }

    UsageTelemetryContent(
        state = buildUsageViewState(uiState),
        callbacks = UsageTelemetryCallbacks(
            onBack = onBack,
            onToggleRecording = viewModel::setRecordingEnabled,
            onShare = viewModel::shareAsText,
            onExportJson = { exportLauncher.launch(exportFilename) },
            onReset = { showResetConfirm = true },
        ),
    )

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_usage_reset_title)) },
            text = { Text(stringResource(R.string.settings_usage_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    showResetConfirm = false
                }) {
                    Text(stringResource(R.string.settings_usage_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.settings_usage_reset_cancel))
                }
            },
        )
    }
}

/** Maps the domain [UsageTelemetryUiState] into the catalog view-state with localized labels. */
@Composable
private fun buildUsageViewState(uiState: UsageTelemetryUiState): UsageTelemetryViewState {
    val summary = uiState.summary
    return UsageTelemetryViewState(
        recordingEnabled = uiState.recordingEnabled,
        isEmpty = summary.isEmpty,
        runsHeadline = stringResource(R.string.settings_usage_runs_total, summary.totalRuns),
        outcomes = buildOutcomeRows(summary),
        pipelines = summary.runsByPipeline.map { tally ->
            UsageStatRow(
                label = tally.pipelineId?.let { uiState.pipelineNames[it] }
                    ?: stringResource(R.string.settings_usage_pipeline_unknown),
                value = tally.runCount.toString(),
            )
        },
        triggersHeadline = stringResource(R.string.settings_usage_triggers_total, summary.totalTriggerFires),
        triggers = summary.triggerFiresByKind.toSortedMap().map { (kind, count) ->
            UsageStatRow(label = triggerKindLabel(kind), value = count.toString())
        },
        activeDays = buildActiveDayRows(summary),
    )
}

/** Builds the four terminal-outcome rows (always shown, including zeros), with percentages. */
@Composable
private fun buildOutcomeRows(summary: UsageTelemetrySummary): List<UsageStatRow> {
    val total = summary.totalRuns
    return listOf(
        PipelineRunStatus.COMPLETED to R.string.settings_usage_outcome_completed,
        PipelineRunStatus.FAILED to R.string.settings_usage_outcome_failed,
        PipelineRunStatus.CANCELLED to R.string.settings_usage_outcome_cancelled,
        PipelineRunStatus.INTERRUPTED to R.string.settings_usage_outcome_interrupted,
    ).map { (status, labelRes) ->
        val count = summary.runsByOutcome[status] ?: 0
        val percent = if (total == 0) 0 else (count.toDouble() * PERCENT / total).roundToInt()
        UsageStatRow(
            label = stringResource(labelRes),
            value = stringResource(R.string.settings_usage_outcome_value, count, percent),
        )
    }
}

/** Builds the active-day rows (count + first/last, with an em-dash for empty bounds). */
@Composable
private fun buildActiveDayRows(summary: UsageTelemetrySummary): List<UsageStatRow> {
    val none = stringResource(R.string.settings_usage_active_days_none)
    return listOf(
        UsageStatRow(stringResource(R.string.settings_usage_active_days_count), summary.activeDays.toString()),
        UsageStatRow(stringResource(R.string.settings_usage_active_days_first), summary.firstActiveDay ?: none),
        UsageStatRow(stringResource(R.string.settings_usage_active_days_last), summary.lastActiveDay ?: none),
    )
}

/** Maps a stable trigger telemetry-kind key to its localized label. */
@Composable
private fun triggerKindLabel(kind: String): String = when (kind) {
    "INTERVAL" -> stringResource(R.string.settings_usage_trigger_interval)
    "DAILY" -> stringResource(R.string.settings_usage_trigger_daily)
    "CHARGING" -> stringResource(R.string.settings_usage_trigger_charging)
    "NETWORK" -> stringResource(R.string.settings_usage_trigger_network)
    else -> kind
}

/** Percentage scale for the outcome share. */
private const val PERCENT = 100
