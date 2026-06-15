package app.knotwork.android.domain.models

/**
 * Outcome of parsing a skill JSON document — one of the bundled JSON files
 * under `assets/presets/skills` (and, in future, an externally authored
 * `.skill.json` file).
 *
 * Mirrors the shape of [PromptPresetImportOutcome]: parsing either succeeds
 * cleanly, surfaces a schema-version mismatch but still produces a best-effort
 * skill, or fails irrecoverably.
 *
 * @see app.knotwork.android.domain.skillio.SkillJsonSerializer
 */
sealed class SkillImportOutcome {

    /**
     * The JSON parsed cleanly and its `schemaVersion` matches what this build
     * understands. The skill is ready to be persisted as-is.
     *
     * @property skill Fully parsed skill.
     */
    data class Success(val skill: Skill) : SkillImportOutcome()

    /**
     * The JSON parsed but the `schemaVersion` field does not match the version
     * this build understands. The skill is still produced on a best-effort
     * basis (unknown fields are dropped); the UI should warn the user before
     * persisting because some configuration may have been lost.
     *
     * @property skill Best-effort parsed skill.
     * @property foundVersion The `schemaVersion` value read from the file.
     * @property expectedVersion The version this build expects.
     */
    data class SchemaMismatch(val skill: Skill, val foundVersion: Int, val expectedVersion: Int) :
        SkillImportOutcome()

    /**
     * Parsing failed irrecoverably (malformed JSON, missing required fields,
     * etc.). [message] is a human-readable description suitable for the UI or
     * for a log warning when the bundled-catalogue loader skips a corrupt
     * file.
     *
     * @property message Human-readable failure description.
     */
    data class Failure(val message: String) : SkillImportOutcome()
}
