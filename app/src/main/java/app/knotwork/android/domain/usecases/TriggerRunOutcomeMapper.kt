package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.RunTerminationKind
import app.knotwork.android.domain.models.RunTerminationReason
import app.knotwork.android.domain.models.TriggerRunOutcome

/**
 * Maps the terminal state of a background [PipelineRun][app.knotwork.android.domain.models.PipelineRun]
 * onto the [TriggerRunOutcome] recorded back onto the originating trigger-evaluation
 * journal row.
 *
 * This is the single translation from the run runtime's vocabulary
 * ([PipelineRunStatus] plus a typed [RunTerminationReason]) into the journal's
 * diagnostic vocabulary, so every consumer settling a run attributes the same
 * outcome for the same terminal state. It is a pure function — no I/O, no clock —
 * and so exhaustively unit-testable.
 *
 * **The classification reads the typed reason, not the error text.** Until the
 * reason existed this function recovered one distinction — an expired approval
 * window — by comparing the recorded message against a constant by string
 * equality. That worked only for as long as nobody edited the copy, and editing
 * that copy is planned work. A vocabulary is now a vocabulary; the message is
 * demoted to what it always was, a rendering for a person to read.
 *
 * The mapping deliberately keeps **platform** outcomes distinct from **product**,
 * **deliberate** and **protective** ones, which is the whole point of the
 * background-reliability journal:
 * - [PipelineRunStatus.COMPLETED] → [TriggerRunOutcome.Success].
 * - [PipelineRunStatus.FAILED] stopped by an autonomous-run ceiling
 *   ([RunTerminationKind.STEP_CEILING] / [RunTerminationKind.TOKEN_CEILING]) →
 *   [TriggerRunOutcome.StoppedByCeiling]. A safety limit doing its job is not a
 *   defect of the trigger, and reporting it as one would teach the user to
 *   distrust a health indicator that is telling the truth.
 * - [PipelineRunStatus.FAILED] carrying [RunTerminationKind.HITL_WINDOW_EXPIRED] →
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
 * @param errorMessage The human-readable failure/interruption text recorded on the
 *   run, or `null`. Only used to fill [TriggerRunOutcome.Failure].
 * @param reason The typed cause when the app itself decided to end the run, or
 *   `null` for a completion or an ordinary node failure. Drives the classification.
 * @return The [TriggerRunOutcome] to attribute to the run's journal row.
 * @throws IllegalArgumentException if [status] is not terminal.
 */
fun triggerRunOutcomeForTerminal(
    status: PipelineRunStatus,
    errorMessage: String?,
    reason: RunTerminationReason?,
): TriggerRunOutcome = when (status) {
    PipelineRunStatus.COMPLETED -> TriggerRunOutcome.Success
    PipelineRunStatus.FAILED -> when (reason?.kind) {
        RunTerminationKind.STEP_CEILING, RunTerminationKind.TOKEN_CEILING ->
            TriggerRunOutcome.StoppedByCeiling
        RunTerminationKind.HITL_WINDOW_EXPIRED -> TriggerRunOutcome.HitlTimeout
        // A loop and a stall are both the automation not working. Unlike a
        // ceiling — which is a number the user chose, doing what they chose it
        // for — these are exactly the condition a health badge exists to show,
        // so they redden it.
        RunTerminationKind.NO_PROGRESS,
        RunTerminationKind.RUN_STALLED,
        RunTerminationKind.GRAPH_CHANGED,
        RunTerminationKind.PROCESS_DIED,
        RunTerminationKind.DISCARDED_BY_USER,
        RunTerminationKind.NOT_RESUMABLE,
        null,
        -> TriggerRunOutcome.Failure(errorMessage ?: DEFAULT_FAILURE_MESSAGE)
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
