package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.RunTerminationReason
import app.knotwork.android.domain.models.TriggerRunOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [triggerRunOutcomeForTerminal] — the pure mapping from a run's
 * terminal state to the trigger-journal outcome vocabulary. The behaviours worth
 * pinning are that a platform kill ([TriggerRunOutcome.CancelledBySystem]), a
 * deliberate stop ([TriggerRunOutcome.Cancelled]), a background-HITL timeout and
 * a protective ceiling stop ([TriggerRunOutcome.StoppedByCeiling]) are each kept
 * distinct from one another and from a genuine failure — and that the
 * classification now comes from the typed [RunTerminationReason] rather than from
 * matching the recorded message text.
 */
class TriggerRunOutcomeMapperTest {

    @Test
    fun `given COMPLETED then maps to Success`() {
        assertEquals(TriggerRunOutcome.Success, triggerRunOutcomeForTerminal(PipelineRunStatus.COMPLETED, null, null))
    }

    @Test
    fun `given FAILED with a message then maps to a typed Failure carrying it`() {
        assertEquals(
            TriggerRunOutcome.Failure("network down"),
            triggerRunOutcomeForTerminal(PipelineRunStatus.FAILED, "network down", null),
        )
    }

    @Test
    fun `given FAILED with no message then maps to a Failure with a neutral fallback`() {
        val outcome = triggerRunOutcomeForTerminal(PipelineRunStatus.FAILED, null, null)
        assertTrue(outcome is TriggerRunOutcome.Failure)
        assertTrue((outcome as TriggerRunOutcome.Failure).error.isNotBlank())
    }

    @Test
    fun `given FAILED with the expired-window reason then maps to HitlTimeout, not Failure`() {
        assertEquals(
            TriggerRunOutcome.HitlTimeout,
            triggerRunOutcomeForTerminal(
                PipelineRunStatus.FAILED,
                ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE,
                RunTerminationReason.HitlWindowExpired,
            ),
        )
    }

    @Test
    fun `given the expiry message but no typed reason then it is a plain Failure`() {
        // The classification must come from the type. Carrying the old marker
        // text without the reason has to read as an ordinary failure — otherwise
        // the string channel is still load-bearing and editing the copy would
        // silently change how the journal reports a run.
        assertEquals(
            TriggerRunOutcome.Failure(ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE),
            triggerRunOutcomeForTerminal(
                PipelineRunStatus.FAILED,
                ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE,
                null,
            ),
        )
    }

    @Test
    fun `given a graph-changed park failure then it is a genuine Failure, not a HitlTimeout`() {
        // Only an expired window means "user never answered"; other park failures
        // (e.g. the graph changed while parked) are real failures.
        assertEquals(
            TriggerRunOutcome.Failure(ParkedRunResumer.GRAPH_CHANGED_MESSAGE),
            triggerRunOutcomeForTerminal(
                PipelineRunStatus.FAILED,
                ParkedRunResumer.GRAPH_CHANGED_MESSAGE,
                RunTerminationReason.GraphChanged,
            ),
        )
    }

    @Test
    fun `given a step-ceiling stop then maps to StoppedByCeiling, not Failure`() {
        // A safety limit doing its job is not a defect of the trigger. Reporting
        // it as one would redden the health badge for correct behaviour.
        assertEquals(
            TriggerRunOutcome.StoppedByCeiling,
            triggerRunOutcomeForTerminal(
                PipelineRunStatus.FAILED,
                "Pipeline execution exceeded the maximum of 15 steps shared across the pipeline tree.",
                RunTerminationReason.StepCeiling(limit = 15, spent = 15),
            ),
        )
    }

    @Test
    fun `given a token-ceiling stop then maps to StoppedByCeiling`() {
        assertEquals(
            TriggerRunOutcome.StoppedByCeiling,
            triggerRunOutcomeForTerminal(
                PipelineRunStatus.FAILED,
                "over budget",
                RunTerminationReason.TokenCeiling(limit = 100_000, spent = 100_400),
            ),
        )
    }

    @Test
    fun `given the stuck-detector then it is a Failure, not a ceiling stop`() {
        // A loop is the automation not working, which is a defect the trigger's
        // health should reflect — unlike a configured ceiling, which is a
        // number the user chose doing what they chose it for.
        val outcome = triggerRunOutcomeForTerminal(
            PipelineRunStatus.FAILED,
            // What the engine actually persists for a detector stop: the terse
            // diagnostic, not prose.
            "no-progress",
            RunTerminationReason.NoProgress,
        )
        assertTrue("a loop must redden the badge", outcome is TriggerRunOutcome.Failure)

        // And the journal must not show the diagnostic to a person. This row is
        // rendered verbatim beside the outcome in the trigger detail screen.
        val shown = (outcome as TriggerRunOutcome.Failure).error
        assertFalse("the journal must not print the log form; got: $shown", shown == "no-progress")
        assertTrue("the journal needs a sentence; got: $shown", shown.contains(" "))
    }

    @Test
    fun `given the silence watchdog then it is a Failure carrying its own recorded prose`() {
        // The counterpart, and the reason the two kinds exist separately: the
        // watchdog records a sentence at its own producer, so the journal keeps
        // it. A later change that moved this next to the ceilings — on the
        // plausible reading that "the app stopped it, so it is not the
        // pipeline's fault" — would stop reddening a badge for a hung tool.
        assertEquals(
            TriggerRunOutcome.Failure("stalled"),
            triggerRunOutcomeForTerminal(PipelineRunStatus.FAILED, "stalled", RunTerminationReason.RunStalled),
        )
    }

    @Test
    fun `given CANCELLED then maps to a deliberate Cancelled, not a platform kill`() {
        // CANCELLED is contractually "the user cancelled the run" — a deliberate
        // stop, which must not be reported as a platform (system) kill.
        assertEquals(
            TriggerRunOutcome.Cancelled,
            triggerRunOutcomeForTerminal(PipelineRunStatus.CANCELLED, null, null),
        )
    }

    @Test
    fun `given INTERRUPTED then maps to CancelledBySystem`() {
        // INTERRUPTED means the owning process died mid-run — the platform-kill
        // signal, distinct from a deliberate CANCELLED.
        assertEquals(
            TriggerRunOutcome.CancelledBySystem,
            triggerRunOutcomeForTerminal(
                PipelineRunStatus.INTERRUPTED,
                "Owning process died",
                RunTerminationReason.ProcessDied,
            ),
        )
    }

    @Test
    fun `given every terminal status then a mapping exists`() {
        PipelineRunStatus.entries.filter { it.isTerminal }.forEach { status ->
            // Must not throw for any terminal status.
            triggerRunOutcomeForTerminal(status, null, null)
        }
    }

    @Test
    fun `given a non-terminal status then it throws`() {
        PipelineRunStatus.entries.filterNot { it.isTerminal }.forEach { status ->
            assertThrows(IllegalArgumentException::class.java) {
                triggerRunOutcomeForTerminal(status, null, null)
            }
        }
    }
}
