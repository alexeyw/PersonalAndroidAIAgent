package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.PipelineDao
import app.knotwork.android.data.local.models.ConnectionEntity
import app.knotwork.android.data.local.models.NodeEntity
import app.knotwork.android.data.local.models.PipelineEntity
import app.knotwork.android.data.local.models.PipelineWithNodesAndConnections
import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Local Room-based implementation of [PipelineRepository].
 */
class LocalPipelineRepositoryImpl @Inject constructor(private val pipelineDao: PipelineDao) : PipelineRepository {

    override fun getAllPipelines(): Flow<List<PipelineGraph>> = pipelineDao.getAllPipelines().map { entities ->
        entities.map { it.toDomainModel() }
    }

    override suspend fun getPipelineById(pipelineId: String): PipelineGraph? =
        pipelineDao.getPipelineById(pipelineId)?.toDomainModel()

    override suspend fun savePipeline(pipeline: PipelineGraph) {
        val pipelineEntity = PipelineEntity(
            id = pipeline.id,
            name = pipeline.name,
            updatedAt = System.currentTimeMillis(),
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
                contextConfig = it.contextConfig,
                configJson = it.configJson,
            )
        }
        val connectionEntities = pipeline.connections.map {
            ConnectionEntity(
                id = it.id,
                pipelineId = pipeline.id,
                sourceNodeId = it.sourceNodeId,
                targetNodeId = it.targetNodeId,
                label = it.label,
            )
        }

        pipelineDao.savePipelineTransaction(pipelineEntity, nodeEntities, connectionEntities)
    }

    override suspend fun deletePipeline(pipelineId: String) {
        pipelineDao.deletePipelineById(pipelineId)
    }

    private fun PipelineWithNodesAndConnections.toDomainModel(): PipelineGraph = PipelineGraph(
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
                systemPrompt =
                it.systemPrompt
                    ?: DefaultPrompts.getDefaultPromptForNodeType(
                        runCatching {
                            NodeType.valueOf(it.type)
                        }.getOrDefault(NodeType.TOOL),
                    ),
                cloudProvider = it.cloudProvider,
                clarificationTimeoutMs = it.clarificationTimeoutMs,
                contextConfig = it.contextConfig,
                configJson = it.configJson,
            )
        },
        connections = this.connections.map {
            ConnectionModel(
                id = it.id,
                sourceNodeId = it.sourceNodeId,
                targetNodeId = it.targetNodeId,
                label = it.label,
            )
        },
    )
}
