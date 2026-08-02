package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.TriggerHitlEvent
import app.knotwork.android.domain.models.TriggerHitlResolution
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.ApprovalNotifier
import app.knotwork.android.domain.services.ClarificationNotifier
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Shared submission tail of the background-HITL decision use cases.
 *
 * [SubmitApprovalDecisionUseCase] and [SubmitClarificationAnswerUseCase]
 * differ only in how they record the user's response onto the parked
 * [PendingInteraction]; everything after that — notification teardown, the
 * lazy approval-window check, the first-writer-wins response write, the
 * checkpoint resume, and the failure settlement of unresumable parks — is
 * identical and lives here so the two cannot drift apart.
 */
class ParkedRunResumer @Inject constructor(
    private val pendingInteractionRepository: PendingInteractionRepository,
    private val pipelineRunRepository: PipelineRunRepository,
    private val settingsRepository: SettingsRepository,
    private val approvalNotifier: ApprovalNotifier,
    private val clarificationNotifier: ClarificationNotifier,
    private val resumePipelineRunUseCase: ResumePipelineRunUseCase,
    private val recordTriggerHitlEvent: RecordTriggerHitlEventUseCase,
) {

    /**
     * Records the user's response onto [pending] and resumes the parked run.
     *
     * Steps, in order:
     *  1. remove the pending notification — whatever the outcome below, the
     *     request is no longer actionable from the shade;
     *  2. lazily enforce the approval window: an expired park is settled as
     *     FAILED ("Approval window expired") right here instead of waiting
     *     for the maintenance pass;
     *  3. write the response via [recordResponse] (first-writer-wins — a
     *     racing duplicate submission turns into [PendingSubmissionOutcome.NothingPending]);
     *  4. resume the run from its checkpoint; parks whose graph changed or
     *     whose run record already settled are failed/cleaned so they cannot
     *     linger as zombies.
     *
     * @param pending The parked interaction being answered.
     * @param recordResponse Writes the response onto the record (guarded
     *   decision/answer write); returns `false` when already responded.
     * @return The submission outcome for UI mapping.
     */
    suspend fun submit(
        pending: PendingInteraction,
        recordResponse: suspend (runId: String) -> Boolean,
    ): PendingSubmissionOutcome {
        cancelNotification(pending)

        val windowHours = settingsRepository.backgroundApprovalWindowHours.first()
        if (System.currentTimeMillis() - pending.requestedAt > windowHours * MILLIS_PER_HOUR) {
            failPark(pending, APPROVAL_WINDOW_EXPIRED_MESSAGE)
            return PendingSubmissionOutcome.Expired
        }

        if (!recordResponse(pending.runId)) {
            return PendingSubmissionOutcome.NothingPending
        }

        return when (resumePipelineRunUseCase(pending.runId)) {
            ResumeOutcome.Resumed -> PendingSubmissionOutcome.Resumed
            ResumeOutcome.GraphChanged -> {
                failPark(pending, GRAPH_CHANGED_MESSAGE)
                PendingSubmissionOutcome.GraphChanged
            }
            ResumeOutcome.Expired -> {
                failPark(pending, APPROVAL_WINDOW_EXPIRED_MESSAGE)
                PendingSubmissionOutcome.Expired
            }
            ResumeOutcome.NotResumable -> {
                // Usually the run record already settled elsewhere (maintenance
                // expiry, a racing resume, a restart) and only the stale park is
                // left to drop. But "cannot resume" was also reachable with the
                // run still *unfinished* — and this branch then deleted the only
                // record that made it answerable while leaving it non-terminal,
                // stranding it in RUNNING behind a permanent "generating…"
                // (phase-40 finding F7). Whatever made it unresumable, a run
                // nobody can act on any more has to reach a terminal state:
                // settle it instead of abandoning it.
                val stillOpen = pipelineRunRepository.getRun(pending.runId)
                    ?.status in NON_TERMINAL_STATUSES
                if (stillOpen) {
                    failPark(pending, NOT_RESUMABLE_MESSAGE)
                } else {
                    // The gate ends here too: without this it would stay
                    // journalled as still waiting on a run that is long over,
                    // which is precisely the kind of silent state the HITL
                    // record exists to remove. The response was given but could
                    // never be applied, so it is ABANDONED rather than an
                    // approval or an answer that took effect.
                    recordTriggerHitlEvent(
                        pending.runId,
                        TriggerHitlEvent.Resolved(TriggerHitlResolution.ABANDONED),
                    )
                    pendingInteractionRepository.delete(pending.runId)
                }
                PendingSubmissionOutcome.NothingPending
            }
        }
    }

    /**
     * Settles an unrecoverable park: fails the run with [reason], deletes the
     * pending record, and removes its notification.
     *
     * The failure is stamped on the **root** of the run tree (the park may sit
     * on a sub-pipeline run): failing the root settles the whole stack and lets
     * retention cascade-delete the lingering WAITING_* descendants through the
     * self-referential `parentRunId` foreign key. For a top-level park the root
     * is the run itself, so the behaviour is unchanged. The pending record is
     * deleted by its own run id (the descendant that actually parked).
     *
     * Also used by the maintenance expiry pass, which shares the exact same
     * settlement semantics.
     *
     * @param pending The parked interaction to settle.
     * @param reason Human-readable failure reason for the run record.
     */
    suspend fun failPark(pending: PendingInteraction, reason: String) {
        val rootId = pipelineRunRepository.getRootRunId(pending.runId) ?: pending.runId
        // Settle the gate in the journal before the run record itself: the
        // window elapsing unanswered and the park being discarded under a
        // changed graph are two different stories, and the run's own outcome
        // (FAILED, mapped to HitlTimeout only for the expiry message) cannot
        // tell them apart on its own.
        recordTriggerHitlEvent(
            rootId,
            TriggerHitlEvent.Resolved(
                if (reason == APPROVAL_WINDOW_EXPIRED_MESSAGE) {
                    TriggerHitlResolution.TIMED_OUT
                } else {
                    TriggerHitlResolution.ABANDONED
                },
            ),
        )
        pipelineRunRepository.finishRun(rootId, PipelineRunStatus.FAILED, reason)
        pendingInteractionRepository.delete(pending.runId)
        cancelNotification(pending)
    }

    /**
     * Removes the notification matching the park's kind.
     *
     * @param pending The parked interaction whose notification to remove.
     */
    private fun cancelNotification(pending: PendingInteraction) {
        when (pending.kind) {
            PendingInteractionKind.APPROVAL -> approvalNotifier.cancelApprovalNotification(pending.sessionId)
            PendingInteractionKind.CLARIFICATION ->
                clarificationNotifier.cancelClarificationNotification(pending.sessionId)
        }
    }

    /** Shared settlement messages, public because the maintenance worker stamps them too. */
    companion object {
        /** Failure reason stamped on runs whose approval window elapsed unanswered. */
        const val APPROVAL_WINDOW_EXPIRED_MESSAGE: String = "Approval window expired"

        /** Failure reason stamped on parked runs whose pipeline graph changed while waiting. */
        const val GRAPH_CHANGED_MESSAGE: String =
            "Pipeline graph changed while waiting for the response. Restart the task instead."

        /**
         * Failure reason stamped on a park that could not be resumed while its
         * run was still open. Deliberately distinct from the expiry and
         * graph-change messages: those describe *why* the response could not be
         * applied, this one admits the run could not be continued at all.
         */
        const val NOT_RESUMABLE_MESSAGE: String =
            "The run could not be resumed and was stopped. Restart the task instead."

        /** Milliseconds in one hour, for the approval-window check. */
        private const val MILLIS_PER_HOUR: Long = 3_600_000L

        /**
         * Run statuses that still expect an executor. A park found in one of
         * these has not settled anywhere else, so dropping its pending record
         * without finishing it would strand the run.
         */
        private val NON_TERMINAL_STATUSES = setOf(
            PipelineRunStatus.RUNNING,
            PipelineRunStatus.QUEUED,
            PipelineRunStatus.INTERRUPTED,
            PipelineRunStatus.WAITING_APPROVAL,
            PipelineRunStatus.WAITING_CLARIFICATION,
        )
    }
}

/**
 * Typed outcome of a background-HITL response submission. The UI maps each
 * variant to its own user-facing message, so the variants carry no text.
 */
sealed class PendingSubmissionOutcome {
    /** The response settled the live in-process gate; the run continues in place. */
    data object LiveResumed : PendingSubmissionOutcome()

    /** The response was recorded and the parked run was re-enqueued from its checkpoint. */
    data object Resumed : PendingSubmissionOutcome()

    /** The approval window had already elapsed; the run was failed. */
    data object Expired : PendingSubmissionOutcome()

    /** The pipeline graph changed while the run was parked; the run was failed. */
    data object GraphChanged : PendingSubmissionOutcome()

    /** No pending request to respond to (already settled, or a duplicate submission). */
    data object NothingPending : PendingSubmissionOutcome()
}
