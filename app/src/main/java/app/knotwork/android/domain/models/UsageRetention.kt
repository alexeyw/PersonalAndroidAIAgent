package app.knotwork.android.domain.models

import java.time.LocalDate

/**
 * One `(day, pipeline)` pair of the on-device activity set: pipeline
 * [pipelineId] had at least one terminal **root** run on device-local calendar
 * day [day].
 *
 * The pair is the only time-resolved dimension the local telemetry keeps beyond
 * the plain active-day set — it is what makes "how many pipelines are alive this
 * week" answerable without storing per-run timestamps. Runs whose pipeline was
 * never resolved are not recorded here at all (an unattributable run cannot make
 * a pipeline "live").
 *
 * @property day Device-local calendar day the run terminated on.
 * @property pipelineId Id of the pipeline that ran.
 */
data class UsagePipelineDay(val day: LocalDate, val pipelineId: String)

/**
 * Weekly-retention aggregate derived from the local activity set — the
 * instrumentation half of the "is this product retained?" question.
 *
 * Every figure is computed on-device from data that is already stored (the set
 * of active days plus the `(day, pipeline)` set); nothing new about *what*
 * happened is recorded to produce it, and nothing here is ever transmitted.
 *
 * **What this can and cannot answer.** It measures the retention of *this
 * device's* user. It is the local instrument that proves the aggregates are
 * right and gives the author a real number; it is **not** a measurement of other
 * people's retention, which no purely on-device counter can ever provide. The
 * external half of that question is collected by hand (see the collection
 * procedure in the internal metrics protocol), not by this class.
 *
 * **Definitions are pre-committed** (fixed before the first measurement so a
 * later result cannot move the goalposts):
 * - *Window* — the last [WINDOW_DAYS] device-local days, **including today**:
 *   `[today − 6, today]`.
 * - *Previous window* — the [WINDOW_DAYS] days immediately before it:
 *   `[today − 13, today − 7]`.
 * - *Break* — [MIN_BREAK_DAYS] or more consecutive days with no activity.
 * - Days recorded ahead of `today` (a device-clock artefact) are ignored
 *   entirely rather than counted as activity.
 *
 * @property activeDaysInWindow Distinct active days inside the window, `0..7`.
 * @property activeDaysInPreviousWindow Distinct active days inside the previous
 *   window, `0..7`. Present so the current week reads as a comparison rather
 *   than as a bare number.
 * @property livePipelineIds Ids of the pipelines with at least one terminal root
 *   run inside the window, sorted for a stable rendering/export. Every terminal
 *   outcome counts — a failed run is still usage.
 * @property currentStreakDays Length of the run of consecutive active days
 *   ending at the most recent active day, reported only while that day is today
 *   or yesterday (an ended streak is `0`, not a stale headline).
 * @property returnsAfterBreak How many times activity resumed after a break —
 *   the count of active days preceded by [MIN_BREAK_DAYS]+ inactive days.
 * @property longestBreakDays Longest run of consecutive inactive days, including
 *   an absence that is still ongoing (counting only days that have already
 *   ended, so today never inflates it).
 * @property firstWeekActiveDays Active days in the first [WINDOW_DAYS] days from
 *   the earliest recorded activity — the local analogue of "first-week
 *   retention". `null` until that first week has fully elapsed, so a two-day-old
 *   install shows "not yet measurable" instead of a misleadingly low number.
 */
data class UsageRetention(
    val activeDaysInWindow: Int,
    val activeDaysInPreviousWindow: Int,
    val livePipelineIds: List<String>,
    val currentStreakDays: Int,
    val returnsAfterBreak: Int,
    val longestBreakDays: Int,
    val firstWeekActiveDays: Int?,
) {
    /** Number of distinct pipelines that ran inside the window. */
    val livePipelinesInWindow: Int get() = livePipelineIds.size

    /** Constants and the zero value for [UsageRetention]. */
    companion object {
        /** Length of the retention window in days (a week, inclusive of today). */
        const val WINDOW_DAYS: Int = 7

        /** Consecutive inactive days that count as a break the user can return from. */
        const val MIN_BREAK_DAYS: Int = 3

        /** The zero aggregate: nothing recorded yet (or statistics just reset). */
        val EMPTY = UsageRetention(
            activeDaysInWindow = 0,
            activeDaysInPreviousWindow = 0,
            livePipelineIds = emptyList(),
            currentStreakDays = 0,
            returnsAfterBreak = 0,
            longestBreakDays = 0,
            firstWeekActiveDays = null,
        )
    }
}
