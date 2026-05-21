package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.LocalModelDao
import ai.agent.android.data.mappers.toDomain
import ai.agent.android.data.mappers.toEntity
import ai.agent.android.domain.models.ActiveModelMeta
import ai.agent.android.domain.models.LocalModel
import ai.agent.android.domain.repositories.LocalModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
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

    override suspend fun isInstalled(fileName: String): Boolean = withContext(Dispatchers.IO) {
        localModelDao.countByName(fileName) > 0
    }

    override fun observeActiveModelMeta(): Flow<ActiveModelMeta?> = localModelDao.observeActiveModel()
        .map { entity ->
            if (entity == null) {
                null
            } else {
                val file = runCatching { File(entity.path) }.getOrNull()
                val downloadedAt = file?.takeIf { it.exists() }?.lastModified()
                ActiveModelMeta(
                    modelId = entity.id,
                    name = entity.name,
                    sizeBytes = entity.size,
                    contextWindowTokens = DEFAULT_CONTEXT_WINDOW_TOKENS,
                    quantization = parseQuantization(entity.name),
                    downloadedAtMs = downloadedAt,
                )
            }
        }
        .flowOn(Dispatchers.IO)

    /**
     * Best-effort quantization marker extracted from a model filename.
     * Matches conventional patterns like `gemma-2b-it-q4_K_M` →
     * `Q4_K_M`, `qwen2-7b-f16` → `F16`. Returns `null` if no recognizable
     * marker is found — the UI then hides the quantization column.
     */
    internal fun parseQuantization(modelName: String): String? {
        val match = QUANTIZATION_PATTERN.find(modelName) ?: return null
        return match.value.uppercase()
    }

    private companion object {
        /** Default context-window assumption for the active-model card. */
        const val DEFAULT_CONTEXT_WINDOW_TOKENS: Int = 2_048

        /**
         * Quantization markers we recognize: `q4_K_M`, `q5_0`, `q8_0`,
         * `f16`, `bf16`, `fp16`, `int8`. Case-insensitive.
         */
        val QUANTIZATION_PATTERN: Regex =
            Regex("""(?i)\b(q[0-9](?:_[0-9a-z]+)?|f16|bf16|fp16|int8)\b""")
    }
}
