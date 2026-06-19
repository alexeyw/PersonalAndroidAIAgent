package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.knotwork.android.data.local.models.ModelPerformanceSampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the `model_performance_samples` table.
 */
@Dao
interface ModelPerformanceDao {

    /**
     * Inserts one performance sample.
     *
     * @param sample The sample row to persist (its `id` is auto-generated).
     * @return The row id assigned to the inserted sample.
     */
    @Insert
    suspend fun insert(sample: ModelPerformanceSampleEntity): Long

    /**
     * Live projection of the most recent samples for one model, newest first.
     *
     * @param modelPath On-disk path of the model whose samples to observe.
     * @param limit Maximum number of rows (the rolling-window size).
     * @return A [Flow] emitting the model's most recent samples, newest first.
     */
    @Query("SELECT * FROM model_performance_samples WHERE modelPath = :modelPath ORDER BY id DESC LIMIT :limit")
    fun observeRecentForModel(modelPath: String, limit: Int): Flow<List<ModelPerformanceSampleEntity>>
}
