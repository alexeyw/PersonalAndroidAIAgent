package app.knotwork.android.presentation.run

import android.content.Context
import app.knotwork.android.R
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.models.RunTerminationReason
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.services.RunOutcomeAnnouncer
import app.knotwork.android.presentation.ui.common.RunTerminationCopyMapper
import app.knotwork.android.presentation.ui.common.resolve
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Presentation-layer [RunOutcomeAnnouncer]: turns a terminal run outcome into a
 * `SYSTEM` chat message.
 *
 * It lives here, rather than beside the queue that calls it, for one reason —
 * the sentence comes from [RunTerminationCopyMapper], the single owner of the
 * words a stopped run is described in. The chat tile, the composer banner and
 * both notifications already resolve their text there; a run that ended in the
 * background must say the same thing, not a second version of it.
 *
 * @property context Application context, used to resolve the copy.
 * @property chatRepository Where the line is persisted.
 */
class RunOutcomeAnnouncerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
) : RunOutcomeAnnouncer {

    override suspend fun announce(
        sessionId: String,
        status: PipelineRunStatus,
        reason: RunTerminationReason?,
        diagnostic: String?,
    ) {
        if (sessionId.isBlank()) return
        val text = sentenceFor(status, reason, diagnostic) ?: return
        // Best-effort: a run that already failed must not also take down the
        // queue worker because its epitaph could not be written.
        try {
            chatRepository.saveMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = Role.SYSTEM,
                    content = text,
                    timestamp = System.currentTimeMillis(),
                    // `isFinal = true` on purpose. The display query filters
                    // `isFinal = 0` out, and a line the user cannot see is
                    // exactly the state this class exists to end — it is the one
                    // SYSTEM message meant to be read rather than journalled.
                    isFinal = true,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not record the outcome of a run in session %s", sessionId)
        }
    }

    /**
     * Resolves the line for a terminal [status], or `null` when the outcome
     * needs no line.
     *
     * A completed run is silent — its answer is the message. So are the
     * non-terminal statuses, which reach here only if a caller is confused.
     *
     * @param status Terminal status the run settled at.
     * @param reason Typed cause, when the engine classified the stop.
     * @param diagnostic The run's persisted `errorMessage`.
     * @return The sentence to persist, or `null` to stay silent.
     */
    private fun sentenceFor(status: PipelineRunStatus, reason: RunTerminationReason?, diagnostic: String?): String? =
        when (status) {
            // A classified stop says exactly what the chat tile and the background
            // notification say about the same event.
            PipelineRunStatus.FAILED -> when {
                reason != null -> context.resolve(RunTerminationCopyMapper.terminationCopy(reason).body)
                !diagnostic.isNullOrBlank() ->
                    context.getString(R.string.run_outcome_chat_failed_with_diagnostic, diagnostic)
                else -> context.getString(R.string.run_outcome_chat_failed)
            }
            PipelineRunStatus.CANCELLED -> context.getString(R.string.run_outcome_chat_cancelled)
            PipelineRunStatus.INTERRUPTED -> context.getString(R.string.run_outcome_chat_interrupted)
            PipelineRunStatus.COMPLETED,
            PipelineRunStatus.QUEUED,
            PipelineRunStatus.RUNNING,
            PipelineRunStatus.WAITING_APPROVAL,
            PipelineRunStatus.WAITING_CLARIFICATION,
            -> null
        }
}
