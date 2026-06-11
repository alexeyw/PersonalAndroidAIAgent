package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.PipelineRun
import app.knotwork.android.domain.models.PipelineRunStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for the persistent pipeline-run records.
 *
 * Backed by the encrypted `pipeline_runs` table, this is the durability layer
 * that lets the application know a run existed — and where it stopped — after
 * the process hosting the in-memory orchestrator state dies. Writers follow a
 * strict split: the task queue owns creation, the RUNNING transition, and all
 * terminal transitions; the execution engine owns per-node progress
 * ([updateCurrentNode]) and the suspension statuses
 * ([PipelineRunStatus.WAITING_APPROVAL] / [PipelineRunStatus.WAITING_CLARIFICATION]).
 *
 * All status mutations are guarded: once a run reaches a terminal status
 * (see [PipelineRunStatus.isTerminal]) further updates are silently ignored,
 * so racing writers (e.g. an engine status write racing the queue's
 * `finally`-side cancellation write) can never resurrect a finished run.
 */
interface PipelineRunRepository {

    /**
     * Persists a freshly enqueued run in [PipelineRunStatus.QUEUED] status.
     *
     * @param run The run record to insert. Its [PipelineRun.pipelineId] and
     *   [PipelineRun.graphContentHash] are typically `null` at this point —
     *   both are resolved when the run starts (see [markRunning]).
     */
    suspend fun createRun(run: PipelineRun)

    /**
     * Transitions the run to [PipelineRunStatus.RUNNING], recording the
     * resolved pipeline and the content hash of the graph about to execute.
     * No-op when the run is already terminal.
     *
     * @param runId Id of the run to update.
     * @param pipelineId Id of the pipeline resolved for this run.
     * @param graphContentHash Content hash of the resolved graph (see
     *   `PipelineGraph.contentHash`), captured for checkpoint invalidation.
     */
    suspend fun markRunning(runId: String, pipelineId: String, graphContentHash: String)

    /**
     * Updates the run's lifecycle status. No-op when the run is already
     * terminal. Use [finishRun] for terminal transitions — this method is for
     * the active-side statuses (RUNNING and the two WAITING_* suspensions).
     *
     * @param runId Id of the run to update.
     * @param status The new non-terminal status.
     */
    suspend fun updateStatus(runId: String, status: PipelineRunStatus)

    /**
     * Records the node the engine is about to execute, so an interrupted run
     * can report where it stopped. No-op when the run is already terminal.
     *
     * @param runId Id of the run to update.
     * @param nodeId Id of the graph node that just started executing.
     */
    suspend fun updateCurrentNode(runId: String, nodeId: String)

    /**
     * Transitions the run to a terminal [status], stamping `finishedAt` and
     * the optional [errorMessage]. Idempotent: when the run is already
     * terminal the call is silently ignored, so the queue's unconditional
     * `finally`-side write never overwrites an earlier COMPLETED/FAILED.
     *
     * @param runId Id of the run to finish.
     * @param status The terminal status to write. Must satisfy
     *   [PipelineRunStatus.isTerminal].
     * @param errorMessage Failure or interruption reason; `null` for
     *   successful or cancelled runs.
     */
    suspend fun finishRun(runId: String, status: PipelineRunStatus, errorMessage: String? = null)

    /**
     * Returns the most recently started non-terminal run of [sessionId], or
     * `null` when every run of the session already finished. This is the
     * entry point of the chat reattach protocol.
     *
     * @param sessionId Id of the chat session to query.
     * @return The active run, or `null` when none is active.
     */
    suspend fun getActiveRunForSession(sessionId: String): PipelineRun?

    /**
     * Observes all runs of [sessionId], most recently started first.
     *
     * @param sessionId Id of the chat session to observe.
     * @return A cold flow re-emitting the full run list on every change.
     */
    fun observeRunsForSession(sessionId: String): Flow<List<PipelineRun>>

    /**
     * Returns runs stranded in [PipelineRunStatus.QUEUED] or
     * [PipelineRunStatus.RUNNING]. Called at application start, where any
     * such record is by definition orphaned — an in-process run cannot
     * predate the process. WAITING_* runs are deliberately excluded: their
     * fate is decided by the background-HITL flow, not the orphan sweep.
     *
     * @return Runs whose owning process died mid-execution.
     */
    suspend fun getOrphanedRunning(): List<PipelineRun>

    /**
     * Deletes every run record of [sessionId]. Called when the user deletes
     * the chat session itself; the table has no foreign key to
     * `chat_sessions` (a run may be created before its session row exists),
     * so cleanup is explicit.
     *
     * @param sessionId Id of the deleted chat session.
     */
    suspend fun deleteRunsForSession(sessionId: String)
}
