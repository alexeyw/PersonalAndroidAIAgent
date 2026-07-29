package app.knotwork.android.presentation.ui.chat.archive

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for the archived-at bucket ladder.
 *
 * The boundaries matter more than the middles: a chat archived 59 minutes ago
 * must not read "1 h ago" and a chat archived 25 hours ago must read
 * "yesterday", because the label is the only thing on the row that tells the
 * user when they put the conversation away.
 */
class ArchivedAtLabelTest {

    private val now: Long = TimeUnit.DAYS.toMillis(1_000)

    private fun ago(amount: Long, unit: TimeUnit): Long = now - unit.toMillis(amount)

    @Test
    fun `given no archive instant when bucketed then unknown`() {
        assertEquals(ArchivedAtLabel.Unknown, archivedAtLabel(archivedAt = null, now = now))
    }

    @Test
    fun `given a future instant when bucketed then unknown rather than a negative duration`() {
        val future = now + TimeUnit.HOURS.toMillis(3)

        assertEquals(ArchivedAtLabel.Unknown, archivedAtLabel(archivedAt = future, now = now))
    }

    @Test
    fun `given under a minute when bucketed then just now`() {
        assertEquals(ArchivedAtLabel.JustNow, archivedAtLabel(ago(59, TimeUnit.SECONDS), now))
    }

    @Test
    fun `given minutes when bucketed then minutes`() {
        assertEquals(ArchivedAtLabel.Minutes(1), archivedAtLabel(ago(1, TimeUnit.MINUTES), now))
        assertEquals(ArchivedAtLabel.Minutes(59), archivedAtLabel(ago(59, TimeUnit.MINUTES), now))
    }

    @Test
    fun `given exactly an hour when bucketed then hours`() {
        assertEquals(ArchivedAtLabel.Hours(1), archivedAtLabel(ago(60, TimeUnit.MINUTES), now))
    }

    @Test
    fun `given under a day when bucketed then hours`() {
        assertEquals(ArchivedAtLabel.Hours(23), archivedAtLabel(ago(23, TimeUnit.HOURS), now))
    }

    @Test
    fun `given between one and two days when bucketed then yesterday`() {
        assertEquals(ArchivedAtLabel.Yesterday, archivedAtLabel(ago(24, TimeUnit.HOURS), now))
        assertEquals(ArchivedAtLabel.Yesterday, archivedAtLabel(ago(47, TimeUnit.HOURS), now))
    }

    @Test
    fun `given two to six days when bucketed then days`() {
        assertEquals(ArchivedAtLabel.Days(2), archivedAtLabel(ago(2, TimeUnit.DAYS), now))
        assertEquals(ArchivedAtLabel.Days(6), archivedAtLabel(ago(6, TimeUnit.DAYS), now))
    }

    @Test
    fun `given a week or more when bucketed then weeks`() {
        assertEquals(ArchivedAtLabel.Weeks(1), archivedAtLabel(ago(7, TimeUnit.DAYS), now))
        assertEquals(ArchivedAtLabel.Weeks(4), archivedAtLabel(ago(29, TimeUnit.DAYS), now))
    }

    @Test
    fun `given a month or more when bucketed then months`() {
        assertEquals(ArchivedAtLabel.Months(1), archivedAtLabel(ago(30, TimeUnit.DAYS), now))
        assertEquals(ArchivedAtLabel.Months(11), archivedAtLabel(ago(350, TimeUnit.DAYS), now))
    }

    @Test
    fun `given a year or more when bucketed then years`() {
        assertEquals(ArchivedAtLabel.Years(1), archivedAtLabel(ago(365, TimeUnit.DAYS), now))
        assertEquals(ArchivedAtLabel.Years(2), archivedAtLabel(ago(800, TimeUnit.DAYS), now))
    }
}
