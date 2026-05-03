package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.PipelineDao
import ai.agent.android.data.local.models.ConnectionEntity
import ai.agent.android.data.local.models.NodeEntity
import ai.agent.android.data.local.models.PipelineEntity
import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.repositories.PipelineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Local Room-based implementation of [PipelineRepository].
 */
class LocalPipelineRepositoryImpl @Inject constructor(
    private val pipelineDao: PipelineDao
) : PipelineRepository {

    override fun getAllPipelines(): Flow<List<PipelineGraph>> {
        return pipelineDao.getAllPipelines().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getPipelineById(pipelineId: String): PipelineGraph? {
        return pipelineDao.getPipelineById(pipelineId)?.toDomainModel()
    }

    override suspend fun savePipeline(pipeline: PipelineGraph) {
        val pipelineEntity = PipelineEntity(
            id = pipeline.id,
            name = pipeline.name,
            updatedAt = System.currentTimeMillis()
        )
        val nodeEntities = pipeline.nodes.map {
            NodeEntity(
                id = it.id,
                pipelineId = pipeline.id,
                type = it.type.name,
                x = it.x,
                y = it.y,
                label = it.label,
                toolName = it.toolName,
                modelPath = it.modelPath,
                conditionComplexity = it.conditionComplexity,
                conditionKeywords = it.conditionKeywords,
                conditionPrompt = it.conditionPrompt,
                systemPrompt = it.systemPrompt,
                cloudProvider = it.cloudProvider,
                clarificationTimeoutMs = it.clarificationTimeoutMs,
            )
        }
        val connectionEntities = pipeline.connections.map {
            ConnectionEntity(
                id = it.id,
                pipelineId = pipeline.id,
                sourceNodeId = it.sourceNodeId,
                targetNodeId = it.targetNodeId,
                label = it.label
            )
        }

        pipelineDao.savePipelineTransaction(pipelineEntity, nodeEntities, connectionEntities)
    }

    override suspend fun deletePipeline(pipelineId: String) {
        pipelineDao.deletePipelineById(pipelineId)
    }

    private fun ai.agent.android.data.local.models.PipelineWithNodesAndConnections.toDomainModel(): PipelineGraph {
        return PipelineGraph(
            id = this.pipeline.id,
            name = this.pipeline.name,
            updatedAt = this.pipeline.updatedAt,
            nodes = this.nodes.map {
                NodeModel(
                    id = it.id,
                    type = runCatching { NodeType.valueOf(it.type) }.getOrDefault(NodeType.TOOL),
                    x = it.x,
                    y = it.y,
                    label = it.label,
                    toolName = it.toolName,
                    modelPath = it.modelPath,
                    conditionComplexity = it.conditionComplexity,
                    conditionKeywords = it.conditionKeywords,
                    conditionPrompt = it.conditionPrompt,
                    systemPrompt = it.systemPrompt ?: ai.agent.android.domain.constants.DefaultPrompts.getDefaultPromptForNodeType(runCatching { NodeType.valueOf(it.type) }.getOrDefault(NodeType.TOOL)),
                    cloudProvider = it.cloudProvider,
                    clarificationTimeoutMs = it.clarificationTimeoutMs,
                )
            },
            connections = this.connections.map {
                ConnectionModel(
                    id = it.id,
                    sourceNodeId = it.sourceNodeId,
                    targetNodeId = it.targetNodeId,
                    label = it.label
                )
            }
        )
    }
}
