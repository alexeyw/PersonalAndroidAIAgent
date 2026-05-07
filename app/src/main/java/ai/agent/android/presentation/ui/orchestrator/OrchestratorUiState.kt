package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineImportOutcome
import ai.agent.android.domain.models.PipelineValidationError
import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.prompt.PromptSegment

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
 * @property availableVariables Tokens (`$KEY`) of every prompt variable currently
 * registered in the DI graph. Drives the chip row in the prompt editor.
 * @property previewState Current state of the prompt-preview bottom sheet.
 * @property pendingImport A schema-mismatch outcome awaiting user
 * confirmation before being persisted. `null` when no import is pending.
 */
data class OrchestratorUiState(
    val currentPipeline: PipelineGraph = PipelineGraph(id = java.util.UUID.randomUUID().toString(), name = "New Pipeline"),
    val savedPipelines: List<PipelineGraph> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val availableTools: List<AgentTool> = emptyList(),
    val providerKeys: Map<String, Boolean> = emptyMap(),
    val promptTemplates: List<PromptTemplate> = emptyList(),
    val availableVariables: List<String> = emptyList(),
    val previewState: PromptPreviewState = PromptPreviewState.Hidden,
    val pendingImport: PipelineImportOutcome.SchemaMismatch? = null,
) {
    /**
     * Helper to get nodes easily.
     */
    val nodes: List<NodeModel> get() = currentPipeline.nodes

    /**
     * Helper to get connections easily.
     */
    val connections: List<ConnectionModel> get() = currentPipeline.connections

    /**
     * Dynamically computed list of validation errors for the current pipeline.
     */
    val validationErrors: List<PipelineValidationError> get() = currentPipeline.validate()
}

/**
 * State of the prompt-preview bottom sheet shared by every editor that supports the
 * `$VARIABLE` chip row.
 *
 * The state is hoisted to the ViewModel so the segments survive configuration changes
 * (rotation) and so prompt resolution — which may suspend on I/O — is performed off the
 * main thread.
 */
sealed interface PromptPreviewState {

    /** Sheet is closed, no preview is being computed. */
    data object Hidden : PromptPreviewState

    /** A preview was requested and the engine is currently rendering segments. */
    data object Loading : PromptPreviewState

    /**
     * Segments have been produced and the sheet should be shown. [segments] is the
     * ordered output of `PromptTemplateEngine.renderSegments`.
     */
    data class Ready(val segments: List<PromptSegment>) : PromptPreviewState
}
