package app.knotwork.android.domain.models

/**
 * Maps a run's terminal status onto the external-automation contract's coarser
 * outcome vocabulary.
 *
 * **Coarser on purpose.** The contract publishes three settled statuses where the
 * app has four terminal ones: `Completed` for a successful run and `Failed` for
 * everything else — a defect, a user cancellation, or a platform kill. The
 * distinction between those three matters enormously *inside* the app (the trigger
 * journal keeps them apart precisely so a platform reliability problem is not read
 * as a product defect), and not at all to a caller in another process, which can
 * act on exactly one bit: did the thing I asked for happen. Publishing the finer
 * vocabulary would freeze four internal names into a third-party contract and
 * invite callers to branch on distinctions they cannot do anything about.
 *
 * The terminal statuses are matched exhaustively (no `else`), so a future terminal
 * status is a compile error here rather than a silent mis-mapping; the non-terminal
 * branch is the single guard rejecting a misuse.
 *
 * @param status The terminal run status. Must satisfy [PipelineRunStatus.isTerminal];
 *   a non-terminal status is a caller bug and throws [IllegalArgumentException].
 * @return The status to settle the request's journal row and callback with.
 * @throws IllegalArgumentException if [status] is not terminal.
 */
fun externalAutomationStatusForTerminal(status: PipelineRunStatus): ExternalAutomationStatus = when (status) {
    PipelineRunStatus.COMPLETED -> ExternalAutomationStatus.Completed
    PipelineRunStatus.FAILED,
    PipelineRunStatus.INTERRUPTED,
    PipelineRunStatus.CANCELLED,
    -> ExternalAutomationStatus.Failed
    PipelineRunStatus.QUEUED,
    PipelineRunStatus.RUNNING,
    PipelineRunStatus.WAITING_APPROVAL,
    PipelineRunStatus.WAITING_CLARIFICATION,
    -> throw IllegalArgumentException(
        "externalAutomationStatusForTerminal requires a terminal status, got $status",
    )
}
