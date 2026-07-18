package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerEvaluation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Time of day, `0..23` : `0..59`, extracted from an evaluation's timestamp for
 * display. Locale- and format-free so the presentation layer renders it with the
 * device's own 12-/24-hour convention.
 *
 * @property hour Hour of the day in the 24-hour clock.
 * @property minute Minute of the hour.
 */
data class ClockTime(val hour: Int, val minute: Int)

/**
 * How an evaluation's moment is shown on its journal row: relative for the
 * freshest rows, absolute clock time for everything older. The grouper picks the
 * variant; the presentation layer resolves each to a localized string.
 */
sealed interface JournalTimestamp {
    /** Recorded within the last minute — reads as "just now". */
    data object JustNow : JournalTimestamp

    /**
     * Recorded within the last hour.
     *
     * @property minutes Whole minutes elapsed, `1..59`.
     */
    data class MinutesAgo(val minutes: Int) : JournalTimestamp

    /**
     * Recorded more than an hour ago — shown as the absolute time of day.
     *
     * @property time The clock time the evaluation was recorded at.
     */
    data class AbsoluteTime(val time: ClockTime) : JournalTimestamp
}

/**
 * The per-day section header a group of journal entries sits under.
 */
sealed interface JournalDayHeader {
    /** The current device-local day. */
    data object Today : JournalDayHeader

    /** The device-local day before [Today]. */
    data object Yesterday : JournalDayHeader

    /**
     * Any earlier day, shown as an absolute date (e.g. "Mon 14 Jul").
     *
     * @property date The device-local date of the group; the presentation layer
     *   formats it with the device locale.
     */
    data class Date(val date: LocalDate) : JournalDayHeader
}

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
 * Side-effect-free and clock-free — the caller supplies `nowMillis` and the
 * device `zone` — so day grouping and the relative/absolute timestamp choice are
 * fully deterministic and unit-testable across day boundaries and DST without a
 * real clock. String resolution (day-header labels, the "just now" / "12m ago"
 * wording, verdict / source / outcome / skip copy) stays in the presentation
 * layer, which has the resources and locale.
 *
 * Input is expected newest-first (the journal store returns it ordered by
 * `evaluatedAt` descending); the projection preserves that order within and
 * across day groups.
 */
class TriggerJournalGrouper @Inject constructor() {

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
    fun group(evaluations: List<TriggerEvaluation>, nowMillis: Long, zone: ZoneId): TriggerJournalView {
        if (evaluations.isEmpty()) return TriggerJournalView(emptyList())

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val groups = evaluations
            .map { it.toEntry(nowMillis, zone) to Instant.ofEpochMilli(it.evaluatedAt).atZone(zone).toLocalDate() }
            .groupByOrderedDay()
            .map { (date, entries) -> TriggerJournalDayGroup(header = dayHeader(date, today), entries = entries) }

        return TriggerJournalView(groups)
    }

    /** Projects one evaluation into a display entry against the current time. */
    private fun TriggerEvaluation.toEntry(nowMillis: Long, zone: ZoneId): TriggerJournalEntry {
        val time = Instant.ofEpochMilli(evaluatedAt).atZone(zone)
        val clock = ClockTime(hour = time.hour, minute = time.minute)
        return TriggerJournalEntry(
            evaluation = this,
            timestamp = timestampFor(nowMillis - evaluatedAt, clock),
            momentTime = clock,
        )
    }

    /** Chooses the relative-vs-absolute timestamp descriptor from the elapsed millis. */
    private fun timestampFor(elapsedMillis: Long, clock: ClockTime): JournalTimestamp {
        // A future-dated row (clock skew) is treated as "just now" rather than a
        // negative age.
        val elapsedMinutes = (elapsedMillis / MILLIS_PER_MINUTE).coerceAtLeast(0)
        return when {
            elapsedMinutes < 1 -> JournalTimestamp.JustNow
            elapsedMinutes < MINUTES_PER_HOUR -> JournalTimestamp.MinutesAgo(elapsedMinutes.toInt())
            else -> JournalTimestamp.AbsoluteTime(clock)
        }
    }

    /** Resolves a group's date to Today / Yesterday / an absolute date header. */
    private fun dayHeader(date: LocalDate, today: LocalDate): JournalDayHeader = when (date) {
        today -> JournalDayHeader.Today
        today.minusDays(1) -> JournalDayHeader.Yesterday
        else -> JournalDayHeader.Date(date)
    }

    /**
     * Buckets already-ordered `(entry, date)` pairs into contiguous same-day runs,
     * preserving the newest-first order both within and across days. A plain
     * `groupBy` would also collapse them by day but re-orders groups by first
     * appearance — which here is identical, yet this spelling makes the
     * order-preserving contract explicit and independent of `groupBy` internals.
     */
    private fun List<Pair<TriggerJournalEntry, LocalDate>>.groupByOrderedDay():
        List<Pair<LocalDate, List<TriggerJournalEntry>>> {
        val ordered = LinkedHashMap<LocalDate, MutableList<TriggerJournalEntry>>()
        for ((entry, date) in this) {
            ordered.getOrPut(date) { mutableListOf() }.add(entry)
        }
        return ordered.map { (date, entries) -> date to entries.toList() }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val MINUTES_PER_HOUR = 60L
    }
}
