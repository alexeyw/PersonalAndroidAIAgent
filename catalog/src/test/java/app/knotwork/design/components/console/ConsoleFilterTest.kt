package app.knotwork.design.components.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [ConsoleFilter] — the predicate used by
 * [ConsolePane]'s Logs tab to drop filtered-out sources.
 */
class ConsoleFilterTest {

    private fun line(source: ConsoleSource): ConsoleLine = ConsoleLine(
        timestamp = "09:00:00.000",
        source = source,
        level = ConsoleLevel.Info,
        text = "demo",
    )

    @Test
    fun `allOn includes every source`() {
        val filter = ConsoleFilter.allOn
        ConsoleSource.entries.forEach { source ->
            assertTrue("Expected $source to match the all-on filter", filter.matches(line(source)))
        }
    }

    @Test
    fun `subset filter excludes other sources`() {
        val filter = ConsoleFilter(sources = setOf(ConsoleSource.TOOL))
        assertTrue(filter.matches(line(ConsoleSource.TOOL)))
        assertFalse(filter.matches(line(ConsoleSource.NODE)))
        assertFalse(filter.matches(line(ConsoleSource.RUNTIME)))
        assertFalse(filter.matches(line(ConsoleSource.USER)))
    }

    @Test
    fun `empty filter excludes every line`() {
        val filter = ConsoleFilter(sources = emptySet())
        ConsoleSource.entries.forEach { source ->
            assertFalse("Expected $source to be dropped by the empty filter", filter.matches(line(source)))
        }
    }

    @Test
    fun `allOn matches the canonical 'all sources' set`() {
        assertEquals(ConsoleSource.entries.toSet(), ConsoleFilter.allOn.sources)
    }
}
