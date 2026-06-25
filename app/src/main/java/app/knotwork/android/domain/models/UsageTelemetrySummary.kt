package app.knotwork.android.domain.models

/**
 * Aggregated, fully **on-device** usage statistics surfaced on the
 * Settings → Privacy → Usage statistics screen.
 *
 * Every figure is derived only from local counters
 * ([app.knotwork.android.domain.repositories.UsageTelemetryRepository]) — nothing
 * here is ever transmitted off the device. The summary feeds a read-only
 * dashboard and the voluntary text/JSON export
 * ([app.knotwork.android.domain.usecases.BuildUsageTelemetryExportUseCase]) the
 * user can share for their own dogfooding analysis.
 *
 * @property runsByPipeline Per-pipeline tally of completed **root** runs (nested
 *   sub-pipeline runs are never counted, so a composed pipeline is one run, not
 *   N). Ordered by the repository newest-busiest first; may be empty.
 * @property runsByOutcome Count of terminal root runs per terminal
 *   [PipelineRunStatus] (`COMPLETED` / `FAILED` / `CANCELLED` / `INTERRUPTED`).
 *   A missing key means zero. Backs the completed/failed/interrupted share.
 * @property triggerFiresByKind Count of background trigger firings keyed by the
 *   stable [TriggerCondition.telemetryKind] string (`INTERVAL` / `DAILY` /
 *   `CHARGING` / `NETWORK`). A missing key means zero.
 * @property activeDays Number of distinct device-local calendar days on which at
 *   least one run finished (a privacy-preserving daily-active proxy — only the
 *   day count is kept, never per-day timestamps).
 * @property firstActiveDay Earliest recorded active day as an ISO `yyyy-MM-dd`
 *   string, or `null` when nothing has been recorded yet.
 * @property lastActiveDay Latest recorded active day as an ISO `yyyy-MM-dd`
 *   string, or `null` when nothing has been recorded yet.
 */
data class UsageTelemetrySummary(
    val runsByPipeline: List<PipelineRunTally>,
    val runsByOutcome: Map<PipelineRunStatus, Int>,
    val triggerFiresByKind: Map<String, Int>,
    val activeDays: Int,
    val firstActiveDay: String?,
    val lastActiveDay: String?,
) {
    /** Total number of terminal root runs across every outcome. */
    val totalRuns: Int get() = runsByOutcome.values.sum()

    /** Total number of background trigger firings across every kind. */
    val totalTriggerFires: Int get() = triggerFiresByKind.values.sum()

    /** Whether any usage has been recorded at all (used to drive the empty state). */
    val isEmpty: Boolean
        get() = totalRuns == 0 && totalTriggerFires == 0 && activeDays == 0

    /** Constants for [UsageTelemetrySummary]. */
    companion object {
        /** The zero summary: nothing recorded yet (or statistics just reset). */
        val EMPTY = UsageTelemetrySummary(
            runsByPipeline = emptyList(),
            runsByOutcome = emptyMap(),
            triggerFiresByKind = emptyMap(),
            activeDays = 0,
            firstActiveDay = null,
            lastActiveDay = null,
        )
    }
}

/**
 * A single per-pipeline run tally.
 *
 * @property pipelineId Id of the pipeline whose runs are counted, or `null` for
 *   runs whose pipeline was never resolved (e.g. a queued run orphaned by process
 *   death and swept to `INTERRUPTED`). The presentation layer resolves the id to
 *   a human-readable name and renders `null` as an "Unknown pipeline" label.
 * @property runCount Number of terminal root runs attributed to [pipelineId].
 */
data class PipelineRunTally(val pipelineId: String?, val runCount: Int)
