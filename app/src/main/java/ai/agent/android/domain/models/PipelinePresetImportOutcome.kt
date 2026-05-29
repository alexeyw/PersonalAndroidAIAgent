package ai.agent.android.domain.models

/**
 * Outcome of parsing a pipeline-preset JSON document — either one of the
 * bundled JSON files under `assets/presets/pipelines` or a `.preset.json`
 * file exported from the browser-side editor.
 *
 * Mirrors the shape of [PipelineImportOutcome]; the preset format wraps the
 * same pipeline-graph schema with three extra top-level fields
 * (`category`, `tags`, `description`), so the same three outcomes apply.
 *
 * @see ai.agent.android.domain.pipelineio.PipelinePresetJsonSerializer
 */
sealed class PipelinePresetImportOutcome {

    /**
     * The JSON parsed cleanly and its `schemaVersion` matches what this
     * build understands. The preset is ready to be persisted as-is.
     *
     * @property preset Fully parsed preset.
     */
    data class Success(val preset: PipelinePreset) : PipelinePresetImportOutcome()

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
    data class SchemaMismatch(val preset: PipelinePreset, val foundVersion: Int, val expectedVersion: Int) :
        PipelinePresetImportOutcome()

    /**
     * Parsing failed irrecoverably (malformed JSON, missing required
     * fields, unknown `NodeType`, etc.). [message] is a human-readable
     * description suitable for the UI or for a log warning when the
     * bundled-catalogue loader skips a corrupt file.
     */
    data class Failure(val message: String) : PipelinePresetImportOutcome()
}
