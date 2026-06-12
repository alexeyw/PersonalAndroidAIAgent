package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentTask
import app.knotwork.android.domain.models.PipelineRun
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Validates and launches the checkpoint resume of a non-live pipeline run.
 *
 * The use case is the single entry point of the resume feature: it owns every
 * precondition that decides whether a run may continue from its persisted
 * trace, and on success hands the run back to the [TaskQueueManager] as a
 * resume-flagged [AgentTask]. The queue worker then re-validates the graph
 * hash (the user could edit the pipeline in the window between this
 * validation and the worker picking the task up), rebuilds the
 * `ResumeContext` from the persisted trace, and drives the engine in replay
 * mode.
 *
 * Two starting states are resumable:
 *  - [PipelineRunStatus.INTERRUPTED] — the owning process died mid-run; the
 *    user resumes explicitly from the status card. Bounded by the
 *    `resumeMaxAgeHours` setting counted from the interruption.
 *  - [PipelineRunStatus.WAITING_APPROVAL] / [PipelineRunStatus.WAITING_CLARIFICATION]
 *    with a parked pending-interaction record — the run waits for a
 *    background HITL response; the decision use cases resume it after
 *    recording the user's answer. Bounded by the
 *    `backgroundApprovalWindowHours` setting counted from the park.
 *
 * Preconditions, in evaluation order:
 *  1. the run exists, is in a resumable status, and carries the original
 *     user prompt (legacy rows → [ResumeOutcome.NotResumable]); a WAITING_*
 *     run must additionally hold a pending-interaction record — without one
 *     the wait is still live in-process and resume does not apply;
 *  2. the run is younger than its window (older → [ResumeOutcome.Expired]);
 *  3. the run's pipeline still exists and its current content hash equals the
 *     hash captured when the run started (deleted or edited graph →
 *     [ResumeOutcome.GraphChanged]; only a full restart can help);
 *  4. the guarded status → QUEUED transition applies (a concurrent
 *     discard/resume loses the race → [ResumeOutcome.NotResumable]).
 */
class ResumePipelineRunUseCase @Inject constructor(
    private val pipelineRunRepository: PipelineRunRepository,
    private val pipelineRepository: PipelineRepository,
    private val settingsRepository: SettingsRepository,
    private val pendingInteractionRepository: PendingInteractionRepository,
    private val taskQueueManager: TaskQueueManager,
) {

    /**
     * Attempts to resume the run [runId].
     *
     * @param runId Id of the persistent run record to resume.
     * @return The typed outcome — [ResumeOutcome.Resumed] when the run was
     *   re-enqueued, or the specific reason resume is unavailable.
     */
    suspend operator fun invoke(runId: String): ResumeOutcome {
        val run = pipelineRunRepository.getRun(runId)
        val userPrompt = run?.userPrompt
        val pipelineId = run?.pipelineId
        if (run == null || run.status !in RESUMABLE_STATUSES || userPrompt == null) {
            return ResumeOutcome.NotResumable
        }
        rejectionFor(run)?.let { return it }

        // The guarded transition is the concurrency gate: of two racing
        // resume taps (or a resume racing a discard) exactly one wins. The
        // pipeline-id null-check is for the compiler only — `rejectionFor`
        // already rejected runs without one as GraphChanged.
        if (pipelineId == null || !pipelineRunRepository.markResumed(runId, run.status)) {
            return ResumeOutcome.NotResumable
        }

        taskQueueManager.enqueueTask(
            AgentTask(
                id = runId,
                sessionId = run.sessionId,
                prompt = userPrompt,
                pipelineId = pipelineId,
                origin = run.origin,
                isResume = true,
            ),
        )
        return ResumeOutcome.Resumed
    }

    /**
     * Evaluates the age/window and graph-identity preconditions (items 2–3
     * of the class contract) against the loaded run record.
     *
     * @param run The resume candidate.
     * @return The rejection outcome, or `null` when the run may resume.
     */
    private suspend fun rejectionFor(run: PipelineRun): ResumeOutcome? {
        windowRejectionFor(run)?.let { return it }
        val recordedHash = run.graphContentHash ?: return ResumeOutcome.GraphChanged
        val graph = run.pipelineId?.let { pipelineRepository.getPipelineById(it) }
            ?: return ResumeOutcome.GraphChanged
        return if (graph.contentHash() != recordedHash) ResumeOutcome.GraphChanged else null
    }

    /**
     * Applies the time-window precondition matching the run's status: the
     * interruption age window for INTERRUPTED runs, the background-approval
     * window (counted from the park) for persistently waiting runs.
     *
     * @param run The resume candidate.
     * @return The rejection outcome, or `null` when the run is within its window.
     */
    private suspend fun windowRejectionFor(run: PipelineRun): ResumeOutcome? {
        if (run.status == PipelineRunStatus.INTERRUPTED) {
            val maxAgeHours = settingsRepository.resumeMaxAgeHours.first()
            val interruptedAt = run.finishedAt ?: run.startedAt
            if (System.currentTimeMillis() - interruptedAt > maxAgeHours * MILLIS_PER_HOUR) {
                return ResumeOutcome.Expired
            }
            return null
        }
        // WAITING_* park: without a pending record the wait is still live in
        // this process — there is no parked state to resume.
        val pending = pendingInteractionRepository.getForRun(run.id) ?: return ResumeOutcome.NotResumable
        val windowHours = settingsRepository.backgroundApprovalWindowHours.first()
        return if (System.currentTimeMillis() - pending.requestedAt > windowHours * MILLIS_PER_HOUR) {
            ResumeOutcome.Expired
        } else {
            null
        }
    }

    private companion object {
        /** Milliseconds in one hour, for the resume-window age check. */
        const val MILLIS_PER_HOUR: Long = 3_600_000L

        /** Statuses a run may be resumed from. */
        val RESUMABLE_STATUSES = setOf(
            PipelineRunStatus.INTERRUPTED,
            PipelineRunStatus.WAITING_APPROVAL,
            PipelineRunStatus.WAITING_CLARIFICATION,
        )
    }
}

/**
 * Typed outcome of a [ResumePipelineRunUseCase] attempt. The UI maps each
 * variant to its own user-facing message, so the variants carry no text.
 */
sealed class ResumeOutcome {
    /** The run was re-enqueued; the live state flow of its session will pick it up. */
    data object Resumed : ResumeOutcome()

    /**
     * The pipeline graph was deleted or edited after the run started — the
     * checkpoint is invalid and only a full restart of the task can help.
     */
    data object GraphChanged : ResumeOutcome()

    /**
     * The run is outside its time window: an interruption older than the
     * `resumeMaxAgeHours` setting, or a parked HITL request older than the
     * `backgroundApprovalWindowHours` setting.
     */
    data object Expired : ResumeOutcome()

    /**
     * The run is not in a resumable state: missing, in a non-resumable
     * status (e.g. a concurrent resume or discard won the race), recorded
     * without the original user prompt, or persistently waiting without a
     * parked pending-interaction record.
     */
    data object NotResumable : ResumeOutcome()
}
