package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live-only advisory raised while a run is still going.
 *
 * Its diagnostic follows the same rule as `RunTerminationReason.diagnostic()`:
 * this is the line an engineer greps, and the sentence a person reads is
 * resolved from the typed cause in the presentation layer.
 */
class RunNoticeCauseTest {

    @Test
    fun `given a soft crossing then the diagnostic names the axis and both numbers`() {
        val line = RunNoticeCause.ApproachingCeiling(
            axis = RunCeilingAxis.STEPS,
            spent = 12,
            hardLimit = 15,
        ).diagnostic()

        assertTrue(line, line.contains("12/15"))
        assertTrue(line, line.contains("steps"))
        assertEquals("a diagnostic is lower case", line, line.lowercase())
    }

    @Test
    fun `given each axis then the diagnostics differ`() {
        val steps = RunNoticeCause.ApproachingCeiling(RunCeilingAxis.STEPS, 12, 15).diagnostic()
        val tokens = RunNoticeCause.ApproachingCeiling(RunCeilingAxis.TOKENS, 12, 15).diagnostic()
        assertTrue("the axis has to be readable off the line", steps != tokens)
    }

    @Test
    fun `given the numbers then the cause carries them itself`() {
        // The notice can only exist while the run is in memory, which is exactly
        // what lets it always state both figures: the limit in force is not
        // persisted, and re-reading the setting later could report a number the
        // user has since changed.
        val cause = RunNoticeCause.ApproachingCeiling(RunCeilingAxis.TOKENS, spent = 780_000, hardLimit = 1_000_000)
        assertEquals(780_000, cause.spent)
        assertEquals(1_000_000, cause.hardLimit)
    }
}
