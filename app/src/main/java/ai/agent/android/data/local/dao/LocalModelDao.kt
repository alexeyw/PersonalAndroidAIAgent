package ai.agent.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ai.agent.android.data.local.models.LocalModelEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing local LLM models in the Room database.
 */
@Dao
interface LocalModelDao {
    /**
     * Retrieves all saved models as a Flow.
     * 
     * @return Flow containing a list of [LocalModelEntity].
     */
    @Query("SELECT * FROM local_models")
    fun getAllModels(): Flow<List<LocalModelEntity>>

    /**
     * Retrieves the currently active model if one exists.
     * 
     * @return The active [LocalModelEntity] or null.
     */
    @Query("SELECT * FROM local_models WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveModel(): LocalModelEntity?

    /**
     * Inserts a new model record. Replaces if a conflict occurs.
     * 
     * @param model The [LocalModelEntity] to insert.
     * @return The row ID of the newly inserted item.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: LocalModelEntity): Long

    /**
     * Updates an existing model record.
     * 
     * @param model The [LocalModelEntity] to update.
     */
    @Update
    suspend fun updateModel(model: LocalModelEntity)

    /**
     * Deletes a model record by its ID.
     * 
     * @param id The ID of the model to delete.
     */
    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteModelById(id: Long)
    
    /**
     * Unsets the isActive flag for all models.
     */
    @Query("UPDATE local_models SET isActive = 0")
    suspend fun deactivateAllModels()

    /**
     * Sets the isActive flag to 1 for a specific model by its ID.
     * 
     * @param id The ID of the model to activate.
     */
    @Query("UPDATE local_models SET isActive = 1 WHERE id = :id")
    suspend fun activateModelById(id: Long)
}
