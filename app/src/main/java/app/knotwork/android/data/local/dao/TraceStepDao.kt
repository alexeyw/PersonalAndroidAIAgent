package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.knotwork.android.data.local.models.TraceStepEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for TraceStepEntity.
 */
@Dao
interface TraceStepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTraceStep(step: TraceStepEntity)

    /**
     * Inserts a batch of trace records in a single transaction. This is the
     * write path of the buffered run-trace recorder: flushing accumulated
     * records in one statement keeps SQLCipher I/O off the hot inference path.
     *
     * @param steps The records to insert, in their in-run order.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTraceSteps(steps: List<TraceStepEntity>)

    @Query("SELECT * FROM trace_steps WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getTraceStepsForSession(sessionId: String): Flow<List<TraceStepEntity>>

    /**
     * Returns the full persisted trace of one pipeline run ordered by the
     * in-run sequence number — the order events were emitted by the engine.
     *
     * @param runId The pipeline run id.
     * @return All trace records of the run, oldest first.
     */
    @Query("SELECT * FROM trace_steps WHERE runId = :runId ORDER BY seq ASC")
    suspend fun getTraceStepsForRun(runId: String): List<TraceStepEntity>

    @Query("DELETE FROM trace_steps WHERE sessionId = :sessionId")
    suspend fun deleteTraceStepsForSession(sessionId: String)
}
