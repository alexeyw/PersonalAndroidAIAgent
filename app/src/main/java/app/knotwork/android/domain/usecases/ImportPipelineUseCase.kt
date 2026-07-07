package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ImportCollisionResolution
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineImportOutcome
import app.knotwork.android.domain.pipelineio.PipelineBundleIdRemapper
import app.knotwork.android.domain.pipelineio.PipelineJsonSerializer
import app.knotwork.android.domain.repositories.PipelineRepository
import java.util.UUID
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
 * 2. On a clean [PipelineImportOutcome.Success] this use case checks whether
 *    the imported graph's id already names a saved pipeline. If it does **not**,
 *    the graph is persisted immediately through [SavePipelineUseCase]. If it
 *    **does**, nothing is written — [ImportInvocation.pendingCollision] carries
 *    the graph so the UI can prompt the user (Replace / Import as copy /
 *    Cancel) and then call [persistWithResolution]. This closes the previous
 *    silent-overwrite behaviour where a colliding id clobbered the existing
 *    pipeline without warning. On [PipelineImportOutcome.SchemaMismatch] we
 *    still defer to [persistConfirmed] after the compatibility warning.
 *
 * Splitting parse from persist keeps the use case testable without a
 * fake `Activity` and lets the UI display a confirm-dialog before any
 * mutation hits the database.
 */
class ImportPipelineUseCase @Inject constructor(
    private val savePipelineUseCase: SavePipelineUseCase,
    private val pipelineRepository: PipelineRepository,
) {

    /**
     * Parses [jsonText] and, if it cleanly matches the current schema and does
     * not collide with an existing pipeline id, persists the resulting graph
     * through [SavePipelineUseCase].
     *
     * For a colliding clean [PipelineImportOutcome.Success] no write happens —
     * the graph is returned in [ImportInvocation.pendingCollision] for the UI
     * to resolve. For every other outcome (`SchemaMismatch` / `Failure`) no
     * write happens either — see [persistConfirmed] for the mismatch path.
     *
     * @return the [ImportInvocation] the UI should render.
     */
    suspend operator fun invoke(jsonText: String): ImportInvocation {
        val outcome = PipelineJsonSerializer.parse(jsonText)
        if (outcome !is PipelineImportOutcome.Success) {
            return ImportInvocation(outcome = outcome, saveResult = null)
        }

        val collides = pipelineRepository.getPipelineById(outcome.graph.id) != null
        return if (collides) {
            ImportInvocation(outcome = outcome, saveResult = null, pendingCollision = outcome.graph)
        } else {
            ImportInvocation(outcome = outcome, saveResult = savePipelineUseCase(outcome.graph))
        }
    }

    /**
     * Persists [outcome.graph] after the user has explicitly accepted the
     * compatibility warning — unless its id collides with an existing pipeline,
     * in which case it returns [ConfirmedImport.Collision] so the UI can prompt
     * for Replace / Import-as-copy first (closing the silent-overwrite gap on
     * the schema-mismatch branch, not just the clean-success branch).
     *
     * @param outcome The schema-mismatch outcome the user confirmed.
     * @return [ConfirmedImport.Saved] with the save result when the id is free,
     *   or [ConfirmedImport.Collision] carrying the graph when it collides.
     */
    suspend fun persistConfirmed(outcome: PipelineImportOutcome.SchemaMismatch): ConfirmedImport =
        if (pipelineRepository.getPipelineById(outcome.graph.id) != null) {
            ConfirmedImport.Collision(outcome.graph)
        } else {
            ConfirmedImport.Saved(savePipelineUseCase(outcome.graph))
        }

    /**
     * Persists [graph] after the user has resolved an id collision.
     *
     * [ImportCollisionResolution.REPLACE] keeps the id and overwrites the
     * existing pipeline; [ImportCollisionResolution.IMPORT_AS_COPY] regenerates
     * the graph's ids through [PipelineBundleIdRemapper] so it is saved as a
     * fresh pipeline (references to *other* library pipelines are preserved,
     * since a single import carries no intra-bundle targets to remap).
     *
     * @param graph The graph captured in [ImportInvocation.pendingCollision].
     * @param resolution The user's choice.
     * @return The [Result] of the save attempt.
     */
    suspend fun persistWithResolution(graph: PipelineGraph, resolution: ImportCollisionResolution): Result<Unit> {
        val toSave = when (resolution) {
            ImportCollisionResolution.REPLACE -> graph
            ImportCollisionResolution.IMPORT_AS_COPY ->
                PipelineBundleIdRemapper.regenerate(listOf(graph)) { UUID.randomUUID().toString() }.first()
        }
        return savePipelineUseCase(toSave)
    }
}

/**
 * Result of confirming a schema-mismatch import ([ImportPipelineUseCase.persistConfirmed]).
 */
sealed class ConfirmedImport {

    /**
     * The confirmed graph's id was free, so it was persisted.
     *
     * @property result The save attempt's result.
     */
    data class Saved(val result: Result<Unit>) : ConfirmedImport()

    /**
     * The confirmed graph's id collides with an existing pipeline; nothing was
     * written. The UI must resolve the collision (Replace / Import as copy).
     *
     * @property graph The parsed graph awaiting collision resolution.
     */
    data class Collision(val graph: PipelineGraph) : ConfirmedImport()
}

/**
 * Aggregate carrying both the parse outcome and (when applicable) the
 * persistence result. Modelled as a `data class` so consumers can pattern
 * match on `outcome` and look at `saveResult` / `pendingCollision` only when
 * relevant.
 */
data class ImportInvocation(
    /** Parse outcome — drives the UI branching (Success / SchemaMismatch / Failure). */
    val outcome: PipelineImportOutcome,
    /**
     * Persistence result; non-null only for outcomes that the use case persisted
     * automatically (i.e. a clean [PipelineImportOutcome.Success] whose id did
     * not collide). For [PipelineImportOutcome.SchemaMismatch] persistence is
     * deferred to `persistConfirmed`, for a colliding success it is deferred to
     * `persistWithResolution`, and for [PipelineImportOutcome.Failure] it never
     * runs.
     */
    val saveResult: Result<Unit>?,
    /**
     * Set only when the parse succeeded cleanly but the graph's id already
     * names a saved pipeline. The UI must prompt the user for a collision
     * resolution and then call
     * [ImportPipelineUseCase.persistWithResolution]; nothing has been written
     * yet.
     */
    val pendingCollision: PipelineGraph? = null,
)
