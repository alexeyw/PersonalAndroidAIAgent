package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.models.TriggerEvaluationVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Unit tests for [TriggerJournalGrouper]: day bucketing, the relative-vs-absolute
 * timestamp choice, moment-time extraction and order preservation, all against a
 * fixed clock and zone.
 */
class TriggerJournalGrouperTest {

    private val grouper = TriggerJournalGrouper()
    private val zone: ZoneId = ZoneOffset.UTC
    private val nowMillis = epoch(2026, 7, 18, 10, 30)

    private fun epoch(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun eval(id: String, atMillis: Long): TriggerEvaluation = TriggerEvaluation(
        id = id,
        triggerId = "trig-1",
        evaluatedAt = atMillis,
        source = TriggerEvaluationSource.POLL,
        verdict = TriggerEvaluationVerdict.Fired,
        runId = "run-$id",
    )

    @Test
    fun `given no evaluations when grouped then empty`() {
        val view = grouper.group(emptyList(), nowMillis, zone)
        assertTrue(view.dayGroups.isEmpty())
    }

    @Test
    fun `given evaluations across days when grouped then one section per device-local day`() {
        val rows = listOf(
            eval("e1", nowMillis), // today 10:30
            eval("e2", epoch(2026, 7, 18, 10, 18)), // today 10:18
            eval("e3", epoch(2026, 7, 18, 7, 30)), // today 07:30
            eval("e4", epoch(2026, 7, 17, 9, 0)), // yesterday
            eval("e5", epoch(2026, 7, 14, 8, 0)), // older
        )

        val view = grouper.group(rows, nowMillis, zone)

        assertEquals(3, view.dayGroups.size)
        assertEquals(JournalDayHeader.Today, view.dayGroups[0].header)
        assertEquals(3, view.dayGroups[0].entries.size)
        assertEquals(JournalDayHeader.Yesterday, view.dayGroups[1].header)
        assertEquals(1, view.dayGroups[1].entries.size)
        assertEquals(JournalDayHeader.Date(LocalDate.of(2026, 7, 14)), view.dayGroups[2].header)
    }

    @Test
    fun `given the newest rows when grouped then relative timestamps, older rows absolute`() {
        val rows = listOf(
            eval("just-now", nowMillis),
            eval("minutes", epoch(2026, 7, 18, 10, 18)), // 12m ago
            eval("absolute", epoch(2026, 7, 18, 7, 30)), // 3h ago
        )

        val entries = grouper.group(rows, nowMillis, zone).dayGroups.single().entries

        assertEquals(JournalTimestamp.JustNow, entries[0].timestamp)
        assertEquals(JournalTimestamp.MinutesAgo(12), entries[1].timestamp)
        assertEquals(JournalTimestamp.AbsoluteTime(ClockTime(7, 30)), entries[2].timestamp)
    }

    @Test
    fun `given an evaluation when grouped then its moment time is its clock time`() {
        val entry = grouper.group(listOf(eval("e", epoch(2026, 7, 18, 7, 15))), nowMillis, zone)
            .dayGroups.single().entries.single()
        assertEquals(ClockTime(7, 15), entry.momentTime)
    }

    @Test
    fun `given rows within a day when grouped then newest-first order is preserved`() {
        val rows = listOf(
            eval("newest", epoch(2026, 7, 18, 9, 0)),
            eval("middle", epoch(2026, 7, 18, 8, 0)),
            eval("oldest", epoch(2026, 7, 18, 7, 0)),
        )
        val ids = grouper.group(rows, nowMillis, zone).dayGroups.single().entries.map { it.evaluation.id }
        assertEquals(listOf("newest", "middle", "oldest"), ids)
    }

    @Test
    fun `given a future-dated row when grouped then it reads as just now`() {
        val entry = grouper.group(listOf(eval("skew", nowMillis + 5_000L)), nowMillis, zone)
            .dayGroups.single().entries.single()
        assertEquals(JournalTimestamp.JustNow, entry.timestamp)
    }
}
