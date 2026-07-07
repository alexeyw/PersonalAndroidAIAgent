package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ImportCollisionResolution
import app.knotwork.android.domain.models.PipelineBundleImportOutcome
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineValidationException
import app.knotwork.android.domain.pipelineio.PipelineBundleIdRemapper
import app.knotwork.android.domain.pipelineio.PipelineBundleJsonSerializer
import app.knotwork.android.domain.repositories.PipelineRepository
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject

/**
 * Imports a pipeline **bundle** — a self-contained closure of a root pipeline
 * and every sub-pipeline it references — produced by
 * [ExportPipelineBundleUseCase] or the browser editor.
 *
 * Orchestrated in two steps so the UI can prompt on id collisions before any
 * write touches the database, mirroring the parse/confirm split of
 * [ImportPipelineUseCase]:
 *
 * 1. [prepare] parses the file (delegating to
 *    [PipelineBundleJsonSerializer.parse]), validates every graph
 *    structurally, and detects which pipeline ids already exist in the
 *    library. It performs no write.
 * 2. [persist] applies the chosen [ImportCollisionResolution] and writes the
 *    whole closure **atomically** — a single structurally-broken graph rolls
 *    back the entire import.
 */
class ImportPipelineBundleUseCase @Inject constructor(private val pipelineRepository: PipelineRepository) {

    /**
     * Parses [jsonText], validates each contained graph, and reports which ids
     * collide with the existing library — without persisting anything.
     *
     * @param jsonText Raw bundle JSON.
     * @return [PipelineBundlePrepareResult.Ready] with the pipelines, the
     *   colliding ids, and any schema mismatches; or
     *   [PipelineBundlePrepareResult.Failure] when the file cannot be parsed or
     *   a contained graph is structurally invalid.
     */
    suspend fun prepare(jsonText: String): PipelineBundlePrepareResult {
        val (pipelines, mismatches) = when (val outcome = PipelineBundleJsonSerializer.parse(jsonText)) {
            is PipelineBundleImportOutcome.Failure ->
                return PipelineBundlePrepareResult.Failure(outcome.message)

            is PipelineBundleImportOutcome.Success -> outcome.pipelines to emptyList()

            is PipelineBundleImportOutcome.PartialSchemaMismatch -> outcome.pipelines to outcome.mismatches
        }

        // Per-graph structural validation. One broken graph aborts the whole
        // import (atomic, observable) rather than writing a partial closure.
        pipelines.firstNotNullOfOrNull { graph ->
            graph.validate().takeIf { it.isNotEmpty() }?.let { graph to it }
        }?.let { (graph, errors) ->
            return PipelineBundlePrepareResult.Failure(
                "Pipeline \"${graph.name}\" is invalid: ${PipelineValidationException(errors).message}",
            )
        }

        val collidingIds = pipelines.map { it.id }
            .filter { pipelineRepository.getPipelineById(it) != null }

        return PipelineBundlePrepareResult.Ready(
            pipelines = pipelines,
            collidingIds = collidingIds,
            schemaMismatches = mismatches,
        )
    }

    /**
     * Atomically persists [pipelines] under the chosen [resolution].
     *
     * [ImportCollisionResolution.REPLACE] keeps the ids and overwrites in
     * place; [ImportCollisionResolution.IMPORT_AS_COPY] regenerates every id
     * and remaps intra-bundle references through [PipelineBundleIdRemapper]
     * before saving, leaving existing pipelines untouched.
     *
     * @param pipelines The closure to persist (as produced by [prepare]).
     * @param resolution How to treat ids that collide with the library.
     * @return [Result.success] with the number of pipelines written, or
     *   [Result.failure] if the atomic save fails.
     */
    suspend fun persist(pipelines: List<PipelineGraph>, resolution: ImportCollisionResolution): Result<Int> {
        val toSave = when (resolution) {
            ImportCollisionResolution.REPLACE -> pipelines
            ImportCollisionResolution.IMPORT_AS_COPY ->
                PipelineBundleIdRemapper.regenerate(pipelines) { UUID.randomUUID().toString() }
        }

        return try {
            pipelineRepository.savePipelines(toSave)
            Result.success(toSave.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Result of the non-persisting [ImportPipelineBundleUseCase.prepare] step.
 */
sealed class PipelineBundlePrepareResult {

    /**
     * The bundle parsed and every graph validated; the import can proceed once
     * the caller picks a collision resolution.
     *
     * @property pipelines The pipelines to persist, in file order.
     * @property collidingIds The subset of pipeline ids that already exist in
     *   the library. Empty means no dialog is needed and the import may persist
     *   straight away with [ImportCollisionResolution.REPLACE].
     * @property schemaMismatches Per-pipeline schema-version divergences to
     *   surface as a compatibility warning, if any.
     */
    data class Ready(
        val pipelines: List<PipelineGraph>,
        val collidingIds: List<String>,
        val schemaMismatches: List<PipelineBundleImportOutcome.SchemaMismatch>,
    ) : PipelineBundlePrepareResult()

    /**
     * The bundle could not be parsed, or a contained graph was structurally
     * invalid. Nothing was written.
     *
     * @property message Human-readable failure description for the UI.
     */
    data class Failure(val message: String) : PipelineBundlePrepareResult()
}
