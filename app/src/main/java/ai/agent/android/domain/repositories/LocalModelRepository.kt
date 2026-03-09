package ai.agent.android.domain.repositories

import ai.agent.android.data.local.models.LocalModelEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing downloaded LLM models metadata.
 */
interface LocalModelRepository {
    /**
     * Retrieves all saved models as a Flow.
     * 
     * @return Flow containing a list of [LocalModelEntity].
     */
    fun getAllModels(): Flow<List<LocalModelEntity>>

    /**
     * Retrieves the currently active model if one exists.
     * 
     * @return The active [LocalModelEntity] or null.
     */
    suspend fun getActiveModel(): LocalModelEntity?

    /**
     * Inserts a new model record.
     * 
     * @param model The [LocalModelEntity] to insert.
     * @return The row ID of the newly inserted item.
     */
    suspend fun insertModel(model: LocalModelEntity): Long

    /**
     * Updates an existing model record.
     * 
     * @param model The [LocalModelEntity] to update.
     */
    suspend fun updateModel(model: LocalModelEntity)

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
}
