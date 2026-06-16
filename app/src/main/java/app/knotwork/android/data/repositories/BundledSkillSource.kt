package app.knotwork.android.data.repositories

import app.knotwork.android.domain.models.Skill

/**
 * Reads the bundled skills shipped under `assets/presets/skills`.
 *
 * Extracted behind an interface so [SkillRepositoryImpl] can be unit-tested
 * without touching the Android asset manager — the seed path then mocks this
 * source, while [AssetBundledSkillSource] handles the real I/O on the IO
 * dispatcher.
 */
interface BundledSkillSource {
    /**
     * Loads and parses every bundled skill JSON. Malformed files are logged
     * and skipped rather than failing the whole set, so a single corrupt file
     * cannot hide the rest of the starter catalogue.
     *
     * @return The parsed bundled skills (each with `isBundled = true`).
     */
    suspend fun load(): List<Skill>
}
