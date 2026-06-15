package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.knotwork.android.data.local.models.SkillEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the skill catalogue (`skills`, schema v36).
 *
 * Holds both bundled (`isBundled = 1`) and user (`isBundled = 0`) skills; the
 * repository layer splits them for its callers.
 */
@Dao
interface SkillDao {

    /** Observes every skill (bundled + user), most recently edited first. */
    @Query("SELECT * FROM skills ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<SkillEntity>>

    /** Observes the bundled skills, by display name (they share a seed time). */
    @Query("SELECT * FROM skills WHERE isBundled = 1 ORDER BY name COLLATE NOCASE ASC")
    fun getBundled(): Flow<List<SkillEntity>>

    /** Observes the user skills, most recently edited first. */
    @Query("SELECT * FROM skills WHERE isBundled = 0 ORDER BY updatedAt DESC")
    fun getUser(): Flow<List<SkillEntity>>

    /**
     * Resolves a single skill by id.
     *
     * @param skillId The stable skill id.
     * @return The matching row, or `null` when none exists.
     */
    @Query("SELECT * FROM skills WHERE id = :skillId")
    suspend fun getById(skillId: String): SkillEntity?

    /**
     * Inserts or replaces a skill row (insert-or-update semantics). Used both
     * for user-skill saves and for the idempotent bundled seed.
     *
     * @param entity The row to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SkillEntity)

    /**
     * Deletes the **user** skill with [skillId]. The `isBundled = 0` guard
     * makes deleting a bundled row a no-op even if a caller bypasses the
     * repository check. No-op when no matching user row exists.
     *
     * @param skillId The id of the user skill to delete.
     */
    @Query("DELETE FROM skills WHERE id = :skillId AND isBundled = 0")
    suspend fun deleteUserById(skillId: String)
}
