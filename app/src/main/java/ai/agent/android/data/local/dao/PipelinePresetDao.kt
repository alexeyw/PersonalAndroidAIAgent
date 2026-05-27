package ai.agent.android.data.local.dao

import ai.agent.android.data.local.models.PipelinePresetEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the user-saved pipeline-preset catalogue
 * (`pipeline_presets`, schema v24).
 *
 * Bundled presets ship in `assets/presets/pipelines` and never reach this
 * DAO — the repository layer composes the two catalogues for its callers.
 */
@Dao
interface PipelinePresetDao {

    /**
     * Observes every user preset, newest first.
     *
     * @return A [Flow] that re-emits on every insert / delete.
     */
    @Query("SELECT * FROM pipeline_presets ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PipelinePresetEntity>>

    /**
     * Resolves a single user preset by id.
     *
     * @param presetId The stable preset id.
     * @return The matching row, or `null` if no row with [presetId] exists.
     */
    @Query("SELECT * FROM pipeline_presets WHERE id = :presetId")
    suspend fun getById(presetId: String): PipelinePresetEntity?

    /**
     * Inserts or replaces a user preset row (insert-or-update semantics).
     *
     * @param entity The row to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PipelinePresetEntity)

    /**
     * Deletes the user preset with [presetId]. No-op when no matching row
     * exists.
     *
     * @param presetId The id of the user preset to delete.
     */
    @Query("DELETE FROM pipeline_presets WHERE id = :presetId")
    suspend fun deleteById(presetId: String)
}
