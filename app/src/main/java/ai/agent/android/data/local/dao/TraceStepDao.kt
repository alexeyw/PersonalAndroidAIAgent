package ai.agent.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ai.agent.android.data.local.models.TraceStepEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for TraceStepEntity.
 */
@Dao
interface TraceStepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTraceStep(step: TraceStepEntity)

    @Query("SELECT * FROM trace_steps WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getTraceStepsForSession(sessionId: String): Flow<List<TraceStepEntity>>

    @Query("DELETE FROM trace_steps WHERE sessionId = :sessionId")
    suspend fun deleteTraceStepsForSession(sessionId: String)
}
