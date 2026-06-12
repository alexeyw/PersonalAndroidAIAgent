package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.knotwork.android.data.local.models.PipelineRunEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [PipelineRunEntity].
 *
 * Status strings are passed in by the repository (enum `name` values); every
 * mutating query that could race a terminal transition takes the terminal
 * status list as a `NOT IN` guard, so a finished run can never be flipped
 * back to an active status by a late writer.
 */
@Dao
interface PipelineRunDao {

    /**
     * Inserts a freshly enqueued run record. Conflicts are IGNOREd — an
     * existing row (whatever its status) is never overwritten, keeping the
     * insert consistent with the write-once terminal guard: a re-delivered or
     * racing insert can never resurrect a settled run as QUEUED.
     *
     * @param run The entity to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRun(run: PipelineRunEntity)

    /**
     * Transitions the run to the RUNNING status, recording the resolved
     * pipeline id and graph content hash, unless the run is already terminal.
     *
     * @param runId Id of the run to update.
     * @param status The RUNNING status name.
     * @param pipelineId Id of the resolved pipeline.
     * @param graphContentHash Content hash of the resolved graph.
     * @param terminalStatuses Status names that must not be overwritten.
     */
    @Query(
        "UPDATE pipeline_runs SET status = :status, pipelineId = :pipelineId, " +
            "graphContentHash = :graphContentHash " +
            "WHERE id = :runId AND status NOT IN (:terminalStatuses)",
    )
    suspend fun markRunning(
        runId: String,
        status: String,
        pipelineId: String,
        graphContentHash: String,
        terminalStatuses: List<String>,
    )

    /**
     * Updates the run's status unless the run is already terminal.
     *
     * @param runId Id of the run to update.
     * @param status The new status name.
     * @param terminalStatuses Status names that must not be overwritten.
     */
    @Query(
        "UPDATE pipeline_runs SET status = :status " +
            "WHERE id = :runId AND status NOT IN (:terminalStatuses)",
    )
    suspend fun updateStatus(runId: String, status: String, terminalStatuses: List<String>)

    /**
     * Records the node currently executing unless the run is already terminal.
     *
     * @param runId Id of the run to update.
     * @param nodeId Id of the graph node that just started.
     * @param terminalStatuses Status names that must not be overwritten.
     */
    @Query(
        "UPDATE pipeline_runs SET currentNodeId = :nodeId " +
            "WHERE id = :runId AND status NOT IN (:terminalStatuses)",
    )
    suspend fun updateCurrentNode(runId: String, nodeId: String, terminalStatuses: List<String>)

    /**
     * Writes a terminal status with its finish timestamp and optional error
     * message. Idempotent: a run that is already terminal is left untouched.
     *
     * @param runId Id of the run to finish.
     * @param status The terminal status name to write.
     * @param finishedAt Epoch millis of the terminal transition.
     * @param errorMessage Failure / interruption reason, or `null`.
     * @param terminalStatuses Status names that must not be overwritten.
     */
    @Query(
        "UPDATE pipeline_runs SET status = :status, finishedAt = :finishedAt, " +
            "errorMessage = :errorMessage " +
            "WHERE id = :runId AND status NOT IN (:terminalStatuses)",
    )
    suspend fun finishRun(
        runId: String,
        status: String,
        finishedAt: Long,
        errorMessage: String?,
        terminalStatuses: List<String>,
    )

    /**
     * Returns the run with [runId], or `null` when no such row exists. Backs
     * checkpoint-resume validation, which addresses one specific run.
     *
     * @param runId Id of the run to load.
     */
    @Query("SELECT * FROM pipeline_runs WHERE id = :runId")
    suspend fun getRun(runId: String): PipelineRunEntity?

    /**
     * Flips a resumable run back to the QUEUED status for checkpoint resume,
     * clearing the markers ([PipelineRunEntity.finishedAt],
     * [PipelineRunEntity.errorMessage]) a sweep may have stamped. The
     * `WHERE status = :fromStatus` guard pins the transition to the expected
     * starting status — INTERRUPTED for the terminal-exit path next to
     * [discardInterruptedRun], or a WAITING_* status for a parked run whose
     * pending interaction was answered; any other status leaves the row
     * untouched.
     *
     * @param runId Id of the run to resume.
     * @param fromStatus The status name the row must currently hold.
     * @param toStatus The QUEUED status name to write.
     * @return The number of updated rows — `1` when the guarded transition
     *   applied, `0` when the row was missing or in a different status.
     */
    @Query(
        "UPDATE pipeline_runs SET status = :toStatus, finishedAt = NULL, errorMessage = NULL " +
            "WHERE id = :runId AND status = :fromStatus",
    )
    suspend fun markResumed(runId: String, fromStatus: String, toStatus: String): Int

    /**
     * Returns the most recently started run of [sessionId] whose status is in
     * [activeStatuses], or `null` when the session has no active run.
     *
     * @param sessionId Id of the chat session to query.
     * @param activeStatuses Non-terminal status names to match.
     */
    @Query(
        "SELECT * FROM pipeline_runs WHERE sessionId = :sessionId " +
            "AND status IN (:activeStatuses) ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun getActiveRunForSession(sessionId: String, activeStatuses: List<String>): PipelineRunEntity?

    /**
     * Returns the most recently started run of [sessionId] regardless of
     * status, or `null` when the session has never had a run. Backs the
     * console replay baseline for sessions whose last run already finished.
     *
     * @param sessionId Id of the chat session to query.
     */
    @Query("SELECT * FROM pipeline_runs WHERE sessionId = :sessionId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestRunForSession(sessionId: String): PipelineRunEntity?

    /**
     * Observes all runs of [sessionId], most recently started first.
     *
     * @param sessionId Id of the chat session to observe.
     */
    @Query("SELECT * FROM pipeline_runs WHERE sessionId = :sessionId ORDER BY startedAt DESC")
    fun observeRunsForSession(sessionId: String): Flow<List<PipelineRunEntity>>

    /**
     * Returns every run whose status is in [statuses]. Used by the orphan
     * sweep at application start (all non-terminal statuses; process-owned
     * runs are filtered out by the repository).
     *
     * @param statuses Status names to match.
     */
    @Query("SELECT * FROM pipeline_runs WHERE status IN (:statuses)")
    suspend fun getRunsByStatuses(statuses: List<String>): List<PipelineRunEntity>

    /**
     * Observes the distinct session ids owning a run whose status is in
     * [statuses]. Backs the drawer thread-list activity indicator (all
     * non-terminal statuses). A single-column DISTINCT projection on
     * purpose: Room re-runs the query on every `pipeline_runs` write (the
     * engine writes per-node progress throughout a run), so each
     * invalidation must stay a cheap column read instead of materialising
     * full rows the consumer would reduce to ids anyway.
     *
     * @param statuses Status names to match.
     */
    @Query("SELECT DISTINCT sessionId FROM pipeline_runs WHERE status IN (:statuses)")
    fun observeSessionIdsByStatuses(statuses: List<String>): Flow<List<String>>

    /**
     * Discards an interrupted run: flips it to the FAILED status with the
     * supplied error message. The `WHERE status = :fromStatus` guard pins the
     * transition to INTERRUPTED rows only — the single sanctioned
     * terminal-to-terminal transition (user dismissed the resume offer); any
     * other status leaves the row untouched.
     *
     * @param runId Id of the run to discard.
     * @param fromStatus The INTERRUPTED status name the row must currently hold.
     * @param toStatus The FAILED status name to write.
     * @param errorMessage The "discarded by user" marker to record.
     */
    @Query(
        "UPDATE pipeline_runs SET status = :toStatus, errorMessage = :errorMessage " +
            "WHERE id = :runId AND status = :fromStatus",
    )
    suspend fun discardInterruptedRun(runId: String, fromStatus: String, toStatus: String, errorMessage: String)

    /**
     * Retention: deletes every **terminal** run that is not among the
     * [keepPerSession] most recently started runs of its own session. The
     * per-session window counts runs of *any* status, so a session whose
     * recent slots are filled by active runs keeps proportionally fewer old
     * terminal ones — the window is a hard cap, not a terminal-only quota.
     * Non-terminal runs (including WAITING_* runs parked on a background
     * approval or clarification) are never deleted: their expiry is owned by
     * the pending-interaction maintenance pass, which settles them to FAILED
     * first. Persisted trace rows ride the `trace_steps.runId` foreign-key
     * cascade.
     *
     * @param keepPerSession How many most-recent runs each session keeps.
     * @param terminalStatuses Terminal status names — the only deletable ones.
     * @return The number of deleted runs.
     */
    @Query(
        "DELETE FROM pipeline_runs WHERE status IN (:terminalStatuses) AND id NOT IN (" +
            "SELECT recent.id FROM pipeline_runs AS recent " +
            "WHERE recent.sessionId = pipeline_runs.sessionId " +
            "ORDER BY recent.startedAt DESC LIMIT :keepPerSession)",
    )
    suspend fun deleteTerminalRunsBeyondSessionLimit(keepPerSession: Int, terminalStatuses: List<String>): Int

    /**
     * Retention: deletes every **terminal** run whose terminal transition
     * happened before [cutoff], regardless of the per-session count. Rows
     * with a `NULL` `finishedAt` are left untouched (terminal rows always
     * carry the timestamp; the guard is defence in depth). Persisted trace
     * rows ride the `trace_steps.runId` foreign-key cascade.
     *
     * @param cutoff Epoch millis; runs finished strictly before it are deleted.
     * @param terminalStatuses Terminal status names — the only deletable ones.
     * @return The number of deleted runs.
     */
    @Query(
        "DELETE FROM pipeline_runs WHERE status IN (:terminalStatuses) " +
            "AND finishedAt IS NOT NULL AND finishedAt < :cutoff",
    )
    suspend fun deleteTerminalRunsFinishedBefore(cutoff: Long, terminalStatuses: List<String>): Int
}
