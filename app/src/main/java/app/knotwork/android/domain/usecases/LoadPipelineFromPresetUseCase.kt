package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineValidationException
import app.knotwork.android.domain.repositories.PipelinePresetRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject

/**
 * Materialises a [app.knotwork.android.domain.models.PipelinePreset] into a
 * concrete, persistable [PipelineGraph].
 *
 * The preset's embedded graph is a *template*: its `id` and every node /
 * connection id are placeholders that must be regenerated on instantiation
 * so the new pipeline can coexist with the source preset in the library
 * without primary-key collisions. The deep-copy logic mirrors
 * [DuplicatePipelineUseCase]:
 *
 * - Fresh UUIDs for the pipeline, every node, and every connection.
 * - Connection `sourceNodeId` / `targetNodeId` re-mapped to the new node ids.
 * - Orphan connections (referencing node ids not present in `template.nodes`)
 *   are silently dropped rather than crashing the load — a corrupt preset
 *   should not block the user from getting at least the well-formed parts.
 *
 * The instantiated graph is validated through [PipelineGraph.validate] and
 * persisted via [PipelineRepository.savePipeline]; the new pipeline id is
 * returned so the caller can immediately load it as the active pipeline in
 * the editor.
 *
 * @property pipelinePresetRepository Source of the preset catalogue.
 * @property pipelineRepository Persistence sink for the instantiated pipeline.
 */
class LoadPipelineFromPresetUseCase @Inject constructor(
    private val pipelinePresetRepository: PipelinePresetRepository,
    private val pipelineRepository: PipelineRepository,
) {
    /**
     * Instantiates the preset identified by [presetId] and persists the
     * resulting [PipelineGraph].
     *
     * @param presetId The stable id of the preset to instantiate.
     * @return [Result.success] holding the id of the newly persisted
     *   pipeline, or [Result.failure] when the preset is missing, the
     *   template fails [PipelineGraph.validate], or the persistence layer
     *   throws.
     */
    suspend operator fun invoke(presetId: String): Result<String> = try {
        val pipeline = materialize(presetId).getOrElse { return Result.failure(it) }
        pipelineRepository.savePipeline(pipeline)
        Result.success(pipeline.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Instantiates the preset identified by [presetId] into a fresh,
     * validated [PipelineGraph] **without persisting it**.
     *
     * Used by surfaces that fill an *existing* pipeline from a template (the
     * editor's empty-state "From template" CTA) rather than spawning a new
     * library row — the caller decides where the regenerated graph's nodes /
     * connections land. [invoke] builds on this and adds the persistence step.
     *
     * @param presetId The stable id of the preset to instantiate.
     * @return [Result.success] with the regenerated graph (new pipeline / node
     *   / connection ids, validated), or [Result.failure] when the preset is
     *   missing or the template fails [PipelineGraph.validate].
     */
    suspend fun materialize(presetId: String): Result<PipelineGraph> = try {
        val preset = pipelinePresetRepository.getPresetById(presetId)
            ?: return Result.failure(IllegalStateException("Preset not found: $presetId"))

        val template = preset.graph
        val nodeIdMapping: Map<String, String> =
            template.nodes.associate { it.id to UUID.randomUUID().toString() }

        val instantiatedNodes = template.nodes.map { node ->
            node.copy(id = nodeIdMapping.getValue(node.id))
        }

        val instantiatedConnections = template.connections.mapNotNull { connection ->
            val newSource = nodeIdMapping[connection.sourceNodeId]
            val newTarget = nodeIdMapping[connection.targetNodeId]
            if (newSource == null || newTarget == null) {
                null
            } else {
                connection.copy(
                    id = UUID.randomUUID().toString(),
                    sourceNodeId = newSource,
                    targetNodeId = newTarget,
                )
            }
        }

        val pipeline = PipelineGraph(
            id = UUID.randomUUID().toString(),
            name = truncateName(preset.name),
            nodes = instantiatedNodes,
            connections = instantiatedConnections,
            updatedAt = System.currentTimeMillis(),
        )

        val errors = pipeline.validate()
        if (errors.isNotEmpty()) {
            Result.failure(PipelineValidationException(errors))
        } else {
            Result.success(pipeline)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Clamps [name] to [MAX_NAME_LENGTH] characters, trimming trailing
     * whitespace so a truncated name does not leak a stray space. Mirrors
     * the limit enforced by [CreatePipelineUseCase] /
     * [RenamePipelineUseCase] / [DuplicatePipelineUseCase] so the
     * instantiated pipeline can always be subsequently renamed without
     * violating the gate.
     */
    private fun truncateName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.length <= MAX_NAME_LENGTH) return trimmed
        return trimmed.substring(0, MAX_NAME_LENGTH).trimEnd()
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
    }
}
