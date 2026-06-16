package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.Skill
import kotlinx.coroutines.flow.Flow

/**
 * Catalogue of reusable [Skill]s, composed of two sub-kinds that share a
 * single Room table (`skills`), distinguished by [Skill.isBundled]:
 *
 * - **Bundled** skills ship in `assets/presets/skills` and are seeded into the
 *   table with stable ids via [seedBundledSkills]. They are read-only.
 * - **User** skills are created/edited/deleted freely.
 *
 * Bundled skills must never reach [saveUserSkill] / [deleteUserSkill] — those
 * reject a bundled argument so a read-only skill can never be mutated through
 * the user path.
 */
interface SkillRepository {

    /** Observes the read-only bundled skills, newest first. */
    fun getBundledSkills(): Flow<List<Skill>>

    /** Observes the user-authored skills, newest first. */
    fun getUserSkills(): Flow<List<Skill>>

    /** Observes every skill (bundled + user), newest first. */
    fun getAllSkills(): Flow<List<Skill>>

    /**
     * Resolves a single skill (bundled or user) by id.
     *
     * @param id The stable skill id.
     * @return The matching skill, or `null` when none exists.
     */
    suspend fun getSkillById(id: String): Skill?

    /**
     * Inserts or updates a **user** skill (insert-or-update by id). Stamps
     * `updatedAt`; preserves `createdAt` on update.
     *
     * @param skill The skill to persist. Must have `isBundled == false`.
     * @throws IllegalArgumentException when [skill] is bundled.
     */
    suspend fun saveUserSkill(skill: Skill)

    /**
     * Deletes the **user** skill with [id]. No-op when no matching user row
     * exists; refuses to delete a bundled skill.
     *
     * @param id The id of the user skill to delete.
     */
    suspend fun deleteUserSkill(id: String)

    /**
     * Creates an editable user copy of the skill with [sourceId] (bundled or
     * user), named "<name> (copy)", with a fresh UUID id and `isBundled =
     * false`, and persists it.
     *
     * @param sourceId The id of the skill to duplicate.
     * @return The newly created copy, or `null` when [sourceId] does not
     *   resolve to an existing skill.
     */
    suspend fun duplicateSkill(sourceId: String): Skill?

    /**
     * Idempotently seeds the bundled skills from `assets/presets/skills` into
     * the table (upsert by stable id, `isBundled = true`). Safe to call on
     * every launch — repeated calls never create duplicates and never touch
     * user rows. Used by `SeedBundledSkillsUseCase`.
     */
    suspend fun seedBundledSkills()
}
