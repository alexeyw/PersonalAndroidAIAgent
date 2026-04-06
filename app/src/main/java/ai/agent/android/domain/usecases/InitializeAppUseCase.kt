package ai.agent.android.domain.usecases

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.models.*
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/**
 * Use case executed when the application is launched.
 * It checks if this is the first launch, and if so, initializes default settings,
 * such as saving the default system prompts to the settings repository,
 * and creates a default complex execution pipeline.
 */
class InitializeAppUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pipelineRepository: PipelineRepository
) {
    /**
     * Executes the initialization logic.
     */
    suspend operator fun invoke() {
        val isFirstLaunch = settingsRepository.isFirstLaunch.first()
        
        if (isFirstLaunch) {
            // Save default prompts to settings so they can be modified later by the user
            settingsRepository.setSystemPromptPrefix(DefaultPrompts.SYSTEM_PROMPT_PREFIX)
            settingsRepository.setToolUsageInstruction(DefaultPrompts.TOOL_USAGE_INSTRUCTION)
            
            // Generate and save the default complex pipeline
            val defaultPipeline = createDefaultPipeline()
            pipelineRepository.savePipeline(defaultPipeline)

            // Mark first launch as complete
            settingsRepository.setFirstLaunch(false)
        }
    }

    private fun createDefaultPipeline(): PipelineGraph {
        val inputNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.INPUT, label = "Input", x = 100f, y = 300f)
        val intentRouterNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.INTENT_ROUTER, label = "Classifier", x = 300f, y = 300f)
        
        // Simple Branch
        val liteRtNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.LITE_RT, label = "Local LLM", x = 500f, y = 100f)
        
        // Data Branch
        val searchToolNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.TOOL, label = "Search Tool", toolName = "search_tool", x = 500f, y = 250f)
        
        // Complex Branch
        val cloudNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.GOOGLE, label = "Google API", x = 500f, y = 400f)
        
        // Task Branch
        val decompositionNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.DECOMPOSITION, label = "Decompose", x = 500f, y = 550f)
        val queueProcessorNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.QUEUE_PROCESSOR, label = "Queue", x = 700f, y = 550f)
        val taskToolNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.LITE_RT, label = "Execute Subtask", x = 900f, y = 550f)
        val summaryNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.SUMMARY, label = "Summary", x = 1100f, y = 550f)

        val outputNode = NodeModel(id = UUID.randomUUID().toString(), type = NodeType.OUTPUT, label = "Output", x = 1300f, y = 300f)

        val nodes = listOf(inputNode, intentRouterNode, liteRtNode, searchToolNode, cloudNode, decompositionNode, queueProcessorNode, taskToolNode, summaryNode, outputNode)
        
        val connections = listOf(
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = inputNode.id, targetNodeId = intentRouterNode.id),
            
            // Routing edges
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = intentRouterNode.id, targetNodeId = liteRtNode.id, label = "Simple"),
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = intentRouterNode.id, targetNodeId = searchToolNode.id, label = "Data"),
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = intentRouterNode.id, targetNodeId = cloudNode.id, label = "Complex"),
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = intentRouterNode.id, targetNodeId = decompositionNode.id, label = "Task"),
            
            // Simple Path
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = liteRtNode.id, targetNodeId = outputNode.id),
            
            // Data Path
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = searchToolNode.id, targetNodeId = outputNode.id),
            
            // Complex Path
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = cloudNode.id, targetNodeId = outputNode.id),
            
            // Task Path
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = decompositionNode.id, targetNodeId = queueProcessorNode.id),
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = queueProcessorNode.id, targetNodeId = taskToolNode.id),
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = taskToolNode.id, targetNodeId = summaryNode.id),
            ConnectionModel(id = UUID.randomUUID().toString(), sourceNodeId = summaryNode.id, targetNodeId = outputNode.id)
        )

        return PipelineGraph(
            id = UUID.randomUUID().toString(),
            name = "Default System Pipeline",
            updatedAt = System.currentTimeMillis(),
            nodes = nodes,
            connections = connections
        )
    }
}
