package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PromptTemplate

/**
 * Represents the UI state for the Visual Orchestrator screen.
 *
 * @property currentPipeline The pipeline graph currently being edited.
 * @property savedPipelines List of all saved pipelines available to load.
 * @property isLoading Whether a loading operation is currently in progress.
 * @property errorMessage An error message if an operation fails.
 * @property availableTools List of all available tools in the system.
 * @property providerKeys Map indicating whether an API key is set for specific provider node types.
 * @property promptTemplates List of saved prompt templates.
 */
data class OrchestratorUiState(
    val currentPipeline: PipelineGraph = PipelineGraph(id = java.util.UUID.randomUUID().toString(), name = "New Pipeline"),
    val savedPipelines: List<PipelineGraph> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val availableTools: List<AgentTool> = emptyList(),
    val providerKeys: Map<String, Boolean> = emptyMap(),
    val promptTemplates: List<PromptTemplate> = emptyList()
) {
    /**
     * Helper to get nodes easily.
     */
    val nodes: List<NodeModel> get() = currentPipeline.nodes

    /**
     * Helper to get connections easily.
     */
    val connections: List<ConnectionModel> get() = currentPipeline.connections
}
