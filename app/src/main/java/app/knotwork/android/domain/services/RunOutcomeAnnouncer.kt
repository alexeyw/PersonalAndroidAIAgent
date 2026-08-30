package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.RunTerminationReason

/**
 * Writes a run's terminal outcome into the chat it ran in, as a message that
 * stays there.
 *
 * Before this existed, a failed or stopped run reached the user only as
 * `ChatHomeUiState.Error` — screen state held by a ViewModel. It died with the
 * ViewModel, so a run that ended while the app was in the background left the
 * thread showing the user's message and no reply at all, with nothing to say
 * why. The outcome has to be written by whatever settled the run rather than by
 * whoever happened to be watching it, which is why this is called from the task
 * queue next to `PipelineRunRepository.finishRun` and not from the chat screen.
 *
 * Implementations live in the presentation layer because the sentence comes
 * from `RunTerminationCopyMapper` — the single owner of the vocabulary a stopped
 * run is described in. Resolving it anywhere else would fork that vocabulary,
 * which is the failure `TriggerRunOutcomeMapper` documents having already paid
 * for once.
 */
interface RunOutcomeAnnouncer {

    /**
     * Records the outcome of a finished run in its session's thread.
     *
     * A successful run is not announced — its answer is the message. Only
     * outcomes that would otherwise leave a question unanswered are.
     *
     * @param sessionId Chat session the run belonged to.
     * @param status Terminal status the run settled at. Non-terminal and
     *   successful statuses are ignored by implementations.
     * @param reason Typed cause when the engine classified the stop; `null` for
     *   an ordinary failure or a cancellation.
     * @param diagnostic The run's persisted `errorMessage` — terse and written
     *   for a log reader. Used only when [reason] is `null` and there is
     *   otherwise nothing concrete to say.
     */
    suspend fun announce(
        sessionId: String,
        status: PipelineRunStatus,
        reason: RunTerminationReason?,
        diagnostic: String?,
    )
}
