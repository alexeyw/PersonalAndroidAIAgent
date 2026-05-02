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
        val provider = TimeVariableProvider(fixedClockAt("2026-05-01T09:07:00Z"))

        assertEquals("TIME", provider.key())
    }

    @Test
    fun `given fixed clock when resolve then formats as HH mm with zero padding`() = runTest {
        val provider = TimeVariableProvider(fixedClockAt("2026-05-01T09:07:00Z"))

        assertEquals("09:07", provider.resolve())
    }

    @Test
    fun `given midnight when resolve then renders 00 00`() = runTest {
        val provider = TimeVariableProvider(fixedClockAt("2026-05-01T00:00:00Z"))

        assertEquals("00:00", provider.resolve())
    }

    @Test
    fun `given evening time when resolve then uses 24 hour format`() = runTest {
        val provider = TimeVariableProvider(fixedClockAt("2026-05-01T23:45:00Z"))

        assertEquals("23:45", provider.resolve())
    }

    private fun fixedClockAt(isoInstant: String): Clock =
        Clock.fixed(Instant.parse(isoInstant), ZoneId.of("UTC"))
}
