package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.OnboardingJourney
import app.knotwork.android.domain.models.OnboardingMilestone
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.UsageRetention
import app.knotwork.android.domain.models.UsageTelemetrySummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * The two voluntary export renderings of the local usage statistics: a
 * human-readable plain-text summary and a machine-readable JSON document.
 *
 * @property text Plain-text rendering suitable for an `ACTION_SEND` share.
 * @property json Pretty-printed JSON rendering suitable for a saved `.json` file.
 */
data class UsageTelemetryExport(val text: String, val json: String)

/**
 * Builds the voluntary export of the on-device usage statistics
 * ([UsageTelemetrySummary]) in both plain-text and JSON form.
 *
 * Pure domain logic: the use case only formats data the caller already holds —
 * it performs no I/O and, crucially, **no network access**. The user triggers
 * the export themselves (share-as-text or save-as-JSON) for their own dogfooding
 * analysis; nothing here transmits anything off the device.
 *
 * Pipeline ids are resolved to display names by the caller (via [pipelineNames])
 * so the domain stays free of any naming repository dependency; an id with no
 * mapping renders as [UNKNOWN_PIPELINE_LABEL].
 */
class BuildUsageTelemetryExportUseCase @Inject constructor() {

    /**
     * Renders [summary] to text + JSON.
     *
     * @param summary The aggregated local statistics to export.
     * @param pipelineNames Resolved `pipelineId → display name` map; ids absent
     *   from it (and the `null` id) render as [UNKNOWN_PIPELINE_LABEL].
     * @param generatedAtLabel Device-local "generated at" label, pre-formatted by
     *   the caller (the domain owns no date formatting).
     * @return The [UsageTelemetryExport] holding both renderings.
     */
    operator fun invoke(
        summary: UsageTelemetrySummary,
        pipelineNames: Map<String, String>,
        generatedAtLabel: String,
    ): UsageTelemetryExport = UsageTelemetryExport(
        text = buildText(summary, pipelineNames, generatedAtLabel),
        json = buildJson(summary, pipelineNames, generatedAtLabel),
    )

    /** Resolves a pipeline id (possibly `null`) to its display label. */
    private fun pipelineLabel(pipelineId: String?, pipelineNames: Map<String, String>): String =
        pipelineId?.let { pipelineNames[it] } ?: UNKNOWN_PIPELINE_LABEL

    /** Rounded integer percentage [count] is of [total]; `0` when [total] is zero. */
    private fun percent(count: Int, total: Int): Int =
        if (total == 0) 0 else (count.toDouble() * PERCENT / total).roundToInt()

    /**
     * Renders the plain-text summary. The opening line states the on-device
     * guarantee so a shared export carries its own privacy context.
     */
    private fun buildText(
        summary: UsageTelemetrySummary,
        pipelineNames: Map<String, String>,
        generatedAtLabel: String,
    ): String = buildString {
        appendLine("Knotwork — local usage statistics")
        appendLine("Generated $generatedAtLabel")
        appendLine("These statistics are stored only on this device; nothing was transmitted.")
        appendLine()

        appendLine("Runs: ${summary.totalRuns} total")
        for (status in PipelineRunStatus.entries.filter { it.isTerminal }) {
            val count = summary.runsByOutcome[status] ?: 0
            appendLine("  ${status.name}: $count (${percent(count, summary.totalRuns)}%)")
        }
        appendLine()

        appendLine("Runs by pipeline:")
        if (summary.runsByPipeline.isEmpty()) {
            appendLine("  (none)")
        } else {
            for (tally in summary.runsByPipeline) {
                appendLine("  ${pipelineLabel(tally.pipelineId, pipelineNames)}: ${tally.runCount}")
            }
        }
        appendLine()

        appendLine("Trigger firings: ${summary.totalTriggerFires} total")
        if (summary.triggerFiresByKind.isEmpty()) {
            appendLine("  (none)")
        } else {
            for ((kind, count) in summary.triggerFiresByKind.toSortedMap()) {
                appendLine("  $kind: $count")
            }
        }
        appendLine()

        appendLine("Active days: ${summary.activeDays}")
        appendLine("  First: ${summary.firstActiveDay ?: "—"}")
        appendLine("  Last: ${summary.lastActiveDay ?: "—"}")
        appendLine()

        val retention = summary.retention
        appendLine("This week (last ${UsageRetention.WINDOW_DAYS} days):")
        appendLine("  Active days: ${retention.activeDaysInWindow}/${UsageRetention.WINDOW_DAYS}")
        appendLine(
            "  Active days the week before: " +
                "${retention.activeDaysInPreviousWindow}/${UsageRetention.WINDOW_DAYS}",
        )
        appendLine("  Pipelines used: ${retention.livePipelinesInWindow}")
        // The unit is spelled into the label: a streak and a break are day
        // counts that the window does not bound, so a bare number is ambiguous.
        appendLine("  Current streak (days): ${retention.currentStreakDays}")
        appendLine("  Returns after a break: ${retention.returnsAfterBreak}")
        appendLine("  Longest break (days): ${retention.longestBreakDays}")
        appendLine("  First week after install: ${retention.firstWeekActiveDays ?: "—"}")
        appendLine()

        appendLine("Onboarding (install → first value):")
        val journey = summary.onboarding
        if (journey.isEmpty) {
            append("  (not recorded)")
        } else {
            appendLine("  Time to first value: ${durationLabel(journey.totalToValueMillis)}")
            appendLine("  Model download: ${durationLabel(journey.modelDownloadMillis)}")
            append("  Time to first value excluding download: ${durationLabel(journey.productToValueMillis)}")
        }
    }

    /**
     * Renders a duration as `12m 34s` (or `34s` under a minute); an unrecorded
     * duration renders as an em-dash so a partial journey reads honestly instead
     * of as a zero.
     */
    private fun durationLabel(millis: Long?): String {
        if (millis == null) return "—"
        val totalSeconds = millis / MILLIS_PER_SECOND
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    /** Renders the machine-readable JSON document. */
    private fun buildJson(
        summary: UsageTelemetrySummary,
        pipelineNames: Map<String, String>,
        generatedAtLabel: String,
    ): String {
        val document = buildJsonObject {
            put("generatedAt", generatedAtLabel)
            put("localOnly", true)
            put("totalRuns", summary.totalRuns)
            put("runsByOutcome", outcomeJson(summary))
            put("runsByPipeline", pipelineJson(summary, pipelineNames))
            put("triggerFiresByKind", triggerJson(summary))
            put("activeDays", summary.activeDays)
            put("firstActiveDay", summary.firstActiveDay)
            put("lastActiveDay", summary.lastActiveDay)
            put("retention", retentionJson(summary.retention))
            put("onboarding", onboardingJson(summary.onboarding))
        }
        return PRETTY_JSON.encodeToString(JsonObject.serializer(), document)
    }

    /**
     * The weekly-retention block. Carries the window definition (`windowDays`,
     * `breakThresholdDays`) alongside the figures, so a document read months
     * later still says what "this week" and "a break" meant when it was written,
     * and the live pipeline **ids** next to their count, so the number can be
     * re-derived instead of trusted.
     */
    private fun retentionJson(retention: UsageRetention): JsonObject = buildJsonObject {
        put("windowDays", UsageRetention.WINDOW_DAYS)
        put("breakThresholdDays", UsageRetention.MIN_BREAK_DAYS)
        put("activeDaysInWindow", retention.activeDaysInWindow)
        put("activeDaysInPreviousWindow", retention.activeDaysInPreviousWindow)
        put("livePipelinesInWindow", retention.livePipelinesInWindow)
        put(
            "livePipelineIds",
            buildJsonArray { for (id in retention.livePipelineIds) add(JsonPrimitive(id)) },
        )
        put("currentStreakDays", retention.currentStreakDays)
        put("returnsAfterBreak", retention.returnsAfterBreak)
        put("longestBreakDays", retention.longestBreakDays)
        put("firstWeekActiveDays", retention.firstWeekActiveDays)
    }

    /**
     * The onboarding journey block: the three durations in milliseconds (`null`
     * when not measurable) plus the raw markers, so an external analysis can
     * re-derive the figures instead of trusting these.
     */
    private fun onboardingJson(journey: OnboardingJourney): JsonObject = buildJsonObject {
        put("recorded", !journey.isEmpty)
        put("totalToValueMillis", journey.totalToValueMillis)
        put("modelDownloadMillis", journey.modelDownloadMillis)
        put("productToValueMillis", journey.productToValueMillis)
        put(
            "milestones",
            buildJsonObject {
                for (milestone in OnboardingMilestone.entries) {
                    put(milestone.name, journey.milestones[milestone])
                }
            },
        )
    }

    /** `{ COMPLETED: n, FAILED: n, … }` over the terminal statuses (always all four). */
    private fun outcomeJson(summary: UsageTelemetrySummary): JsonObject = buildJsonObject {
        for (status in PipelineRunStatus.entries.filter { it.isTerminal }) {
            put(status.name, summary.runsByOutcome[status] ?: 0)
        }
    }

    /** `[ { pipelineId, name, runCount }, … ]`, preserving the summary ordering. */
    private fun pipelineJson(summary: UsageTelemetrySummary, pipelineNames: Map<String, String>): JsonArray =
        buildJsonArray {
            for (tally in summary.runsByPipeline) {
                addJsonObject {
                    put("pipelineId", tally.pipelineId)
                    put("name", pipelineLabel(tally.pipelineId, pipelineNames))
                    put("runCount", tally.runCount)
                }
            }
        }

    /** `[ { kind, count }, … ]`, sorted by kind for a stable document. */
    private fun triggerJson(summary: UsageTelemetrySummary): JsonArray = buildJsonArray {
        for ((kind, count) in summary.triggerFiresByKind.toSortedMap()) {
            addJsonObject {
                put("kind", kind)
                put("count", count)
            }
        }
    }

    private companion object {
        /** Label rendered for runs whose pipeline id is unknown / unresolved. */
        const val UNKNOWN_PIPELINE_LABEL = "Unknown pipeline"

        /** Percentage scale. */
        const val PERCENT = 100

        /** Milliseconds in a second, for the duration labels. */
        const val MILLIS_PER_SECOND = 1_000L

        /** Seconds in a minute, for the duration labels. */
        const val SECONDS_PER_MINUTE = 60L

        /** Shared pretty-printing JSON configuration for the export document. */
        val PRETTY_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
