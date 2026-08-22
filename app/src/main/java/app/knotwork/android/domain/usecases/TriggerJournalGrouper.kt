package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerEvaluation
import java.time.ZoneId
import javax.inject.Inject

/**
 * One journal entry projected for display: the raw domain evaluation plus the
 * pre-computed timestamp descriptor and the time-of-day used by the
 * condition-not-met skip sentence ("…wasn't met at 07:15").
 *
 * @property evaluation The underlying evaluation record.
 * @property timestamp How to show its moment (relative / absolute).
 * @property momentTime The evaluation's clock time, named by the
 *   condition-not-met skip sentence.
 */
data class TriggerJournalEntry(
    val evaluation: TriggerEvaluation,
    val timestamp: JournalTimestamp,
    val momentTime: ClockTime,
)

/**
 * A contiguous run of entries recorded on the same device-local day.
 *
 * @property header The day the entries belong to.
 * @property entries The entries, newest first (input order preserved).
 */
data class TriggerJournalDayGroup(val header: JournalDayHeader, val entries: List<TriggerJournalEntry>)

/**
 * The whole journal, grouped into per-day sections for the detail timeline.
 *
 * @property dayGroups Sections newest day first; empty when there are no
 *   evaluations.
 */
data class TriggerJournalView(val dayGroups: List<TriggerJournalDayGroup>)

/**
 * Pure projection of a trigger's raw evaluation list into the grouped, display-
 * ready [TriggerJournalView] the detail timeline renders.
 *
 * The day bucketing and the relative/absolute timestamp choice live in the shared
 * [JournalDayGrouper]; this class is the trigger-shaped face of it, naming the
 * evaluation timestamp and re-wrapping the generic sections into the types the
 * trigger surfaces already speak. The arithmetic is shared with the
 * external-automation request journal so the two timelines cannot drift apart on
 * what "just now" or "Yesterday" means.
 *
 * Side-effect-free and clock-free — the caller supplies `nowMillis` and the device
 * `zone`. String resolution (day-header labels, the "just now" / "12m ago"
 * wording, verdict / source / outcome / skip copy) stays in the presentation
 * layer, which has the resources and locale.
 *
 * Input is expected newest-first (the journal store returns it ordered by
 * `evaluatedAt` descending); the projection preserves that order within and
 * across day groups.
 */
class TriggerJournalGrouper @Inject constructor() {

    /** The shared day-grouping arithmetic, told where a trigger evaluation keeps its moment. */
    private val grouper = JournalDayGrouper<TriggerEvaluation> { it.evaluatedAt }

    /**
     * Groups [evaluations] by device-local day and annotates each with a
     * timestamp descriptor.
     *
     * @param evaluations The trigger's evaluations, newest first.
     * @param nowMillis Current wall-clock time, epoch-millis — anchors "today",
     *   "yesterday", "just now" and "Nm ago".
     * @param zone The device time zone used to bucket timestamps into days and to
     *   read their clock time.
     * @return The grouped, display-ready view; [TriggerJournalView.dayGroups] is
     *   empty when [evaluations] is empty.
     */
    fun group(evaluations: List<TriggerEvaluation>, nowMillis: Long, zone: ZoneId): TriggerJournalView =
        TriggerJournalView(
            grouper.group(evaluations, nowMillis, zone).map { section ->
                TriggerJournalDayGroup(
                    header = section.header,
                    entries = section.entries.map { entry ->
                        TriggerJournalEntry(
                            evaluation = entry.item,
                            timestamp = entry.timestamp,
                            momentTime = entry.momentTime,
                        )
                    },
                )
            },
        )
}
