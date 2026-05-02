package ai.agent.android.data.prompt

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [TimeVariableProvider].
 */
class TimeVariableProviderTest {

    @Test
    fun `given key when called then returns TIME`() {
        val provider = TimeVariableProvider(clockProvider = { fixedClockAt("2026-05-01T09:07:00Z") })

        assertEquals("TIME", provider.key())
    }

    @Test
    fun `given fixed clock when resolve then formats as HH mm with zero padding`() = runTest {
        val provider = TimeVariableProvider(clockProvider = { fixedClockAt("2026-05-01T09:07:00Z") })

        assertEquals("09:07", provider.resolve())
    }

    @Test
    fun `given midnight when resolve then renders 00 00`() = runTest {
        val provider = TimeVariableProvider(clockProvider = { fixedClockAt("2026-05-01T00:00:00Z") })

        assertEquals("00:00", provider.resolve())
    }

    @Test
    fun `given evening time when resolve then uses 24 hour format`() = runTest {
        val provider = TimeVariableProvider(clockProvider = { fixedClockAt("2026-05-01T23:45:00Z") })

        assertEquals("23:45", provider.resolve())
    }

    @Test
    fun `given clock provider when resolve called twice then provider invoked each time`() = runTest {
        // Same intent as the analogous Date test: the zone may change mid-process and
        // each resolve must reflect the latest zone, so the provider must NOT be
        // captured once at construction.
        val sameInstant = Instant.parse("2026-05-01T15:00:00Z")
        val zones = ArrayDeque(
            listOf(
                Clock.fixed(sameInstant, ZoneId.of("UTC")),
                Clock.fixed(sameInstant, ZoneId.of("Asia/Tokyo")),
            )
        )
        val provider = TimeVariableProvider(clockProvider = { zones.removeFirst() })

        val first = provider.resolve()
        val second = provider.resolve()

        // 15:00 UTC vs same instant in Tokyo (+09:00) = 00:00 next day
        assertEquals("15:00", first)
        assertEquals("00:00", second)
    }

    private fun fixedClockAt(isoInstant: String): Clock =
        Clock.fixed(Instant.parse(isoInstant), ZoneId.of("UTC"))
}
