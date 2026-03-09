package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.LocalModelDao
import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.domain.repositories.LocalModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LocalModelRepository] that uses [LocalModelDao] as the data source.
 */
@Singleton
class LocalModelRepositoryImpl @Inject constructor(
    private val localModelDao: LocalModelDao
) : LocalModelRepository {

    override fun getAllModels(): Flow<List<LocalModelEntity>> {
        return localModelDao.getAllModels()
    }

    override suspend fun getActiveModel(): LocalModelEntity? = withContext(Dispatchers.IO) {
        localModelDao.getActiveModel()
    }

    override suspend fun insertModel(model: LocalModelEntity): Long = withContext(Dispatchers.IO) {
        localModelDao.insertModel(model)
    }

    override suspend fun updateModel(model: LocalModelEntity): Unit = withContext(Dispatchers.IO) {
        localModelDao.updateModel(model)
    }

    override suspend fun deleteModelById(id: Long): Unit = withContext(Dispatchers.IO) {
        localModelDao.deleteModelById(id)
    }

    override suspend fun setActiveModel(id: Long): Unit = withContext(Dispatchers.IO) {
        localModelDao.deactivateAllModels()
        localModelDao.activateModelById(id)
    }
}
