package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.TriggerRunOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [triggerRunOutcomeForTerminal] — the pure mapping from a run's
 * terminal state to the trigger-journal outcome vocabulary. The behaviours worth
 * pinning are that a platform kill ([TriggerRunOutcome.CancelledBySystem]), a
 * deliberate stop ([TriggerRunOutcome.Cancelled]) and a background-HITL timeout
 * are each kept distinct from one another and from a genuine failure.
 */
class TriggerRunOutcomeMapperTest {

    @Test
    fun `given COMPLETED then maps to Success`() {
        assertEquals(TriggerRunOutcome.Success, triggerRunOutcomeForTerminal(PipelineRunStatus.COMPLETED, null))
    }

    @Test
    fun `given FAILED with a message then maps to a typed Failure carrying it`() {
        assertEquals(
            TriggerRunOutcome.Failure("network down"),
            triggerRunOutcomeForTerminal(PipelineRunStatus.FAILED, "network down"),
        )
    }

    @Test
    fun `given FAILED with no message then maps to a Failure with a neutral fallback`() {
        val outcome = triggerRunOutcomeForTerminal(PipelineRunStatus.FAILED, null)
        assertTrue(outcome is TriggerRunOutcome.Failure)
        assertTrue((outcome as TriggerRunOutcome.Failure).error.isNotBlank())
    }

    @Test
    fun `given FAILED with the approval-window-expired marker then maps to HitlTimeout, not Failure`() {
        assertEquals(
            TriggerRunOutcome.HitlTimeout,
            triggerRunOutcomeForTerminal(
                PipelineRunStatus.FAILED,
                ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE,
            ),
        )
    }

    @Test
    fun `given a graph-changed park failure then it is a genuine Failure, not a HitlTimeout`() {
        // Only the expiry marker means "user never answered"; other park failures
        // (e.g. the graph changed while parked) are real failures.
        assertEquals(
            TriggerRunOutcome.Failure(ParkedRunResumer.GRAPH_CHANGED_MESSAGE),
            triggerRunOutcomeForTerminal(PipelineRunStatus.FAILED, ParkedRunResumer.GRAPH_CHANGED_MESSAGE),
        )
    }

    @Test
    fun `given CANCELLED then maps to a deliberate Cancelled, not a platform kill`() {
        // CANCELLED is contractually "the user cancelled the run" — a deliberate
        // stop, which must not be reported as a platform (system) kill.
        assertEquals(
            TriggerRunOutcome.Cancelled,
            triggerRunOutcomeForTerminal(PipelineRunStatus.CANCELLED, null),
        )
    }

    @Test
    fun `given INTERRUPTED then maps to CancelledBySystem`() {
        // INTERRUPTED means the owning process died mid-run — the platform-kill
        // signal, distinct from a deliberate CANCELLED.
        assertEquals(
            TriggerRunOutcome.CancelledBySystem,
            triggerRunOutcomeForTerminal(PipelineRunStatus.INTERRUPTED, "Owning process died"),
        )
    }

    @Test
    fun `given every terminal status then a mapping exists`() {
        PipelineRunStatus.entries.filter { it.isTerminal }.forEach { status ->
            // Must not throw for any terminal status.
            triggerRunOutcomeForTerminal(status, null)
        }
    }

    @Test
    fun `given a non-terminal status then it throws`() {
        PipelineRunStatus.entries.filterNot { it.isTerminal }.forEach { status ->
            assertThrows(IllegalArgumentException::class.java) {
                triggerRunOutcomeForTerminal(status, null)
            }
        }
    }
}
