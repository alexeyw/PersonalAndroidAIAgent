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
 * and **deliberate** ones, which is the whole point of the background-reliability
 * journal:
 * - [PipelineRunStatus.COMPLETED] → [TriggerRunOutcome.Success].
 * - [PipelineRunStatus.FAILED] carrying the background-HITL expiry marker
 *   ([ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE]) →
 *   [TriggerRunOutcome.HitlTimeout]: the run did not fail on its own, it parked on
 *   an approval/clarification the user never answered in the window. Distinguished
 *   here from a genuine failure so the journal does not blame the pipeline for an
 *   un-answered prompt.
 * - Any other [PipelineRunStatus.FAILED] → [TriggerRunOutcome.Failure] with the
 *   error message (or a neutral fallback when none was recorded).
 * - [PipelineRunStatus.INTERRUPTED] → [TriggerRunOutcome.CancelledBySystem]: the
 *   owning process died mid-run and the startup sweep reaped the record — the
 *   platform-kill signal (OEM background killer / OOM). Never conflated with a
 *   [TriggerRunOutcome.Failure] or with a deliberate stop.
 * - [PipelineRunStatus.CANCELLED] → [TriggerRunOutcome.Cancelled]: a
 *   **deliberate** in-process stop (the user pressed Stop, or the hosting service
 *   was torn down). Its own contract is "the user cancelled the run — not a
 *   failure", so it must not be reported as a platform kill either.
 *
 * The terminal statuses are matched exhaustively (no `else`), so adding a future
 * terminal status is a compile error here rather than a silent mis-mapping; the
 * non-terminal branch is the single guard rejecting a misuse.
 *
 * @param status The terminal run status. Must satisfy [PipelineRunStatus.isTerminal];
 *   a non-terminal status is a caller bug and throws [IllegalArgumentException].
 * @param errorMessage The failure/interruption reason recorded on the run, or
 *   `null`. Only consulted for the [PipelineRunStatus.FAILED] branch.
 * @return The [TriggerRunOutcome] to attribute to the run's journal row.
 * @throws IllegalArgumentException if [status] is not terminal.
 */
fun triggerRunOutcomeForTerminal(status: PipelineRunStatus, errorMessage: String?): TriggerRunOutcome = when (status) {
    PipelineRunStatus.COMPLETED -> TriggerRunOutcome.Success
    PipelineRunStatus.FAILED ->
        if (errorMessage == ParkedRunResumer.APPROVAL_WINDOW_EXPIRED_MESSAGE) {
            TriggerRunOutcome.HitlTimeout
        } else {
            TriggerRunOutcome.Failure(errorMessage ?: DEFAULT_FAILURE_MESSAGE)
        }
    PipelineRunStatus.INTERRUPTED -> TriggerRunOutcome.CancelledBySystem
    PipelineRunStatus.CANCELLED -> TriggerRunOutcome.Cancelled
    PipelineRunStatus.QUEUED,
    PipelineRunStatus.RUNNING,
    PipelineRunStatus.WAITING_APPROVAL,
    PipelineRunStatus.WAITING_CLARIFICATION,
    -> throw IllegalArgumentException("triggerRunOutcomeForTerminal requires a terminal status, got $status")
}

/** Neutral failure text used when a run finished FAILED without a recorded reason. */
private const val DEFAULT_FAILURE_MESSAGE = "Run failed"
