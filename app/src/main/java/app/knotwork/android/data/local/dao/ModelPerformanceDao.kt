package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Deletes the oldest samples for [modelPath], keeping only the most recent
     * [keep] rows. Bounds per-model growth — called after each insert. The
     * `id NOT IN (… ORDER BY id DESC LIMIT :keep)` form mirrors the hard-cap
     * retention used by `pipeline_runs`.
     *
     * @param modelPath On-disk path of the model whose history to trim.
     * @param keep Number of newest rows to retain.
     */
    @Query(
        "DELETE FROM model_performance_samples WHERE modelPath = :modelPath AND id NOT IN " +
            "(SELECT id FROM model_performance_samples WHERE modelPath = :modelPath ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun trimToLatest(modelPath: String, keep: Int)

    /**
     * Inserts a sample and trims [modelPath]'s history to [keep] rows in one
     * transaction, so the per-model cap holds even under concurrent recorders.
     *
     * @param sample The sample to persist.
     * @param keep Per-model retention cap.
     */
    @Transaction
    suspend fun insertAndTrim(sample: ModelPerformanceSampleEntity, keep: Int) {
        insert(sample)
        trimToLatest(sample.modelPath, keep)
    }

    /**
     * Deletes every sample for [modelPath]. Called when a model is removed so
     * its history doesn't linger as orphaned rows (samples carry no foreign key).
     *
     * @param modelPath On-disk path of the removed model.
     */
    @Query("DELETE FROM model_performance_samples WHERE modelPath = :modelPath")
    suspend fun deleteForModel(modelPath: String)
}
