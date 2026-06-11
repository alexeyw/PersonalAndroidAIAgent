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
 * **Best-effort contract.** Run records are an observability layer, never a
 * correctness dependency of the execution they describe. Implementations must
 * absorb storage and data-corruption failures: writes log and return, reads
 * log and degrade to `null` / empty results. The only exceptions allowed to
 * escape are `CancellationException` (cooperative cancellation must survive)
 * and [IllegalArgumentException] for caller contract violations (see
 * [finishRun]). Callers therefore never need their own guards.
 *
 * All status mutations are guarded: once a run reaches a terminal status
 * (see [PipelineRunStatus.isTerminal]) further updates are silently ignored,
 * so racing writers (e.g. an engine status write racing the queue's
 * `finally`-side cancellation write) can never resurrect a finished run.
 */
interface PipelineRunRepository {

    /**
     * Persists a freshly enqueued run in [PipelineRunStatus.QUEUED] status and
     * registers the run as **owned by the current process** (see
     * [getOrphanedRuns]). Inserting an id that already has a row is a silent
     * no-op — the existing record (whatever its status) is never overwritten,
     * so racing creators and re-deliveries cannot resurrect a settled run.
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
     *   [PipelineRunStatus.isTerminal] — a non-terminal status is a caller
     *   bug and throws [IllegalArgumentException] (not absorbed).
     * @param errorMessage Failure or interruption reason; `null` for
     *   successful or cancelled runs.
     */
    suspend fun finishRun(runId: String, status: PipelineRunStatus, errorMessage: String? = null)

    /**
     * Returns the run with [runId], or `null` when no such run exists (or the
     * store is unreadable — best-effort contract). Checkpoint resume uses it
     * to load and validate the one specific run being resumed.
     *
     * @param runId Id of the run to load.
     * @return The run record, or `null` when not found.
     */
    suspend fun getRun(runId: String): PipelineRun?

    /**
     * Flips an interrupted run back to [PipelineRunStatus.QUEUED] for
     * checkpoint resume, clearing the `finishedAt` / `errorMessage` markers
     * stamped by the orphan sweep, and re-registers the run as owned by the
     * current process (see [getOrphanedRuns]) — the resumed execution is
     * hosted here, and a second interruption must again be detectable. This
     * is the second sanctioned terminal-exit transition next to
     * [discardInterruptedRun] and is equally guarded in SQL: a run in any
     * other status is left untouched and the method reports failure.
     *
     * @param runId Id of the run to resume.
     * @return `true` when the guarded INTERRUPTED → QUEUED transition was
     *   applied; `false` when the run is missing, not INTERRUPTED, or the
     *   store failed (best-effort contract).
     */
    suspend fun markResumed(runId: String): Boolean

    /**
     * Returns the most recently started non-terminal run of [sessionId], or
     * `null` when every run of the session already finished (or the store is
     * unreadable — best-effort contract). This is the entry point of the
     * chat reattach protocol.
     *
     * @param sessionId Id of the chat session to query.
     * @return The active run, or `null` when none is active.
     */
    suspend fun getActiveRunForSession(sessionId: String): PipelineRun?

    /**
     * Returns the most recently started run of [sessionId] regardless of
     * status, or `null` when the session has never had a run (or the store
     * is unreadable — best-effort contract). The console replay path uses it
     * to pick the baseline trace when no run is currently active.
     *
     * @param sessionId Id of the chat session to query.
     * @return The latest run, or `null` when the session has no runs.
     */
    suspend fun getLatestRunForSession(sessionId: String): PipelineRun?

    /**
     * Observes all runs of [sessionId], most recently started first.
     *
     * @param sessionId Id of the chat session to observe.
     * @return A cold flow re-emitting the full run list on every change;
     *   storage failures degrade to an empty emission.
     */
    fun observeRunsForSession(sessionId: String): Flow<List<PipelineRun>>

    /**
     * Observes the set of session ids that currently own a non-terminal run
     * (QUEUED / RUNNING / WAITING_*). Powers the drawer thread-list
     * indicator: a session in the set renders an in-progress badge so the
     * user can see at a glance which background conversations are still
     * working. Deliberately a session-id projection rather than full run
     * records — the underlying table is written on every node transition,
     * and implementations deduplicate emissions so consumers only react
     * when the set itself changes (run started / run settled).
     *
     * @return A cold flow of the active session-id set, deduplicated;
     *   storage failures degrade to an empty emission.
     */
    fun observeActiveRunSessionIds(): Flow<Set<String>>

    /**
     * Discards an interrupted run: transitions it from
     * [PipelineRunStatus.INTERRUPTED] to [PipelineRunStatus.FAILED] with a
     * fixed "discarded by user" error message. This is the only sanctioned
     * terminal-to-terminal transition — the user explicitly dismissed the
     * resume offer, so the record must stop presenting itself as resumable.
     * Guarded in SQL: a run in any other status (including the other terminal
     * ones) is left untouched, so a racing resume or a stale UI action can
     * never corrupt a settled record.
     *
     * @param runId Id of the run to discard.
     */
    suspend fun discardInterruptedRun(runId: String)

    /**
     * Returns every non-terminal run that is **not owned by the current
     * process** — i.e. whose [createRun] happened in a process that has since
     * died. Such runs are orphans by definition: the in-memory machinery that
     * could finish or resume them (queue worker, suspension deferreds) died
     * with their process. Runs created by the live process are excluded no
     * matter their status, so the startup sweep can never interrupt a run
     * that is actively executing (e.g. kept alive by the foreground service
     * or a WorkManager worker while no Activity exists).
     *
     * WAITING_* runs from dead processes are included: the pending approval /
     * clarification lives only in in-memory deferreds today, so after a
     * process death nothing can ever settle them. Revisit when persistent
     * background HITL ships and suspended runs become resumable.
     *
     * @return Runs whose owning process died mid-execution.
     */
    suspend fun getOrphanedRuns(): List<PipelineRun>
}
