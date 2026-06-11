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
}
