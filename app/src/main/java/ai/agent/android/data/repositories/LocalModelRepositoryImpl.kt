package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.LocalModelDao
import ai.agent.android.data.mappers.toDomain
import ai.agent.android.data.mappers.toEntity
import ai.agent.android.domain.models.LocalModel
import ai.agent.android.domain.repositories.LocalModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LocalModelRepository] that uses [LocalModelDao] as the data source.
 */
@Singleton
class LocalModelRepositoryImpl @Inject constructor(private val localModelDao: LocalModelDao) : LocalModelRepository {

    override fun getAllModels(): Flow<List<LocalModel>> = localModelDao.getAllModels().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun getActiveModel(): LocalModel? = withContext(Dispatchers.IO) {
        localModelDao.getActiveModel()?.toDomain()
    }

    override suspend fun insertModel(model: LocalModel): Long = withContext(Dispatchers.IO) {
        localModelDao.insertModel(model.toEntity())
    }

    override suspend fun updateModel(model: LocalModel): Unit = withContext(Dispatchers.IO) {
        localModelDao.updateModel(model.toEntity())
    }

    override suspend fun deleteModelById(id: Long): Unit = withContext(Dispatchers.IO) {
        localModelDao.deleteModelById(id)
    }

    override suspend fun setActiveModel(id: Long): Unit = withContext(Dispatchers.IO) {
        localModelDao.deactivateAllModels()
        localModelDao.activateModelById(id)
    }
}
