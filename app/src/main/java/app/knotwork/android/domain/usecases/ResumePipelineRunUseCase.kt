package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentTask
import app.knotwork.android.domain.models.PipelineRun
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Validates and launches the checkpoint resume of an interrupted pipeline run.
 *
 * The use case is the single entry point of the resume feature: it owns every
 * precondition that decides whether a [PipelineRunStatus.INTERRUPTED] run may
 * continue from its persisted trace, and on success hands the run back to the
 * [TaskQueueManager] as a resume-flagged [AgentTask]. The queue worker then
 * re-validates the graph hash (the user could edit the pipeline in the window
 * between this validation and the worker picking the task up), rebuilds the
 * `ResumeContext` from the persisted trace, and drives the engine in replay
 * mode.
 *
 * Preconditions, in evaluation order:
 *  1. the run exists and is INTERRUPTED (anything else → [ResumeOutcome.NotResumable]);
 *  2. the run record carries the original user prompt (legacy rows written
 *     before prompt persistence → [ResumeOutcome.NotResumable]);
 *  3. the interruption is younger than the `resumeMaxAgeHours` setting
 *     (older → [ResumeOutcome.Expired] — the recorded context grows stale);
 *  4. the run's pipeline still exists and its current content hash equals the
 *     hash captured when the run started (deleted or edited graph →
 *     [ResumeOutcome.GraphChanged]; only a full restart can help);
 *  5. the guarded INTERRUPTED → QUEUED transition applies (a concurrent
 *     discard/resume loses the race → [ResumeOutcome.NotResumable]).
 */
class ResumePipelineRunUseCase @Inject constructor(
    private val pipelineRunRepository: PipelineRunRepository,
    private val pipelineRepository: PipelineRepository,
    private val settingsRepository: SettingsRepository,
    private val taskQueueManager: TaskQueueManager,
) {

    /**
     * Attempts to resume the interrupted run [runId].
     *
     * @param runId Id of the persistent run record to resume.
     * @return The typed outcome — [ResumeOutcome.Resumed] when the run was
     *   re-enqueued, or the specific reason resume is unavailable.
     */
    suspend operator fun invoke(runId: String): ResumeOutcome {
        val run = pipelineRunRepository.getRun(runId)
        val userPrompt = run?.userPrompt
        val pipelineId = run?.pipelineId
        if (run == null || run.status != PipelineRunStatus.INTERRUPTED || userPrompt == null) {
            return ResumeOutcome.NotResumable
        }
        rejectionFor(run)?.let { return it }

        // The guarded transition is the concurrency gate: of two racing
        // resume taps (or a resume racing a discard) exactly one wins. The
        // pipeline-id null-check is for the compiler only — `rejectionFor`
        // already rejected runs without one as GraphChanged.
        if (pipelineId == null || !pipelineRunRepository.markResumed(runId)) {
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
     * Evaluates the age and graph-identity preconditions (items 3–4 of the
     * class contract) against the loaded run record.
     *
     * @param run The INTERRUPTED run candidate.
     * @return The rejection outcome, or `null` when the run may resume.
     */
    private suspend fun rejectionFor(run: PipelineRun): ResumeOutcome? {
        val maxAgeHours = settingsRepository.resumeMaxAgeHours.first()
        val interruptedAt = run.finishedAt ?: run.startedAt
        if (System.currentTimeMillis() - interruptedAt > maxAgeHours * MILLIS_PER_HOUR) {
            return ResumeOutcome.Expired
        }
        val recordedHash = run.graphContentHash ?: return ResumeOutcome.GraphChanged
        val graph = run.pipelineId?.let { pipelineRepository.getPipelineById(it) }
            ?: return ResumeOutcome.GraphChanged
        return if (graph.contentHash() != recordedHash) ResumeOutcome.GraphChanged else null
    }

    private companion object {
        /** Milliseconds in one hour, for the resume-window age check. */
        const val MILLIS_PER_HOUR: Long = 3_600_000L
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

    /** The interruption is older than the `resumeMaxAgeHours` window. */
    data object Expired : ResumeOutcome()

    /**
     * The run is not in a resumable state: missing, not INTERRUPTED (e.g. a
     * concurrent resume or discard won the race), or recorded without the
     * original user prompt.
     */
    data object NotResumable : ResumeOutcome()
}
