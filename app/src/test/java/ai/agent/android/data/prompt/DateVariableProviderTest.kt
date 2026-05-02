package ai.agent.android.data.prompt

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Unit tests for [DateVariableProvider].
 *
 * The clock and locale are pinned via constructor injection so the assertions
 * stay deterministic regardless of when / where the test runs.
 */
class DateVariableProviderTest {

    @Test
    fun `given key when called then returns DATE`() {
        val provider = DateVariableProvider(
            clock = fixedClockAt("2026-05-01T12:00:00Z"),
            localeProvider = { Locale.ENGLISH },
        )

        assertEquals("DATE", provider.key())
    }

    @Test
    fun `given fixed clock and english locale when resolve then formats as dd MMMM yyyy`() = runTest {
        val provider = DateVariableProvider(
            clock = fixedClockAt("2026-05-01T12:00:00Z"),
            localeProvider = { Locale.ENGLISH },
        )

        assertEquals("01 May 2026", provider.resolve())
    }

    @Test
    fun `given different month when resolve then uses month full name`() = runTest {
        val provider = DateVariableProvider(
            clock = fixedClockAt("2026-12-31T23:59:00Z"),
            localeProvider = { Locale.ENGLISH },
        )

        assertEquals("31 December 2026", provider.resolve())
    }

    @Test
    fun `given french locale when resolve then localizes month name`() = runTest {
        val provider = DateVariableProvider(
            clock = fixedClockAt("2026-05-01T12:00:00Z"),
            localeProvider = { Locale.FRENCH },
        )

        assertEquals("01 mai 2026", provider.resolve())
    }

    private fun fixedClockAt(isoInstant: String): Clock =
        Clock.fixed(Instant.parse(isoInstant), ZoneId.of("UTC"))
}
