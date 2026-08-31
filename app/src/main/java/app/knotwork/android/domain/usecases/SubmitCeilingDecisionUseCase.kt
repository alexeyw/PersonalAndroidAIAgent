package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PendingDecision
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.RunCeilingAxis
import app.knotwork.android.domain.models.RunTerminationReason
import app.knotwork.android.domain.models.TriggerHitlResolution
import app.knotwork.android.domain.models.ceilingBreach
import app.knotwork.android.domain.models.diagnostic
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import javax.inject.Inject

/**
 * Single entry point for the user's continue / stop decision on a run that has
 * spent one of its ceilings.
 *
 * The third of the parked-run submission use cases, and the only one whose
 * "yes" changes what the run is allowed to do rather than what it is about to
 * do. Continuing grants the tree **one more portion of the axis that bound** —
 * the same allowance again — and resumes it from its checkpoint. It does not
 * lift the limit: the next crossing asks again, and a run waved through ten
 * times is a run the user said yes to ten times. Stopping settles the run with
 * the ceiling as its recorded cause, which is exactly what the run did before
 * it learned to ask.
 *
 * There is no live in-process phase to route around, unlike
 * [SubmitApprovalDecisionUseCase]: a ceiling pause is durable from the moment
 * it is raised, so every answer — the in-chat card, the notification tap after
 * process death — arrives at the same parked record.
 *
 * **Ordering is load-bearing.** The grant is written *before* the resume is
 * enqueued and, on the continue path, only by the coroutine that won the
 * first-writer-wins guard on the record. Written after the resume, the engine
 * could rebuild its ledger from a row that has not been raised yet, breach on
 * its first node and re-ask the question just answered; written without the
 * guard, two racing taps would buy two portions for one answer.
 */
class SubmitCeilingDecisionUseCase @Inject constructor(
    private val pendingInteractionRepository: PendingInteractionRepository,
    private val pipelineRunRepository: PipelineRunRepository,
    private val parkedRunResumer: ParkedRunResumer,
) {

    /**
     * Submits the user's decision for the ceiling pause of [sessionId].
     *
     * @param sessionId Id of the session whose paused run is being answered.
     * @param shouldContinue `true` to grant one more portion and resume the run,
     *   `false` to stop it at the ceiling.
     * @param runId Id of the parked run when the caller knows it (notification
     *   taps address the run directly); `null` falls back to the session's
     *   parked record.
     * @return The typed outcome for UI mapping.
     */
    suspend operator fun invoke(
        sessionId: String,
        shouldContinue: Boolean,
        runId: String? = null,
    ): PendingSubmissionOutcome {
        val pending = (
            runId?.let { pendingInteractionRepository.getForRun(it) }
                ?: pendingInteractionRepository.getForSession(sessionId)
            )
            ?.takeIf { it.kind == PendingInteractionKind.CEILING }
            ?: return PendingSubmissionOutcome.NothingPending

        return if (shouldContinue) {
            grantAndResume(pending)
        } else {
            stop(pending)
        }
    }

    /**
     * Continue path: elects one winner among racing submissions, grants that
     * winner's portion to the tree root, and resumes the run.
     *
     * The grant rides inside the guarded write rather than beside it. The
     * record's `decision IS NULL` clause is the only mutual exclusion available
     * across processes — a notification tap and an in-chat tap can genuinely
     * land at once — and doing the increment outside it would let both through.
     *
     * The root, not the parked run: a pause raised inside a sub-pipeline is
     * recorded where it happened, but the counters live with the spend, on the
     * record at the top of the tree.
     *
     * A record that cannot name its axis is refused **before** anything is
     * written. Resuming without the grant would spend the user's answer on a run
     * that breaches again on its first node, and writing the decision first and
     * discovering the problem after would leave a record that reads as already
     * answered — permanently unanswerable. Nothing produces such a record today;
     * the guard is what keeps a degraded read from becoming a stuck run.
     *
     * @param pending The parked ceiling pause being answered.
     * @return The submission outcome.
     */
    private suspend fun grantAndResume(pending: PendingInteraction): PendingSubmissionOutcome {
        val axis = pending.ceilingBreach()?.axis ?: return PendingSubmissionOutcome.NothingPending
        val rootId = pipelineRunRepository.getRootRunId(pending.runId) ?: pending.runId
        return parkedRunResumer.submit(pending) { parkedRunId ->
            pendingInteractionRepository.recordDecision(parkedRunId, PendingDecision.APPROVED).also { won ->
                if (won) pipelineRunRepository.extendCeiling(rootId, axis)
            }
        }
    }

    /**
     * Stop path: settles the run with the ceiling as its recorded cause.
     *
     * Reuses [ParkedRunResumer.failPark] — the same settlement the expiry pass
     * performs — with two deliberate substitutions. The typed cause is the
     * ceiling rather than a HITL timeout, so the trigger health badge still
     * reads a working safety limit as a limit rather than a failure; and the
     * journalled resolution is [TriggerHitlResolution.DENIED], because the user
     * answered, and recording a given answer as abandonment would misreport who
     * was present.
     *
     * The `errorMessage` stamped on the run is the diagnostic, not prose, on the
     * same terms as every other protective stop: the sentence a person reads is
     * resolved from the typed cause in the presentation layer.
     *
     * @param pending The parked ceiling pause being answered.
     * @return The submission outcome. Stopping is never a resume, so it reports
     *   [PendingSubmissionOutcome.NothingPending] — there is nothing left
     *   pending once the run is settled.
     */
    private suspend fun stop(pending: PendingInteraction): PendingSubmissionOutcome {
        val reason = pending.terminationReason()
        parkedRunResumer.failPark(
            pending = pending,
            reason = reason.diagnostic(),
            terminationReason = reason,
            resolution = TriggerHitlResolution.DENIED,
        )
        return PendingSubmissionOutcome.NothingPending
    }

    /**
     * Rebuilds the typed stop cause from the parked record.
     *
     * The numbers come off the record rather than out of settings, because they
     * describe what happened to *this* run: it stopped at the limit in force
     * when it stopped, which is not necessarily the limit configured now, and is
     * definitely not the base limit when the user had already granted portions.
     *
     * A record missing either half — written before the columns existed, or
     * degraded — falls back to [RunTerminationReason.NotResumable]. That is the
     * honest reading: without the axis there is nothing to say the run was
     * stopped by a ceiling, and claiming one with invented numbers would put a
     * false `15/15` into the run record.
     *
     * @return The cause to settle the run with.
     */
    private fun PendingInteraction.terminationReason(): RunTerminationReason {
        val breach = ceilingBreach() ?: return RunTerminationReason.NotResumable
        return when (breach.axis) {
            RunCeilingAxis.STEPS -> RunTerminationReason.StepCeiling(limit = breach.limit, spent = breach.spent)
            RunCeilingAxis.TOKENS -> RunTerminationReason.TokenCeiling(limit = breach.limit, spent = breach.spent)
            // Unmeasured in this release, so nothing can have parked on it. If
            // that changes, this must gain a reason rather than fall back to a
            // stop that reads as "could not be resumed".
            RunCeilingAxis.MONEY -> RunTerminationReason.NotResumable
        }
    }
}
