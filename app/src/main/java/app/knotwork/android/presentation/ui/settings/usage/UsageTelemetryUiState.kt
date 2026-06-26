package app.knotwork.android.presentation.ui.settings.usage

import app.knotwork.android.domain.models.UsageTelemetrySummary

/**
 * Screen state for the on-device Usage statistics screen.
 *
 * Holds the raw domain figures plus the recording opt-in; the screen renders
 * these into the catalog view-state, resolving localized labels and percentages
 * there (so this state carries no Android string concerns).
 *
 * @property loading Whether the first read of the counters / settings is still
 *   in flight.
 * @property recordingEnabled Whether local recording is currently on.
 * @property summary The aggregated on-device usage statistics.
 * @property pipelineNames Resolved `pipelineId → display name` for the
 *   per-pipeline rows and the export; ids absent from it render as "Unknown
 *   pipeline".
 */
data class UsageTelemetryUiState(
    val loading: Boolean,
    val recordingEnabled: Boolean,
    val summary: UsageTelemetrySummary,
    val pipelineNames: Map<String, String>,
) {
    /** Constants for [UsageTelemetryUiState]. */
    companion object {
        /** Initial state before the first read settles. */
        val LOADING = UsageTelemetryUiState(
            loading = true,
            recordingEnabled = true,
            summary = UsageTelemetrySummary.EMPTY,
            pipelineNames = emptyMap(),
        )
    }
}

/**
 * One-shot events emitted by the Usage statistics ViewModel for the screen to
 * act on (share the export through the system share sheet).
 */
sealed interface UsageTelemetryEvent {

    /**
     * The statistics were rendered to plain text and should be shared.
     *
     * @property text The human-readable export text.
     */
    data class ShareText(val text: String) : UsageTelemetryEvent
}
