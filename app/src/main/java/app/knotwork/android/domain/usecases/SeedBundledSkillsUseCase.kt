package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.SkillRepository
import javax.inject.Inject

/**
 * Seeds the bundled skills (shipped under `assets/presets/skills`) into the
 * `skills` table.
 *
 * Invoked from `InitializeAppUseCase` on **every** launch — not only the first
 * — so that users who upgrade from a build without skills still receive the
 * bundled catalogue. The operation is idempotent by construction: the
 * repository upserts each bundled skill by its stable id, so repeated calls
 * never create duplicates and never touch user-authored rows.
 *
 * Delegating the asset I/O to [SkillRepository.seedBundledSkills] keeps this
 * use case Android-free (the asset read happens in the data layer).
 */
class SeedBundledSkillsUseCase @Inject constructor(private val skillRepository: SkillRepository) {
    /**
     * Runs the idempotent seed. Any failure is the repository's concern; this
     * use case simply triggers it.
     */
    suspend operator fun invoke() {
        skillRepository.seedBundledSkills()
    }
}
