package app.knotwork.android.domain.usecases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Unit tests for [JournalDayGrouper] — the day-bucketing and timestamp
 * arithmetic shared by the trigger evaluation journal and the
 * external-automation request journal.
 *
 * Exercised through a stand-in record rather than either real journal type, so
 * the tests pin the generic contract itself: whichever journal is wired to it
 * next inherits exactly this behaviour.
 */
class JournalDayGrouperTest {

    /** A minimal journal-shaped record: an identity and a moment. */
    private data class Record(val id: String, val recordedAt: Long)

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val grouper = JournalDayGrouper<Record> { it.recordedAt }

    /** 2026-08-18 14:30 local — the "now" every case is measured against. */
    private val now = at(2026, 8, 18, 14, 30)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `given no records when grouped then no sections`() {
        assertEquals(emptyList<JournalDaySection<Record>>(), grouper.group(emptyList(), now, zone))
    }

    @Test
    fun `given records across days when grouped then one section per device-local day`() {
        val records = listOf(
            Record("today", at(2026, 8, 18, 9, 0)),
            Record("yesterday", at(2026, 8, 17, 22, 15)),
            Record("older", at(2026, 8, 14, 7, 5)),
        )

        val sections = grouper.group(records, now, zone)

        assertEquals(3, sections.size)
        assertEquals(JournalDayHeader.Today, sections[0].header)
        assertEquals(JournalDayHeader.Yesterday, sections[1].header)
        assertEquals(JournalDayHeader.Date(LocalDate.of(2026, 8, 14)), sections[2].header)
    }

    @Test
    fun `given the record itself when grouped then it is carried through unchanged`() {
        val record = Record("carried", at(2026, 8, 18, 9, 0))

        val entry = grouper.group(listOf(record), now, zone).single().entries.single()

        assertEquals(record, entry.item)
    }

    @Test
    fun `given fresh records when grouped then relative timestamps, older ones absolute`() {
        val records = listOf(
            Record("seconds", now - 30_000L),
            Record("minutes", now - 12 * 60_000L),
            Record("hours", at(2026, 8, 18, 7, 5)),
        )

        val entries = grouper.group(records, now, zone).single().entries

        assertEquals(JournalTimestamp.JustNow, entries[0].timestamp)
        assertEquals(JournalTimestamp.MinutesAgo(12), entries[1].timestamp)
        assertEquals(JournalTimestamp.AbsoluteTime(ClockTime(7, 5)), entries[2].timestamp)
    }

    @Test
    fun `given the hour boundary when grouped then 59 minutes is relative and 60 is absolute`() {
        val records = listOf(
            Record("59m", now - 59 * 60_000L),
            Record("60m", now - 60 * 60_000L),
        )

        val entries = grouper.group(records, now, zone).single().entries

        assertEquals(JournalTimestamp.MinutesAgo(59), entries[0].timestamp)
        assertTrue(entries[1].timestamp is JournalTimestamp.AbsoluteTime)
    }

    @Test
    fun `given a record when grouped then its moment time is its device-local clock time`() {
        val entry = grouper.group(listOf(Record("r", at(2026, 8, 18, 7, 5))), now, zone).single().entries.single()

        assertEquals(ClockTime(hour = 7, minute = 5), entry.momentTime)
    }

    @Test
    fun `given records within a day when grouped then newest-first input order is preserved`() {
        val records = listOf(
            Record("a", at(2026, 8, 18, 11, 0)),
            Record("b", at(2026, 8, 18, 10, 0)),
            Record("c", at(2026, 8, 18, 9, 0)),
        )

        val entries = grouper.group(records, now, zone).single().entries

        assertEquals(listOf("a", "b", "c"), entries.map { it.item.id })
    }

    @Test
    fun `given interleaved days when grouped then each day keeps its own contiguous run`() {
        // Out-of-order input is not the contract, but it must not lose rows:
        // every record still lands under its own day rather than a neighbour's.
        val records = listOf(
            Record("t1", at(2026, 8, 18, 11, 0)),
            Record("y1", at(2026, 8, 17, 11, 0)),
            Record("t2", at(2026, 8, 18, 10, 0)),
        )

        val sections = grouper.group(records, now, zone)

        assertEquals(2, sections.size)
        assertEquals(listOf("t1", "t2"), sections[0].entries.map { it.item.id })
        assertEquals(listOf("y1"), sections[1].entries.map { it.item.id })
    }

    @Test
    fun `given a future-dated record when grouped then it reads as just now`() {
        // Clock skew between the writing process and the reading one must not
        // produce a negative age (which would render as "-3m ago").
        val entry = grouper.group(listOf(Record("skewed", now + 90_000L)), now, zone).single().entries.single()

        assertEquals(JournalTimestamp.JustNow, entry.timestamp)
    }

    @Test
    fun `given a moment near midnight when grouped then the device zone decides the day`() {
        // 2026-08-18 00:30 in Berlin is still 2026-08-17 22:30 UTC. Bucketing by
        // the device zone is what makes "Today" mean the user's today.
        val entry = grouper
            .group(listOf(Record("midnight", at(2026, 8, 18, 0, 30))), now, zone)
            .single()

        assertEquals(JournalDayHeader.Today, entry.header)
    }
}
