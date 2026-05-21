package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.ActiveModelMeta
import ai.agent.android.domain.models.LocalModel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing downloaded LLM models metadata.
 */
interface LocalModelRepository {
    /**
     * Retrieves all saved models as a Flow.
     *
     * @return Flow containing a list of [LocalModel].
     */
    fun getAllModels(): Flow<List<LocalModel>>

    /**
     * Retrieves the currently active model if one exists.
     *
     * @return The active [LocalModel] or null.
     */
    suspend fun getActiveModel(): LocalModel?

    /**
     * Inserts a new model record.
     *
     * @param model The [LocalModel] to insert.
     * @return The row ID of the newly inserted item.
     */
    suspend fun insertModel(model: LocalModel): Long

    /**
     * Updates an existing model record.
     *
     * @param model The [LocalModel] to update.
     */
    suspend fun updateModel(model: LocalModel)

    /**
     * Deletes a model record by its ID.
     *
     * @param id The ID of the model to delete.
     */
    suspend fun deleteModelById(id: Long)

    /**
     * Sets a specific model as the active one, unsetting any previously active models.
     *
     * @param id The ID of the model to activate.
     */
    suspend fun setActiveModel(id: Long)

    /**
     * Live snapshot of the currently active model enriched with on-device
     * metadata (file size, parsed quantization marker, downloaded
     * timestamp). Emits `null` when no model has been activated yet —
     * the Settings → Local model card then renders an empty state with a
     * primary "Browse models" CTA.
     */
    fun observeActiveModelMeta(): Flow<ActiveModelMeta?>
}
