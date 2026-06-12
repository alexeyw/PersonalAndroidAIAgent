package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.PendingDecision
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import javax.inject.Inject

/**
 * Single entry point for the user's approve / deny decision on a HITL tool
 * gate, regardless of which waiting phase the run is in and which surface
 * the decision comes from (in-chat card, notification action — including
 * after process death).
 *
 * Routing, in order:
 *  1. **Live phase** — the session has an in-process approval deferred:
 *     complete it via [TaskQueueManager.resumeWithApproval] exactly as the
 *     pre-park flow always did.
 *  2. **Persistent phase** — the run is parked on a pending-interaction
 *     record: record the decision onto it (one-shot, first-writer-wins) and
 *     resume the run from its checkpoint via [ParkedRunResumer]. The resumed
 *     TOOL node consumes the decision under its TOCTOU argument guard. A
 *     denial resumes too — the run continues through the standard
 *     "Execution denied by user" observation path rather than failing.
 *
 * The decision is looked up by [runId] when the caller knows it (persistent
 * notification actions address the run directly) and falls back to the
 * session's parked record (in-chat card after process death).
 */
class SubmitApprovalDecisionUseCase @Inject constructor(
    private val taskQueueManager: TaskQueueManager,
    private val pendingInteractionRepository: PendingInteractionRepository,
    private val parkedRunResumer: ParkedRunResumer,
) {

    /**
     * Submits the user's decision for the pending approval of [sessionId].
     *
     * @param sessionId Id of the session whose approval gate is being answered.
     * @param isApproved `true` to approve the staged tool call, `false` to deny it.
     * @param runId Id of the parked run when the caller knows it (notification
     *   actions); `null` falls back to the session's parked record.
     * @return The typed outcome for UI mapping.
     */
    suspend operator fun invoke(
        sessionId: String,
        isApproved: Boolean,
        runId: String? = null,
    ): PendingSubmissionOutcome {
        if (taskQueueManager.pendingApproval(sessionId) != null) {
            taskQueueManager.resumeWithApproval(sessionId, isApproved)
            return PendingSubmissionOutcome.LiveResumed
        }

        val pending = (
            runId?.let { pendingInteractionRepository.getForRun(it) }
                ?: pendingInteractionRepository.getForSession(sessionId)
            )
            ?.takeIf { it.kind == PendingInteractionKind.APPROVAL }
            ?: return PendingSubmissionOutcome.NothingPending

        val decision = if (isApproved) PendingDecision.APPROVED else PendingDecision.DENIED
        return parkedRunResumer.submit(pending) { parkedRunId ->
            pendingInteractionRepository.recordDecision(parkedRunId, decision)
        }
    }
}
