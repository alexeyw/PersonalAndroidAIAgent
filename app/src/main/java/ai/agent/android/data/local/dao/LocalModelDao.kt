package ai.agent.android.data.local.dao

import ai.agent.android.data.local.models.LocalModelEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
     * Live projection of the currently active model — emits a new value
     * whenever any row toggles the `isActive` column. Powers the
     * Settings → Local model card.
     */
    @Query("SELECT * FROM local_models WHERE isActive = 1 LIMIT 1")
    fun observeActiveModel(): Flow<LocalModelEntity?>

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

    /**
     * Returns the number of rows whose [LocalModelEntity.name] matches
     * [fileName] exactly. Used by [LocalModelRepository.isInstalled] to
     * avoid loading the full table just to test presence.
     *
     * @param fileName the on-disk filename to look up.
     * @return row count (0 when not installed, >= 1 when installed).
     */
    @Query("SELECT COUNT(*) FROM local_models WHERE name = :fileName")
    suspend fun countByName(fileName: String): Int

    /**
     * Returns the first row whose [LocalModelEntity.name] matches
     * [fileName] exactly. The `LIMIT 1` makes the call deterministic
     * if the user has somehow ended up with two rows for the same
     * filename. Used by `OnboardingViewModel` to resolve the on-disk
     * path of the *picked* model independently of whichever model
     * currently has `isActive = 1` — picking and activation are
     * orthogonal.
     *
     * @param fileName the on-disk filename to look up.
     * @return the matching entity, or `null` when no row exists.
     */
    @Query("SELECT * FROM local_models WHERE name = :fileName LIMIT 1")
    suspend fun findByName(fileName: String): LocalModelEntity?
}
