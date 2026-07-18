package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.TriggerRunOutcome

/**
 * Maps the terminal state of a background [PipelineRun][app.knotwork.android.domain.models.PipelineRun]
 * onto the [TriggerRunOutcome] recorded back onto the originating trigger-evaluation
 * journal row.
 *
 * This is the single translation from the run runtime's vocabulary
 * ([PipelineRunStatus] plus an optional error message) into the journal's
 * diagnostic vocabulary, so every consumer settling a run attributes the same
 * outcome for the same terminal state. It is a pure function — no I/O, no clock —
 * and so exhaustively unit-testable.
 *
 * The mapping deliberately keeps **platform** outcomes distinct from **product**
 * ones, which is the whole point of the background-reliability journal:
 * - [PipelineRunStatus.COMPLETED] → [TriggerRunOutcome.Success].
 * - [PipelineRunStatus.FAILED] carrying the background-HITL expiry marker
 *   ([ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE]) →
 *   [TriggerRunOutcome.HitlTimeout]: the run did not fail on its own, it parked on
 *   an approval/clarification the user never answered in the window. Distinguished
 *   here from a genuine failure so the journal does not blame the pipeline for an
 *   un-answered prompt.
 * - Any other [PipelineRunStatus.FAILED] → [TriggerRunOutcome.Failure] with the
 *   error message (or a neutral fallback when none was recorded).
 * - [PipelineRunStatus.CANCELLED] / [PipelineRunStatus.INTERRUPTED] →
 *   [TriggerRunOutcome.CancelledBySystem]: a run the system stopped (WorkManager
 *   reclaimed the worker, or the owning process died and the startup sweep reaped
 *   the record) is a platform event, **not** a product defect, and must never be
 *   conflated with [TriggerRunOutcome.Failure].
 *
 * @param status The terminal run status. Must satisfy [PipelineRunStatus.isTerminal];
 *   a non-terminal status is a caller bug and throws [IllegalArgumentException].
 * @param errorMessage The failure/interruption reason recorded on the run, or
 *   `null`. Only consulted for the [PipelineRunStatus.FAILED] branch.
 * @return The [TriggerRunOutcome] to attribute to the run's journal row.
 * @throws IllegalArgumentException if [status] is not terminal.
 */
fun triggerRunOutcomeForTerminal(status: PipelineRunStatus, errorMessage: String?): TriggerRunOutcome {
    require(status.isTerminal) { "triggerRunOutcomeForTerminal requires a terminal status, got $status" }
    return when (status) {
        PipelineRunStatus.COMPLETED -> TriggerRunOutcome.Success
        PipelineRunStatus.FAILED ->
            if (errorMessage == ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE) {
                TriggerRunOutcome.HitlTimeout
            } else {
                TriggerRunOutcome.Failure(errorMessage ?: DEFAULT_FAILURE_MESSAGE)
            }
        PipelineRunStatus.CANCELLED, PipelineRunStatus.INTERRUPTED -> TriggerRunOutcome.CancelledBySystem
        // Exhaustive over terminal statuses; the require() above rejects the rest.
        PipelineRunStatus.QUEUED,
        PipelineRunStatus.RUNNING,
        PipelineRunStatus.WAITING_APPROVAL,
        PipelineRunStatus.WAITING_CLARIFICATION,
        -> error("unreachable: non-terminal status $status passed the terminal guard")
    }
}

/** Neutral failure text used when a run finished FAILED without a recorded reason. */
private const val DEFAULT_FAILURE_MESSAGE = "Run failed"
