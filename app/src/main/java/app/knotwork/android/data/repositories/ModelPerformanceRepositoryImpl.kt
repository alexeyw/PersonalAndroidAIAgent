package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.ModelPerformanceDao
import app.knotwork.android.data.mappers.toDomain
import app.knotwork.android.data.mappers.toEntity
import app.knotwork.android.domain.models.ModelPerformanceSample
import app.knotwork.android.domain.repositories.ModelPerformanceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ModelPerformanceRepository].
 */
@Singleton
class ModelPerformanceRepositoryImpl @Inject constructor(private val dao: ModelPerformanceDao) :
    ModelPerformanceRepository {

    override suspend fun record(sample: ModelPerformanceSample) {
        // Best-effort: a failed metrics write must never break the inference run
        // that produced it. Swallow storage errors (logged), but re-throw
        // cancellation so structured concurrency keeps working.
        try {
            withContext(Dispatchers.IO) {
                dao.insert(sample.toEntity())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to record model performance sample")
        }
    }

    override fun observeRecentForModel(modelPath: String, limit: Int): Flow<List<ModelPerformanceSample>> =
        dao.observeRecentForModel(modelPath, limit)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
}
