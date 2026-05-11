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
 * Both the clock and locale are pinned through provider lambdas so the
 * assertions stay deterministic regardless of when / where the test runs.
 */
class DateVariableProviderTest {

    @Test
    fun `given key when called then returns DATE`() {
        val provider = DateVariableProvider(
            clockProvider = { fixedClockAt("2026-05-01T12:00:00Z") },
            localeProvider = { Locale.ENGLISH },
        )

        assertEquals("DATE", provider.key())
    }

    @Test
    fun `given fixed clock and english locale when resolve then formats as dd MMMM yyyy`() = runTest {
        val provider = DateVariableProvider(
            clockProvider = { fixedClockAt("2026-05-01T12:00:00Z") },
            localeProvider = { Locale.ENGLISH },
        )

        assertEquals("01 May 2026", provider.resolve())
    }

    @Test
    fun `given different month when resolve then uses month full name`() = runTest {
        val provider = DateVariableProvider(
            clockProvider = { fixedClockAt("2026-12-31T23:59:00Z") },
            localeProvider = { Locale.ENGLISH },
        )

        assertEquals("31 December 2026", provider.resolve())
    }

    @Test
    fun `given french locale when resolve then localizes month name`() = runTest {
        val provider = DateVariableProvider(
            clockProvider = { fixedClockAt("2026-05-01T12:00:00Z") },
            localeProvider = { Locale.FRENCH },
        )

        assertEquals("01 mai 2026", provider.resolve())
    }

    @Test
    fun `given clock provider when resolve called twice then provider invoked each time`() = runTest {
        // Reflects the real-world scenario where the device crosses a time-zone
        // boundary mid-process: each resolve() must observe the updated zone, so
        // the provider lambda has to be invoked per call rather than memoised.
        val zones = ArrayDeque(
            listOf(
                Clock.fixed(Instant.parse("2026-05-01T23:30:00Z"), ZoneId.of("UTC")),
                Clock.fixed(Instant.parse("2026-05-01T23:30:00Z"), ZoneId.of("Asia/Tokyo")),
            ),
        )
        val provider = DateVariableProvider(
            clockProvider = { zones.removeFirst() },
            localeProvider = { Locale.ENGLISH },
        )

        val firstCall = provider.resolve()
        val secondCall = provider.resolve()

        // 23:30 UTC is still May 1; the same instant in Tokyo (+09:00) is already May 2.
        assertEquals("01 May 2026", firstCall)
        assertEquals("02 May 2026", secondCall)
    }

    private fun fixedClockAt(isoInstant: String): Clock = Clock.fixed(Instant.parse(isoInstant), ZoneId.of("UTC"))
}
