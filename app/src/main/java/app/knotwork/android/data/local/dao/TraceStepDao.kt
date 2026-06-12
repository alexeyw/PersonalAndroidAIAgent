package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.knotwork.android.data.local.models.TraceStepEntity

/**
 * Data Access Object for [TraceStepEntity] — the persistent pipeline-run
 * trace. The write surface is deliberately batch-only: every production
 * insert goes through the buffered run-trace recorder, so a per-row insert
 * path would only invite per-event SQLCipher commits back onto the
 * streaming hot path. Per-session reads and deletes are likewise absent —
 * the trace is queried per run; session-scoped cleanup rides the
 * `chat_sessions` foreign-key cascade, and run-scoped cleanup rides the
 * `pipeline_runs` cascade (the only direct delete here targets legacy
 * pre-run rows, see [deleteLegacyStepsBefore]).
 */
@Dao
interface TraceStepDao {
    /**
     * Inserts a batch of trace records in a single transaction. This is the
     * write path of the buffered run-trace recorder: flushing accumulated
     * records in one statement keeps SQLCipher I/O off the hot inference path.
     *
     * @param steps The records to insert, in their in-run order.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTraceSteps(steps: List<TraceStepEntity>)

    /**
     * Returns the full persisted trace of one pipeline run ordered by the
     * in-run sequence number — the order events were emitted by the engine.
     *
     * @param runId The pipeline run id.
     * @return All trace records of the run, oldest first.
     */
    @Query("SELECT * FROM trace_steps WHERE runId = :runId ORDER BY seq ASC")
    suspend fun getTraceStepsForRun(runId: String): List<TraceStepEntity>

    /**
     * Retention: deletes legacy trace rows written before run-trace
     * persistence existed (`runId IS NULL`) once they age past [cutoff].
     * Such rows belong to no run, so they are unreachable from any replay
     * path and would otherwise survive until their whole session is deleted.
     * Run-scoped rows are never touched here — they ride the
     * `pipeline_runs` foreign-key cascade when retention deletes their run.
     *
     * @param cutoff Epoch millis; legacy rows older than it are deleted.
     * @return The number of deleted rows.
     */
    @Query("DELETE FROM trace_steps WHERE runId IS NULL AND timestamp < :cutoff")
    suspend fun deleteLegacyStepsBefore(cutoff: Long): Int
}
