package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.PipelineGraph

/**
 * Represents the UI state for the Visual Orchestrator screen.
 *
 * @property currentPipeline The pipeline graph currently being edited.
 * @property savedPipelines List of all saved pipelines available to load.
 * @property isLoading Whether a loading operation is currently in progress.
 * @property errorMessage An error message if an operation fails.
 */
data class OrchestratorUiState(
    val currentPipeline: PipelineGraph = PipelineGraph(id = java.util.UUID.randomUUID().toString(), name = "New Pipeline"),
    val savedPipelines: List<PipelineGraph> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
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
