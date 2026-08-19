package app.knotwork.android.domain.usecases

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Time of day, `0..23` : `0..59`, extracted from a journal record's timestamp for
 * display. Format-free (the presentation layer renders it); the journal surfaces
 * render it in the **24-hour** convention used consistently across the app (the
 * trigger editor's time field is labelled "24H", condition lines read "08:00").
 *
 * @property hour Hour of the day in the 24-hour clock.
 * @property minute Minute of the hour.
 */
data class ClockTime(val hour: Int, val minute: Int)

/**
 * How a record's moment is shown on its journal row: relative for the freshest
 * rows, absolute clock time for everything older. The grouper picks the variant;
 * the presentation layer resolves each to a localized string.
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
     * @property time The clock time the record was written at.
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
 * One journal record projected for display: the record itself plus the
 * pre-computed timestamp descriptor and its clock time.
 *
 * @property item The underlying record.
 * @property timestamp How to show its moment (relative / absolute).
 * @property momentTime The record's clock time, for sentences that name it
 *   ("…wasn't met at 07:15").
 */
data class JournalDayEntry<T>(val item: T, val timestamp: JournalTimestamp, val momentTime: ClockTime)

/**
 * A contiguous run of records written on the same device-local day.
 *
 * @property header The day the entries belong to.
 * @property entries The entries, newest first (input order preserved).
 */
data class JournalDaySection<T>(val header: JournalDayHeader, val entries: List<JournalDayEntry<T>>)

/**
 * Pure projection of a raw journal record list into day-grouped, display-ready
 * sections.
 *
 * The app has two journals that answer the same question about different entry
 * points — the trigger evaluation journal (background scheduling) and the
 * external-automation request journal (third-party callers) — and both render the
 * same timeline shape: day headers, newest first, relative timestamps for the
 * freshest rows. This type owns that arithmetic **once**, so "just now", the
 * hour boundary, the Today/Yesterday cut and the DST/clock-skew handling cannot
 * drift apart between the two surfaces.
 *
 * Side-effect-free and clock-free — the caller supplies `nowMillis` and the device
 * `zone` — so day grouping and the relative/absolute choice are fully
 * deterministic and unit-testable across day boundaries and DST without a real
 * clock. String resolution (day-header labels, the "just now" / "12m ago"
 * wording, and every per-journal vocabulary) stays in the presentation layer,
 * which has the resources and locale.
 *
 * Input is expected newest-first (both journal stores return their rows ordered
 * by timestamp descending); the projection preserves that order within and across
 * day groups.
 *
 * @param T The journal record type.
 * @property recordedAtOf Reads the record's epoch-millis timestamp. Passed rather
 *   than required as an interface on [T] so the domain records stay plain data
 *   classes with no display-driven supertype.
 */
class JournalDayGrouper<T>(private val recordedAtOf: (T) -> Long) {

    /**
     * Groups [items] by device-local day and annotates each with a timestamp
     * descriptor.
     *
     * @param items The journal records, newest first.
     * @param nowMillis Current wall-clock time, epoch-millis — anchors "today",
     *   "yesterday", "just now" and "Nm ago".
     * @param zone The device time zone used to bucket timestamps into days and to
     *   read their clock time.
     * @return The grouped sections, newest day first; empty when [items] is empty.
     */
    fun group(items: List<T>, nowMillis: Long, zone: ZoneId): List<JournalDaySection<T>> {
        if (items.isEmpty()) return emptyList()

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return items
            .map { item ->
                val date = Instant.ofEpochMilli(recordedAtOf(item)).atZone(zone).toLocalDate()
                item.toEntry(nowMillis, zone) to date
            }
            .groupByOrderedDay()
            .map { (date, entries) -> JournalDaySection(header = dayHeader(date, today), entries = entries) }
    }

    /** Projects one record into a display entry against the current time. */
    private fun T.toEntry(nowMillis: Long, zone: ZoneId): JournalDayEntry<T> {
        val recordedAt = recordedAtOf(this)
        val time = Instant.ofEpochMilli(recordedAt).atZone(zone)
        val clock = ClockTime(hour = time.hour, minute = time.minute)
        return JournalDayEntry(
            item = this,
            timestamp = timestampFor(nowMillis - recordedAt, clock),
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
    private fun List<Pair<JournalDayEntry<T>, LocalDate>>.groupByOrderedDay():
        List<Pair<LocalDate, List<JournalDayEntry<T>>>> {
        val ordered = LinkedHashMap<LocalDate, MutableList<JournalDayEntry<T>>>()
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
