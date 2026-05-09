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
        val source = pipelineRepository.getPipelineById(pipelineId)
            ?: return Result.failure(IllegalStateException("Pipeline not found"))

        val nodeIdMapping: Map<String, String> = source.nodes.associate { it.id to UUID.randomUUID().toString() }
        val duplicatedNodes = source.nodes.map { node ->
            node.copy(id = nodeIdMapping.getValue(node.id))
        }
        val duplicatedConnections = source.connections.map { connection ->
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = nodeIdMapping.getValue(connection.sourceNodeId),
                targetNodeId = nodeIdMapping.getValue(connection.targetNodeId),
                label = connection.label,
            )
        }

        val duplicate = PipelineGraph(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (copy)",
            nodes = duplicatedNodes,
            connections = duplicatedConnections,
            updatedAt = System.currentTimeMillis(),
        )

        return try {
            pipelineRepository.savePipeline(duplicate)
            Result.success(duplicate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
