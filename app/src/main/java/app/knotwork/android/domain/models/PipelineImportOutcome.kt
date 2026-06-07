package app.knotwork.android.domain.models

/**
 * Outcome of parsing a pipeline JSON document produced by the browser-side
 * editor or by another instance of this application.
 *
 * The parsing layer produces this value before any persistence happens, so
 * the UI can decide whether to:
 *
 * - persist immediately ([Success]);
 * - prompt the user with a compatibility warning before persisting
 *   ([SchemaMismatch]) — the file was emitted by a different editor
 *   version, so individual fields may not round-trip cleanly;
 * - surface an error and abort ([Failure]) for malformed JSON or
 *   structurally invalid pipelines.
 *
 * @see app.knotwork.android.domain.pipelineio.PipelineJsonSerializer
 * @see app.knotwork.android.domain.usecases.ImportPipelineUseCase
 */
sealed class PipelineImportOutcome {

    /**
     * The JSON parsed cleanly and the schema version matches what this
     * application expects. The graph is ready to be persisted as-is.
     *
     * @property graph Fully parsed pipeline ready for `SavePipelineUseCase`.
     */
    data class Success(val graph: PipelineGraph) : PipelineImportOutcome()

    /**
     * The JSON parsed but the `schemaVersion` field does not match the
     * version this build understands. The graph is still produced on a
     * best-effort basis (unknown fields are dropped), but the UI should
     * surface a warning before persisting because some configuration may
     * have been lost.
     *
     * @property graph Best-effort parsed graph.
     * @property foundVersion The `schemaVersion` value read from the file.
     * @property expectedVersion The version this build expects.
     */
    data class SchemaMismatch(val graph: PipelineGraph, val foundVersion: Int, val expectedVersion: Int) :
        PipelineImportOutcome()

    /**
     * Parsing failed irrecoverably (malformed JSON, missing required
     * fields, unknown `NodeType`, etc.). [message] contains a
     * human-readable description suitable for the UI.
     */
    data class Failure(val message: String) : PipelineImportOutcome()
}
