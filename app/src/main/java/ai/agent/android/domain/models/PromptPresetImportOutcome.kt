package ai.agent.android.domain.models

/**
 * Outcome of parsing a prompt-preset JSON document — either one of the
 * bundled JSON files under `assets/presets/prompts` or a future
 * `.prompt-preset.json` file authored externally.
 *
 * Mirrors the shape of [PipelinePresetImportOutcome]: parsing either
 * succeeds cleanly, surfaces a schema-version mismatch but still produces
 * a best-effort preset, or fails irrecoverably.
 *
 * @see ai.agent.android.domain.promptio.PromptPresetJsonSerializer
 */
sealed class PromptPresetImportOutcome {

    /**
     * The JSON parsed cleanly and its `schemaVersion` matches what this
     * build understands. The preset is ready to be persisted as-is.
     *
     * @property preset Fully parsed preset.
     */
    data class Success(val preset: PromptPreset) : PromptPresetImportOutcome()

    /**
     * The JSON parsed but the `schemaVersion` field does not match the
     * version this build understands. The preset is still produced on a
     * best-effort basis (unknown fields are dropped); the UI should warn
     * the user before persisting because some configuration may have been
     * lost.
     *
     * @property preset Best-effort parsed preset.
     * @property foundVersion The `schemaVersion` value read from the file.
     * @property expectedVersion The version this build expects.
     */
    data class SchemaMismatch(val preset: PromptPreset, val foundVersion: Int, val expectedVersion: Int) :
        PromptPresetImportOutcome()

    /**
     * Parsing failed irrecoverably (malformed JSON, missing required
     * fields, unknown `NodeType`, non-LLM `NodeType`, etc.). [message] is
     * a human-readable description suitable for the UI or for a log
     * warning when the bundled-catalogue loader skips a corrupt file.
     */
    data class Failure(val message: String) : PromptPresetImportOutcome()
}
