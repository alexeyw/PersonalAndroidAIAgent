package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.repositories.ClarificationRepository
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import javax.inject.Inject

/**
 * Single entry point for the user's answer to a clarification gate,
 * regardless of which waiting phase the run is in.
 *
 * Routing, in order:
 *  1. **Live phase** — when [requestId] is known and an in-process
 *     clarification deferred still listens, the answer settles it via
 *     [ClarificationRepository.submitClarification] exactly as the pre-park
 *     flow always did.
 *  2. **Persistent phase** — the run is parked on a pending-interaction
 *     record: record the answer onto it (one-shot, first-writer-wins) and
 *     resume the run from its checkpoint via [ParkedRunResumer]. The resumed
 *     CLARIFICATION node consumes the recorded answer without re-running
 *     question inference.
 */
class SubmitClarificationAnswerUseCase @Inject constructor(
    private val clarificationRepository: ClarificationRepository,
    private val pendingInteractionRepository: PendingInteractionRepository,
    private val parkedRunResumer: ParkedRunResumer,
) {

    /**
     * Submits the user's answer for the pending clarification of [sessionId].
     *
     * @param sessionId Id of the session whose clarification is being answered.
     * @param requestId Id of the live clarification request when the UI still
     *   holds one; `null` (or a request that already timed out) falls through
     *   to the session's parked record.
     * @param answer The user's answer text.
     * @return The typed outcome for UI mapping.
     */
    suspend operator fun invoke(sessionId: String, requestId: String?, answer: String): PendingSubmissionOutcome {
        if (requestId != null && clarificationRepository.submitClarification(requestId, answer)) {
            return PendingSubmissionOutcome.LiveResumed
        }

        val pending = pendingInteractionRepository.getForSession(sessionId)
            ?.takeIf { it.kind == PendingInteractionKind.CLARIFICATION }
            ?: return PendingSubmissionOutcome.NothingPending

        return parkedRunResumer.submit(pending) { parkedRunId ->
            pendingInteractionRepository.recordAnswer(parkedRunId, answer)
        }
    }
}
