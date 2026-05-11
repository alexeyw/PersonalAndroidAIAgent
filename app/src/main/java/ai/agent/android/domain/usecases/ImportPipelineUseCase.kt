package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.PipelineImportOutcome
import ai.agent.android.domain.pipelineio.PipelineJsonSerializer
import javax.inject.Inject

/**
 * Imports a pipeline from a JSON document produced by the browser-side
 * editor (`pipeline-editor.html`) or by another instance of this app.
 *
 * Two-step orchestration:
 *
 * 1. The JSON is parsed by [PipelineJsonSerializer.parse], which surfaces
 *    one of [PipelineImportOutcome.Success] / [PipelineImportOutcome.SchemaMismatch]
 *    / [PipelineImportOutcome.Failure].
 * 2. On a clean [PipelineImportOutcome.Success] this use case immediately
 *    persists the parsed graph through [SavePipelineUseCase] (which also
 *    runs the structural validator). On [PipelineImportOutcome.SchemaMismatch]
 *    we DO NOT persist automatically — the UI must show a warning dialog
 *    first; if the user agrees, the caller invokes [persistConfirmed]
 *    with the same graph to actually save it.
 *
 * Splitting parse from persist keeps the use case testable without a
 * fake `Activity` and lets the UI display a confirm-dialog before any
 * mutation hits the database.
 */
class ImportPipelineUseCase @Inject constructor(private val savePipelineUseCase: SavePipelineUseCase) {

    /**
     * Parses [jsonText] and, if it cleanly matches the current schema,
     * persists the resulting graph through [SavePipelineUseCase].
     *
     * For every other outcome (`SchemaMismatch` / `Failure`) no write
     * happens — see [persistConfirmed] for the second step of the
     * mismatch path.
     *
     * @return the [PipelineImportOutcome] that the UI should render. The
     * outcome is paired with a `saveResult` for `Success`: `null` when no
     * write was attempted, otherwise the [Result] of the save attempt
     * (which itself can carry a [ai.agent.android.domain.models.PipelineValidationException]
     * when the imported graph is structurally invalid).
     */
    suspend operator fun invoke(jsonText: String): ImportInvocation {
        val outcome = PipelineJsonSerializer.parse(jsonText)
        val saveResult = if (outcome is PipelineImportOutcome.Success) {
            savePipelineUseCase(outcome.graph)
        } else {
            null
        }
        return ImportInvocation(outcome = outcome, saveResult = saveResult)
    }

    /**
     * Persists [outcome.graph] after the user has explicitly accepted the
     * compatibility warning. No-op for any other outcome variant — the
     * caller should never reach this branch otherwise.
     */
    suspend fun persistConfirmed(outcome: PipelineImportOutcome.SchemaMismatch): Result<Unit> =
        savePipelineUseCase(outcome.graph)
}

/**
 * Aggregate carrying both the parse outcome and (when applicable) the
 * persistence result. Modelled as a `data class` so consumers can pattern
 * match on `outcome` and look at `saveResult` only when relevant.
 */
data class ImportInvocation(
    /** Parse outcome — drives the UI branching (Success / SchemaMismatch / Failure). */
    val outcome: PipelineImportOutcome,
    /**
     * Persistence result; non-null only for outcomes that the use case persisted
     * automatically (i.e. clean [PipelineImportOutcome.Success]). For
     * [PipelineImportOutcome.SchemaMismatch] persistence is deferred to
     * `persistConfirmed`, and for [PipelineImportOutcome.Failure] it never runs.
     */
    val saveResult: Result<Unit>?,
)
