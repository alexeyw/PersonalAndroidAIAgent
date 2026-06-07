package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.knotwork.android.data.local.models.PromptPresetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the user-saved prompt-preset catalogue
 * (`prompt_presets`, schema v25).
 *
 * Bundled presets ship in `assets/presets/prompts` and never reach this
 * DAO — the repository layer composes the two catalogues for its callers.
 */
@Dao
interface PromptPresetDao {

    /**
     * Observes every user preset, newest first.
     *
     * @return A [Flow] that re-emits on every insert / delete.
     */
    @Query("SELECT * FROM prompt_presets ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PromptPresetEntity>>

    /**
     * Observes every user preset targeting the given [nodeTypeKey],
     * newest first.
     *
     * @param nodeTypeKey Wire-form key of the target `NodeType`.
     * @return A [Flow] of matching rows.
     */
    @Query("SELECT * FROM prompt_presets WHERE nodeTypeKey = :nodeTypeKey ORDER BY createdAt DESC")
    fun getAllForType(nodeTypeKey: String): Flow<List<PromptPresetEntity>>

    /**
     * Resolves a single user preset by id.
     *
     * @param presetId The stable preset id.
     * @return The matching row, or `null` if no row with [presetId] exists.
     */
    @Query("SELECT * FROM prompt_presets WHERE id = :presetId")
    suspend fun getById(presetId: String): PromptPresetEntity?

    /**
     * Inserts or replaces a user preset row (insert-or-update semantics).
     *
     * @param entity The row to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PromptPresetEntity)

    /**
     * Deletes the user preset with [presetId]. No-op when no matching row
     * exists.
     *
     * @param presetId The id of the user preset to delete.
     */
    @Query("DELETE FROM prompt_presets WHERE id = :presetId")
    suspend fun deleteById(presetId: String)
}
