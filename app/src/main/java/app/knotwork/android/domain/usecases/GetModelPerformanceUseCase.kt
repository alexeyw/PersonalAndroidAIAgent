package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.PerformanceConstants
import app.knotwork.android.domain.models.ModelPerformanceSummary
import app.knotwork.android.domain.repositories.ModelPerformanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Produces the rolling-average performance summary for a model, computed on the
 * fly from its most recent samples.
 *
 * Aggregation is deliberately recomputed from the raw sample rows rather than
 * stored as a precomputed aggregate: the window is small
 * ([PerformanceConstants.SAMPLE_WINDOW] rows) and folding it on every emission
 * keeps a single source of truth (the samples table) without a second table to
 * keep in sync.
 *
 * @property repository Source of the per-model samples.
 */
class GetModelPerformanceUseCase @Inject constructor(private val repository: ModelPerformanceRepository) {
    /**
     * Observes the rolling [ModelPerformanceSummary] for the model at
     * [modelPath].
     *
     * @param modelPath Absolute on-disk path of the model to summarise.
     * @return A [Flow] emitting the latest summary, or `null` when the model has
     *   no recorded runs yet (the card then shows its "no runs yet" empty state).
     */
    operator fun invoke(modelPath: String): Flow<ModelPerformanceSummary?> =
        repository.observeRecentForModel(modelPath, PerformanceConstants.SAMPLE_WINDOW)
            .map { samples -> ModelPerformanceSummary.from(samples) }
}
