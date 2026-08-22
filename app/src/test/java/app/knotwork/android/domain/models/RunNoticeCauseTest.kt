package app.knotwork.android.domain.models

import app.knotwork.android.domain.engine.stuck.StuckSignal
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
    fun `given the stuck cause then the diagnostic names the signal`() {
        val line = RunNoticeCause.LooksStuck(StuckSignal.REPEATED_STEP).diagnostic()

        assertTrue(line, line.contains(StuckSignal.REPEATED_STEP.diagnostic))
        assertEquals("a diagnostic is lower case", line, line.lowercase())
        assertTrue("a diagnostic is one line: '$line'", !line.contains("\n"))
    }

    @Test
    fun `given every cause then the vocabulary is covered and the diagnostics are distinct`() {
        // The completeness fixture `RunTerminationKind` has had since it was
        // introduced, and that this type went without: adding a variant is
        // otherwise caught only by the compiler's exhaustive `when`, which says
        // nothing about whether the new cause reached a surface.
        //
        // Listed by hand because a sealed interface with a data-class variant
        // has no `entries` to enumerate; the assertion below is what makes the
        // list a checklist rather than a sample.
        val everyCause: List<RunNoticeCause> = listOf(
            RunNoticeCause.ApproachingCeiling(RunCeilingAxis.STEPS, 12, 15),
            RunNoticeCause.LooksStuck(StuckSignal.NO_NEW_OUTPUT),
        )
        assertEquals(
            "add the new cause to everyCause, or nothing here covers it",
            RunNoticeCause::class.sealedSubclasses.toSet(),
            everyCause.map { it::class }.toSet(),
        )
        val lines = everyCause.map { it.diagnostic() }
        assertEquals("two causes must not log the same line", lines.size, lines.toSet().size)
        lines.forEach { line ->
            assertTrue("no trailing punctuation: '$line'", !line.endsWith("."))
        }
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
