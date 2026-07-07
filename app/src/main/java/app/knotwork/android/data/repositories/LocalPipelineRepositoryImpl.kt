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

    override fun observePipelineNames(): Flow<Map<String, String>> =
        pipelineDao.observePipelineNames().map { rows -> rows.associate { it.id to it.name } }

    override suspend fun getPipelineById(pipelineId: String): PipelineGraph? =
        pipelineDao.getPipelineById(pipelineId)?.toDomainModel()

    override suspend fun savePipeline(pipeline: PipelineGraph) {
        val timestamp = System.currentTimeMillis()
        pipelineDao.savePipelineTransaction(
            pipeline = pipeline.toPipelineEntity(timestamp),
            nodes = pipeline.toNodeEntities(),
            connections = pipeline.toConnectionEntities(),
        )
    }

    override suspend fun savePipelines(pipelines: List<PipelineGraph>) {
        val timestamp = System.currentTimeMillis()
        pipelineDao.savePipelinesTransaction(
            pipelines = pipelines.map { it.toPipelineEntity(timestamp) },
            nodes = pipelines.flatMap { it.toNodeEntities() },
            connections = pipelines.flatMap { it.toConnectionEntities() },
        )
    }

    override suspend fun deletePipeline(pipelineId: String) {
        pipelineDao.deletePipelineById(pipelineId)
    }

    private fun PipelineGraph.toPipelineEntity(updatedAt: Long): PipelineEntity =
        PipelineEntity(id = id, name = name, updatedAt = updatedAt)

    private fun PipelineGraph.toNodeEntities(): List<NodeEntity> = nodes.map {
        NodeEntity(
            id = it.id,
            pipelineId = id,
            type = it.type.name,
            x = it.x,
            y = it.y,
            label = it.label,
            toolName = it.toolName,
            targetPipelineId = it.targetPipelineId,
            skillId = it.skillId,
            modelPath = it.modelPath,
            conditionComplexity = it.conditionComplexity,
            conditionKeywords = it.conditionKeywords,
            conditionPrompt = it.conditionPrompt,
            conditionHasImage = it.conditionHasImage,
            systemPrompt = it.systemPrompt,
            cloudProvider = it.cloudProvider,
            clarificationTimeoutMs = it.clarificationTimeoutMs,
            contextConfig = it.contextConfig,
            configJson = it.configJson,
        )
    }

    private fun PipelineGraph.toConnectionEntities(): List<ConnectionEntity> = connections.map {
        ConnectionEntity(
            id = it.id,
            pipelineId = id,
            sourceNodeId = it.sourceNodeId,
            targetNodeId = it.targetNodeId,
            label = it.label,
        )
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
                targetPipelineId = it.targetPipelineId,
                skillId = it.skillId,
                modelPath = it.modelPath,
                conditionComplexity = it.conditionComplexity,
                conditionKeywords = it.conditionKeywords,
                conditionPrompt = it.conditionPrompt,
                conditionHasImage = it.conditionHasImage,
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
