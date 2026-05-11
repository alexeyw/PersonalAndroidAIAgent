package ai.agent.android.domain.engine

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.models.CloudProvider
import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import java.util.UUID

/**
 * Factory for creating default pipelines.
 */
object DefaultPipelineFactory {

    /**
     * Creates the default complex execution pipeline.
     *
     * @param name The name of the pipeline.
     * @return A new instance of PipelineGraph containing the default nodes and connections.
     */
    // Reason: declarative graph construction — 10 nodes + 12 connections each
    // built as one builder-style expression. Splitting into helpers would
    // hurt readability more than the length cost.
    @Suppress("LongMethod")
    fun create(name: String = "Default System Pipeline"): PipelineGraph {
        val inputNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.INPUT,
            label = "Input",
            x = 100f,
            y = 300f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.INPUT),
        )
        val intentRouterNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.INTENT_ROUTER,
            label = "Classifier",
            x = 300f,
            y = 300f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.INTENT_ROUTER),
        )

        // Simple Branch
        val liteRtNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.LITE_RT,
            label = "Local LLM",
            x = 500f,
            y = 100f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.LITE_RT),
        )

        // Data Branch
        val searchToolNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.TOOL,
            label = "Search Tool",
            toolName = "search_tool",
            x = 500f,
            y = 250f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.TOOL),
        )

        // Complex Branch
        val cloudNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.CLOUD,
            label = "Cloud API",
            cloudProvider = CloudProvider.AUTO_KEY,
            x = 500f,
            y = 400f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.CLOUD),
        )

        // Task Branch
        val decompositionNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.DECOMPOSITION,
            label = "Decompose",
            x = 500f,
            y = 550f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.DECOMPOSITION),
        )
        val queueProcessorNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.QUEUE_PROCESSOR,
            label = "Queue",
            x = 700f,
            y = 550f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.QUEUE_PROCESSOR),
        )
        val taskToolNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.LITE_RT,
            label = "Execute Subtask",
            x = 900f,
            y = 550f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.LITE_RT),
        )
        val summaryNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.SUMMARY,
            label = "Summary",
            x = 1100f,
            y = 550f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.SUMMARY),
        )

        val outputNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.OUTPUT,
            label = "Output",
            systemPrompt = DefaultPrompts.OUTPUT_FORMAT_PROMPT,
            x = 1300f,
            y = 300f,
            contextConfig = NodeContextConfig.defaultForType(NodeType.OUTPUT),
        )

        val nodes = listOf(
            inputNode,
            intentRouterNode,
            liteRtNode,
            searchToolNode,
            cloudNode,
            decompositionNode,
            queueProcessorNode,
            taskToolNode,
            summaryNode,
            outputNode,
        )

        val connections = listOf(
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = inputNode.id,
                targetNodeId = intentRouterNode.id,
            ),

            // Routing edges
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = intentRouterNode.id,
                targetNodeId = liteRtNode.id,
                label = "Simple",
            ),
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = intentRouterNode.id,
                targetNodeId = searchToolNode.id,
                label = "Data",
            ),
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = intentRouterNode.id,
                targetNodeId = cloudNode.id,
                label = "Complex",
            ),
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = intentRouterNode.id,
                targetNodeId = decompositionNode.id,
                label = "Task",
            ),

            // Simple Path
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = liteRtNode.id,
                targetNodeId = outputNode.id,
            ),

            // Data Path
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = searchToolNode.id,
                targetNodeId = outputNode.id,
            ),

            // Complex Path
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = cloudNode.id,
                targetNodeId = outputNode.id,
            ),

            // Task Path
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = decompositionNode.id,
                targetNodeId = queueProcessorNode.id,
            ),
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = queueProcessorNode.id,
                targetNodeId = taskToolNode.id,
            ),
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = taskToolNode.id,
                targetNodeId = summaryNode.id,
            ),
            ConnectionModel(
                id = UUID.randomUUID().toString(),
                sourceNodeId = summaryNode.id,
                targetNodeId = outputNode.id,
            ),
        )

        return PipelineGraph(
            id = UUID.randomUUID().toString(),
            name = name,
            updatedAt = System.currentTimeMillis(),
            nodes = nodes,
            connections = connections,
        )
    }
}
