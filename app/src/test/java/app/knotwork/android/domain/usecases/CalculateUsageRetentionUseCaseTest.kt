package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.UsagePipelineDay
import app.knotwork.android.domain.models.UsageRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

/**
 * Verifies every boundary of the pre-committed retention definitions
 * ([UsageRetention]): the window edges, the previous-window comparison, which
 * pipelines count as live, the streak rule, what counts as a break the user
 * returned from, and the first-week figure.
 *
 * The definitions are fixed before the first measurement, so these tests are the
 * place a later change to them would have to be argued.
 */
class CalculateUsageRetentionUseCaseTest {

    private val useCase = CalculateUsageRetentionUseCase()

    /** Anchor "today" for every case; a Thursday, deliberately mid-month. */
    private val today: LocalDate = LocalDate.of(2026, 6, 25)

    /** [today] minus [days], as a shorthand for building activity sets. */
    private fun ago(days: Long): LocalDate = today.minusDays(days)

    private fun calculate(
        activeDays: List<LocalDate>,
        pipelineDays: List<UsagePipelineDay> = emptyList(),
        now: LocalDate = today,
    ): UsageRetention = useCase(activeDays = activeDays, pipelineDays = pipelineDays, today = now)

    @Test
    fun `given no activity when calculated then the aggregate is empty`() {
        assertSame(UsageRetention.EMPTY, calculate(activeDays = emptyList()))
    }

    @Test
    fun `given only days ahead of today when calculated then they are ignored as a clock artefact`() {
        assertSame(UsageRetention.EMPTY, calculate(activeDays = listOf(today.plusDays(1))))
    }

    @Test
    fun `given a day exactly six days back when calculated then it is inside the window`() {
        // The window is [today - 6, today] inclusive: seven days including today.
        val result = calculate(activeDays = listOf(ago(6)))

        assertEquals(1, result.activeDaysInWindow)
        assertEquals(0, result.activeDaysInPreviousWindow)
    }

    @Test
    fun `given a day exactly seven days back when calculated then it falls into the previous window`() {
        val result = calculate(activeDays = listOf(ago(7)))

        assertEquals(0, result.activeDaysInWindow)
        assertEquals(1, result.activeDaysInPreviousWindow)
    }

    @Test
    fun `given a day fourteen days back when calculated then it is outside both windows`() {
        // The previous window is [today - 13, today - 7]; day 14 is behind it.
        val result = calculate(activeDays = listOf(ago(14), ago(13)))

        assertEquals(0, result.activeDaysInWindow)
        assertEquals(1, result.activeDaysInPreviousWindow)
    }

    @Test
    fun `given duplicate active days when calculated then they collapse to one`() {
        val result = calculate(activeDays = listOf(ago(1), ago(1), ago(1)))

        assertEquals(1, result.activeDaysInWindow)
    }

    @Test
    fun `given a full week of activity when calculated then the window is saturated`() {
        val result = calculate(activeDays = (0L..6L).map(::ago))

        assertEquals(UsageRetention.WINDOW_DAYS, result.activeDaysInWindow)
        assertEquals(UsageRetention.WINDOW_DAYS, result.currentStreakDays)
        assertEquals(0, result.longestBreakDays)
    }

    @Test
    fun `given several runs of the same pipeline in the window when calculated then it counts once`() {
        val result = calculate(
            activeDays = listOf(ago(1), today),
            pipelineDays = listOf(
                UsagePipelineDay(ago(1), "pipe-b"),
                UsagePipelineDay(today, "pipe-b"),
                UsagePipelineDay(today, "pipe-a"),
            ),
        )

        assertEquals(listOf("pipe-a", "pipe-b"), result.livePipelineIds)
        assertEquals(2, result.livePipelinesInWindow)
    }

    @Test
    fun `given pipeline activity outside the window when calculated then it is not live`() {
        val result = calculate(
            activeDays = listOf(ago(20), today),
            pipelineDays = listOf(
                UsagePipelineDay(ago(20), "stale-pipe"),
                UsagePipelineDay(today, "fresh-pipe"),
            ),
        )

        assertEquals(listOf("fresh-pipe"), result.livePipelineIds)
    }

    @Test
    fun `given the last active day was yesterday when calculated then the streak still stands`() {
        // A streak must survive "has not opened the app yet today" — otherwise it
        // reads as broken every morning.
        val result = calculate(activeDays = listOf(ago(3), ago(2), ago(1)))

        assertEquals(3, result.currentStreakDays)
    }

    @Test
    fun `given the last active day was two days ago when calculated then the streak is over`() {
        val result = calculate(activeDays = listOf(ago(4), ago(3), ago(2)))

        assertEquals(0, result.currentStreakDays)
    }

    @Test
    fun `given a gap inside the streak when calculated then only the trailing run counts`() {
        val result = calculate(activeDays = listOf(ago(10), ago(9), ago(2), ago(1), today))

        assertEquals(3, result.currentStreakDays)
    }

    @Test
    fun `given a gap of two inactive days when calculated then it is not a return`() {
        // MIN_BREAK_DAYS is 3: two missed days is a busy week, not a lapse.
        val result = calculate(activeDays = listOf(ago(3), today))

        assertEquals(0, result.returnsAfterBreak)
        assertEquals(2, result.longestBreakDays)
    }

    @Test
    fun `given a gap of exactly three inactive days when calculated then it counts as a return`() {
        val result = calculate(activeDays = listOf(ago(4), today))

        assertEquals(1, result.returnsAfterBreak)
        assertEquals(3, result.longestBreakDays)
    }

    @Test
    fun `given several breaks when calculated then each return counts and the longest is reported`() {
        val result = calculate(activeDays = listOf(ago(30), ago(20), ago(10), today))

        assertEquals(3, result.returnsAfterBreak)
        assertEquals(9, result.longestBreakDays)
    }

    @Test
    fun `given an ongoing absence when calculated then it lengthens the longest break but is no return`() {
        // Nobody has come back from an absence that is still running.
        val result = calculate(activeDays = listOf(ago(21), ago(20)))

        assertEquals(0, result.returnsAfterBreak)
        assertEquals(19, result.longestBreakDays)
    }

    @Test
    fun `given the last activity was yesterday when calculated then no break has completed`() {
        val result = calculate(activeDays = listOf(ago(1)))

        assertEquals(0, result.longestBreakDays)
    }

    @Test
    fun `given the first week has not elapsed when calculated then the first-week figure is withheld`() {
        // Five days after the first run, "2 of 7" would read as poor retention
        // when it only means the week is still running.
        val result = calculate(activeDays = listOf(ago(5), ago(4)))

        assertNull(result.firstWeekActiveDays)
    }

    @Test
    fun `given the first week has just elapsed when calculated then its active days are counted`() {
        // First activity six days back: the seventh day of that week is today.
        val result = calculate(activeDays = listOf(ago(6), ago(5), today))

        assertEquals(3, result.firstWeekActiveDays)
    }

    @Test
    fun `given activity after the first week when calculated then it is not counted into it`() {
        val result = calculate(activeDays = listOf(ago(30), ago(29), ago(20), today))

        assertEquals(2, result.firstWeekActiveDays)
    }

    @Test
    fun `given activity spanning a year boundary when calculated then the arithmetic still holds`() {
        val newYear = LocalDate.of(2027, 1, 2)
        val result = calculate(
            activeDays = listOf(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 1), newYear),
            now = newYear,
        )

        assertEquals(3, result.activeDaysInWindow)
        assertEquals(2, result.currentStreakDays)
        assertEquals(1, result.longestBreakDays)
    }
}
