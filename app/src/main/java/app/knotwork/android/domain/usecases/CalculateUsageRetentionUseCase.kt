package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.UsagePipelineDay
import app.knotwork.android.domain.models.UsageRetention
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Derives the weekly-retention aggregate ([UsageRetention]) from the local
 * activity set.
 *
 * Pure domain logic: it folds data the caller already holds and performs no I/O
 * and — by construction — **no network access**. Keeping the window/streak/break
 * arithmetic here (rather than inside the repository fold) is what makes every
 * boundary of the pre-committed definitions directly unit-testable.
 *
 * The definitions themselves are documented on [UsageRetention]; this class is
 * their single implementation, so a definition can never drift between the
 * screen and the export.
 */
class CalculateUsageRetentionUseCase @Inject constructor() {

    /**
     * Computes the retention aggregate as of [today].
     *
     * @param activeDays Every recorded device-local active day; duplicates are
     *   collapsed and days after [today] (a device-clock artefact) are ignored.
     * @param pipelineDays Every recorded `(day, pipeline)` activity pair; only
     *   the ones inside the window are consulted.
     * @param today The device-local "today" the window is anchored to. Passed in
     *   rather than read from a clock so the domain owns no time source.
     * @return The aggregate, or [UsageRetention.EMPTY] when no usable activity
     *   has been recorded.
     */
    operator fun invoke(
        activeDays: Collection<LocalDate>,
        pipelineDays: Collection<UsagePipelineDay>,
        today: LocalDate,
    ): UsageRetention {
        val days = activeDays.filter { it <= today }.distinct().sorted()
        if (days.isEmpty()) return UsageRetention.EMPTY

        val window = today.minusDays((UsageRetention.WINDOW_DAYS - 1).toLong())..today
        val previousWindowEnd = window.start.minusDays(1)
        val previousWindow = previousWindowEnd.minusDays((UsageRetention.WINDOW_DAYS - 1).toLong())..previousWindowEnd
        val completedBreaks = breakLengths(days)

        return UsageRetention(
            activeDaysInWindow = days.count { it in window },
            activeDaysInPreviousWindow = days.count { it in previousWindow },
            livePipelineIds = pipelineDays
                .filter { it.day in window }
                .map { it.pipelineId }
                .distinct()
                .sorted(),
            currentStreakDays = currentStreakDays(days, today),
            // Only completed breaks count as returns: nobody has come back from
            // an absence that is still running. The longest break does include
            // it — an absence you are still in is the honest maximum.
            returnsAfterBreak = completedBreaks.count { it >= UsageRetention.MIN_BREAK_DAYS },
            longestBreakDays = (completedBreaks + ongoingBreakLength(days.last(), today)).max(),
            firstWeekActiveDays = firstWeekActiveDays(days, today),
        )
    }

    /**
     * Lengths (in whole inactive days) of every gap between consecutive active
     * days; a gap of `0` means the two days are adjacent. Empty for a single
     * active day.
     */
    private fun breakLengths(sortedDays: List<LocalDate>): List<Int> =
        sortedDays.zipWithNext { previous, next -> (ChronoUnit.DAYS.between(previous, next) - 1).toInt() }

    /**
     * Inactive days accumulated since [lastActiveDay] that have already ended.
     * Today is excluded because it is still in progress — an absence should not
     * grow just because the user has not opened the app *yet* this morning.
     */
    private fun ongoingBreakLength(lastActiveDay: LocalDate, today: LocalDate): Int =
        (ChronoUnit.DAYS.between(lastActiveDay, today) - 1).coerceAtLeast(0).toInt()

    /**
     * Length of the streak of consecutive active days ending at the most recent
     * active day — reported only while that day is today or yesterday, so a
     * streak that has already been broken reads as `0` rather than as a stale
     * achievement.
     */
    private fun currentStreakDays(sortedDays: List<LocalDate>, today: LocalDate): Int {
        val anchor = sortedDays.last()
        if (ChronoUnit.DAYS.between(anchor, today) > 1) return 0
        var streak = 1
        var expected = anchor.minusDays(1)
        for (day in sortedDays.asReversed().drop(1)) {
            if (day != expected) break
            streak++
            expected = expected.minusDays(1)
        }
        return streak
    }

    /**
     * Active days inside the first week of recorded activity, or `null` while
     * that week is still running — a partial first week would read as poor
     * retention when it only means "installed two days ago".
     */
    private fun firstWeekActiveDays(sortedDays: List<LocalDate>, today: LocalDate): Int? {
        val firstDay = sortedDays.first()
        val firstWeekEnd = firstDay.plusDays((UsageRetention.WINDOW_DAYS - 1).toLong())
        if (today < firstWeekEnd) return null
        return sortedDays.count { it in firstDay..firstWeekEnd }
    }
}
