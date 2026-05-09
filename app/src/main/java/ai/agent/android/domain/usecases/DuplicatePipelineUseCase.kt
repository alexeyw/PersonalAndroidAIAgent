package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.repositories.PipelineRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for duplicating an existing pipeline.
 *
 * Produces a deep copy of the source graph with fresh identifiers (pipeline,
 * every node, every connection) so the duplicate can coexist with its source
 * in the library without primary-key collisions on the Room side. Connection
 * source / target references are remapped to the new node ids.
 *
 * The duplicate's name is the source name suffixed with `" (copy)"`. We keep
 * the suffix even when the source name already ends with `(copy)` — repeated
 * duplication then yields `"X (copy) (copy)"`, which is intentional: it
 * surfaces accidental double-clicks instead of silently collapsing them into a
 * single name shared by multiple pipelines.
 *
 * @property pipelineRepository Source of the original graph and the persistence sink.
 */
class DuplicatePipelineUseCase @Inject constructor(
    private val pipelineRepository: PipelineRepository,
) {
    /**
     * Duplicates the pipeline identified by [pipelineId].
     *
     * @param pipelineId Unique identifier of the source pipeline.
     * @return [Result.success] holding the persisted duplicate (so the caller
     * can immediately load it as the active pipeline), or [Result.failure] if
     * the source is missing or the save fails.
     */
    suspend operator fun invoke(pipelineId: String): Result<PipelineGraph> {
        // Wrap the entire pipeline (read + transform + save) so any unexpected
        // throwable — including a malformed source graph that references node
        // ids no longer present in `source.nodes` — surfaces as `Result.failure`
        // instead of escaping the use-case boundary as an uncaught exception.
        return try {
            val source = pipelineRepository.getPipelineById(pipelineId)
                ?: return Result.failure(IllegalStateException("Pipeline not found"))

            val nodeIdMapping: Map<String, String> =
                source.nodes.associate { it.id to UUID.randomUUID().toString() }

            val duplicatedNodes = source.nodes.map { node ->
                node.copy(id = nodeIdMapping.getValue(node.id))
            }

            // Drop connections that reference node ids missing from
            // `source.nodes` rather than crashing the duplicate. A dangling
            // connection in the source graph is already broken; producing a
            // duplicate that silently inherits the corruption (or worse,
            // crashes the action) would be a regression. We keep the rest of
            // the graph intact.
            val duplicatedConnections = source.connections.mapNotNull { connection ->
                val newSource = nodeIdMapping[connection.sourceNodeId]
                val newTarget = nodeIdMapping[connection.targetNodeId]
                if (newSource == null || newTarget == null) {
                    null
                } else {
                    ConnectionModel(
                        id = UUID.randomUUID().toString(),
                        sourceNodeId = newSource,
                        targetNodeId = newTarget,
                        label = connection.label,
                    )
                }
            }

            val duplicate = PipelineGraph(
                id = UUID.randomUUID().toString(),
                name = "${source.name} (copy)",
                nodes = duplicatedNodes,
                connections = duplicatedConnections,
                updatedAt = System.currentTimeMillis(),
            )

            pipelineRepository.savePipeline(duplicate)
            Result.success(duplicate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
