package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.ModelPerformanceSample
import kotlinx.coroutines.flow.Flow

/**
 * Persists and queries per-model inference performance samples
 * ([ModelPerformanceSample]).
 *
 * Unlike [MetricsRepository], which holds session-scoped, process-local figures
 * not keyed by model, this repository durably records one row per generation
 * keyed by the model's on-disk path, so the model screen can show rolling
 * averages and the controlled benchmark result that survive process death.
 */
interface ModelPerformanceRepository {

    /**
     * Records a single inference sample.
     *
     * **Best-effort:** the implementation persists on `Dispatchers.IO` and never
     * propagates a storage failure to the caller (a dropped metrics row must
     * never break an inference run). Cancellation is still honoured.
     *
     * @param sample The sample to persist (its `id` is ignored; Room assigns one).
     */
    suspend fun record(sample: ModelPerformanceSample)

    /**
     * Live view of the most recent samples for one model, newest first, capped
     * at [limit] rows. Emits a fresh list whenever a new sample for [modelPath]
     * lands, so the Performance card's rolling average updates reactively.
     *
     * @param modelPath Absolute on-disk path of the model to observe.
     * @param limit Maximum number of rows (the rolling-window size).
     * @return A [Flow] of the model's most recent samples, newest first.
     */
    fun observeRecentForModel(modelPath: String, limit: Int): Flow<List<ModelPerformanceSample>>

    /**
     * Deletes all recorded samples for the model at [modelPath]. Called when a
     * model is removed so its history is not left orphaned (samples are keyed by
     * path, not a foreign key). Best-effort, like [record].
     *
     * @param modelPath Absolute on-disk path of the removed model.
     */
    suspend fun deleteForModel(modelPath: String)
}
