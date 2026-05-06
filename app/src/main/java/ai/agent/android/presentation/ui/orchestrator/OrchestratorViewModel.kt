package ai.agent.android.presentation.ui.orchestrator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.usecases.LoadPipelineUseCase
import ai.agent.android.domain.usecases.SavePipelineUseCase
import ai.agent.android.domain.usecases.GetPromptTemplatesUseCase
import ai.agent.android.domain.usecases.SavePromptTemplateUseCase
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Visual Orchestrator feature.
 * Manages the state of the infinite canvas and coordinates saving/loading pipelines.
 */
@HiltViewModel
class OrchestratorViewModel @Inject constructor(
    private val savePipelineUseCase: SavePipelineUseCase,
    private val loadPipelineUseCase: LoadPipelineUseCase,
    private val getPromptTemplatesUseCase: GetPromptTemplatesUseCase,
    private val savePromptTemplateUseCase: SavePromptTemplateUseCase,
    private val apiKeyRepository: ApiKeyRepository,
    private val toolRepository: ToolRepository,
    private val promptTemplateEngine: PromptTemplateEngine,
    private val promptVariableProviders: Set<@JvmSuppressWildcards PromptVariableProvider>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OrchestratorUiState(availableVariables = computeAvailableVariables(promptVariableProviders)),
    )
    
    /**
     * The current UI state of the Orchestrator screen.
     */
    val uiState: StateFlow<OrchestratorUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    init {
        observeSavedPipelines()
        observeProviderKeys()
        loadAvailableTools()
        observePromptTemplates()
    }

    private fun observePromptTemplates() {
        viewModelScope.launch {
            getPromptTemplatesUseCase()
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
                .collect { templates ->
                    _uiState.update { state -> 
                        state.copy(promptTemplates = templates) 
                    }
                }
        }
    }

    /**
     * Saves a new prompt template.
     * 
     * @param name The name of the prompt.
     * @param text The prompt content.
     * @param category The category corresponding to NodeType.
     */
    fun savePromptTemplate(name: String, text: String, category: String) {
        viewModelScope.launch {
            try {
                savePromptTemplateUseCase(
                    PromptTemplate(name = name, text = text, category = category)
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    private fun observeSavedPipelines() {
        viewModelScope.launch {
            loadPipelineUseCase.observeAllPipelines()
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
                .collect { pipelines ->
                    _uiState.update { state -> 
                        val newCurrent = if (state.currentPipeline.nodes.isEmpty() && pipelines.isNotEmpty()) {
                            pipelines.first()
                        } else {
                            state.currentPipeline
                        }
                        state.copy(savedPipelines = pipelines, currentPipeline = newCurrent) 
                    }
                }
        }
    }

    private fun observeProviderKeys() {
        viewModelScope.launch {
            apiKeyRepository.getOpenAIKey().collect { key ->
                updateProviderKey("openai", !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            apiKeyRepository.getAnthropicKey().collect { key ->
                updateProviderKey("anthropic", !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            apiKeyRepository.getGoogleKey().collect { key ->
                updateProviderKey("google", !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            apiKeyRepository.getDeepSeekKey().collect { key ->
                updateProviderKey("deepseek", !key.isNullOrBlank())
            }
        }
    }

    private fun updateProviderKey(provider: String, hasKey: Boolean) {
        _uiState.update { state ->
            val updatedKeys = state.providerKeys.toMutableMap()
            updatedKeys[provider] = hasKey
            state.copy(providerKeys = updatedKeys)
        }
    }

    private fun loadAvailableTools() {
        viewModelScope.launch {
            try {
                val tools = toolRepository.getAvailableTools()
                _uiState.update { it.copy(availableTools = tools) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /**
     * Adds a new node to the canvas at the specified coordinates.
     *
     * @param type The type of node to add.
     * @param x The x-coordinate for the node's position.
     * @param y The y-coordinate for the node's position.
     */
    fun addNode(type: NodeType, x: Float, y: Float) {
        val newNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = type,
            x = x,
            y = y,
            cloudProvider = if (type == NodeType.CLOUD) "auto" else null
        )
        _uiState.update { state ->
            val updatedPipeline = state.currentPipeline.copy(
                nodes = state.currentPipeline.nodes + newNode
            )
            state.copy(currentPipeline = updatedPipeline)
        }
    }

    /**
     * Moves an existing node by a delta amount.
     *
     * @param nodeId The unique identifier of the node to move.
     * @param deltaX The change in the x-coordinate.
     * @param deltaY The change in the y-coordinate.
     */
    fun moveNode(nodeId: String, deltaX: Float, deltaY: Float) {
        _uiState.update { state ->
            val updatedNodes = state.currentPipeline.nodes.map {
                if (it.id == nodeId) it.copy(x = it.x + deltaX, y = it.y + deltaY) else it
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes)
            )
        }
    }

    /**
     * Creates a connection between two nodes.
     *
     * @param sourceNodeId The unique identifier of the source node.
     * @param targetNodeId The unique identifier of the target node.
     * @param label Optional label for the connection.
     * @return The ID of the newly created connection, or null if it was not created (e.g. cycle).
     */
    fun addConnection(sourceNodeId: String, targetNodeId: String, label: String? = null): String? {
        val newConnection = ConnectionModel(
            id = UUID.randomUUID().toString(),
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId,
            label = label
        )
        var createdConnectionId: String? = null
        _uiState.update { state ->
            // Remove previous connection if it's between the same source and target, 
            // OR if it's from the same source with the same label (e.g. "True" / "False")
            val filteredConnections = state.currentPipeline.connections.filterNot { 
                (it.sourceNodeId == sourceNodeId && it.targetNodeId == targetNodeId) ||
                (it.sourceNodeId == sourceNodeId && it.label == label && label != null)
            }

            val tempPipeline = state.currentPipeline.copy(
                connections = filteredConnections + newConnection
            )

            // Validate DAG
            if (tempPipeline.isValidDAG()) {
                createdConnectionId = newConnection.id
                state.copy(currentPipeline = tempPipeline, errorMessage = null)
            } else {
                state.copy(errorMessage = "Cannot connect: Cycle detected")
            }
        }
        return createdConnectionId
    }

    /**
     * Updates the label of an existing connection.
     *
     * @param connectionId The unique identifier of the connection.
     * @param label The new label for the connection, or null to remove it.
     */
    fun updateConnectionLabel(connectionId: String, label: String?) {
        _uiState.update { state ->
            val updatedConnections = state.currentPipeline.connections.map {
                if (it.id == connectionId) it.copy(label = label) else it
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(connections = updatedConnections)
            )
        }
    }

    /**
     * Removes an existing connection.
     *
     * @param connectionId The unique identifier of the connection to remove.
     */
    fun removeConnection(connectionId: String) {
        _uiState.update { state ->
            val updatedConnections = state.currentPipeline.connections.filter {
                it.id != connectionId
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(connections = updatedConnections)
            )
        }
    }

    /**
     * Updates the condition configuration of an IF_CONDITION node and the system prompt for any node.
     *
     * @param nodeId The unique identifier of the node.
     * @param complexity Threshold for task complexity.
     * @param keywords Comma-separated keywords.
     * @param prompt Free-form prompt.
     * @param systemPrompt The system prompt configuring the behavior of the node.
     */
    fun updateNodeConfiguration(nodeId: String, complexity: Int?, keywords: String?, prompt: String?, systemPrompt: String?) {
        _uiState.update { state ->
            val updatedNodes = state.currentPipeline.nodes.map {
                if (it.id == nodeId) {
                    it.copy(
                        conditionComplexity = complexity,
                        conditionKeywords = keywords,
                        conditionPrompt = prompt,
                        systemPrompt = systemPrompt
                    )
                } else it
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes)
            )
        }
    }

    /**
     * Removes a node and any connections attached to it.
     *
     * @param nodeId The unique identifier of the node to remove.
     */
    fun removeNode(nodeId: String) {
        _uiState.update { state ->
            val updatedNodes = state.currentPipeline.nodes.filter { it.id != nodeId }
            val updatedConnections = state.currentPipeline.connections.filter {
                it.sourceNodeId != nodeId && it.targetNodeId != nodeId
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(
                    nodes = updatedNodes,
                    connections = updatedConnections
                )
            )
        }
    }

    /**
     * Updates the tool assigned to a specific node.
     * 
     * @param nodeId The unique identifier of the node.
     * @param toolName The name of the tool to assign.
     */
    fun updateNodeTool(nodeId: String, toolName: String) {
        _uiState.update { state ->
            val updatedNodes = state.currentPipeline.nodes.map {
                if (it.id == nodeId) it.copy(toolName = toolName, label = toolName) else it
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes)
            )
        }
    }

    /**
     * Updates the reply timeout for a CLARIFICATION node.
     *
     * @param nodeId The unique identifier of the node.
     * @param timeoutMs The timeout in milliseconds, or `null` to fall back to the engine default.
     */
    fun updateNodeClarificationTimeout(nodeId: String, timeoutMs: Long?) {
        _uiState.update { state ->
            val updatedNodes = state.currentPipeline.nodes.map {
                if (it.id == nodeId) it.copy(clarificationTimeoutMs = timeoutMs) else it
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes)
            )
        }
    }

    /**
     * Updates the per-node context configuration that controls which pipeline
     * context blocks (chat history, original task, previous node output,
     * long-term memory, tool results) are concatenated into the node's input
     * on every execution.
     *
     * Two invariants are enforced here as a safety net for cases where the
     * UI layer is bypassed (JSON import, programmatic updates, future
     * regressions):
     *
     * 1. The `nodeInput` flag is forced to `true` — the previous node's
     *    output is the canonical input source for any node in a chain, so
     *    disabling it would silently break the pipeline.
     * 2. If the caller passes a config with every flag disabled, the
     *    `errorMessage` is set so the UI can surface a Snackbar prompting
     *    the user to keep at least one source enabled.
     *
     * @param nodeId The unique identifier of the node to update.
     * @param config The desired [NodeContextConfig]; sanitized before use.
     */
    fun updateNodeContextConfig(nodeId: String, config: NodeContextConfig) {
        val incomingAllDisabled = config.isEmpty()
        val sanitized = config.copy(nodeInput = true)
        _uiState.update { state ->
            val updatedNodes = state.currentPipeline.nodes.map {
                if (it.id == nodeId) it.copy(contextConfig = sanitized) else it
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes),
                errorMessage = if (incomingAllDisabled) {
                    "At least one data source must remain enabled"
                } else {
                    null
                },
            )
        }
    }

    /**
     * Updates the cloud provider for a CLOUD node.
     *
     * @param nodeId The unique identifier of the node.
     * @param provider The name of the provider.
     */
    fun updateNodeCloudProvider(nodeId: String, provider: String) {
        _uiState.update { state ->
            val updatedNodes = state.currentPipeline.nodes.map {
                if (it.id == nodeId) it.copy(cloudProvider = provider) else it
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes)
            )
        }
    }

    /**
     * Clears the current pipeline.
     */
    fun clearPipeline() {
        _uiState.update { state ->
            state.copy(
                currentPipeline = state.currentPipeline.copy(
                    nodes = emptyList(),
                    connections = emptyList()
                )
            )
        }
    }

    /**
     * Applies the base preset consisting of a complex task routing pipeline.
     */
    fun applyBasePreset() {
        _uiState.update { state ->
            state.copy(
                currentPipeline = ai.agent.android.domain.engine.DefaultPipelineFactory.create("Base Preset")
            )
        }
    }

    /**
     * Exports the current pipeline to a JSON string.
     *
     * @return The JSON representation of the current pipeline.
     */
    fun exportPipelineToJson(): String {
        return gson.toJson(_uiState.value.currentPipeline)
    }

    /**
     * Imports a pipeline from a JSON string.
     *
     * @param jsonString The JSON string representing a pipeline.
     */
    fun importPipelineFromJson(jsonString: String) {
        try {
            val pipeline = gson.fromJson(jsonString, PipelineGraph::class.java)
            if (pipeline != null) {
                _uiState.update { state ->
                    state.copy(currentPipeline = pipeline, errorMessage = null)
                }
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to parse JSON") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Invalid JSON format: ${e.message}") }
        }
    }

    /**
     * Saves the current pipeline.
     */
    fun saveCurrentPipeline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = savePipelineUseCase(_uiState.value.currentPipeline)
            _uiState.update { state ->
                val errorMsg = result.exceptionOrNull()?.let { e ->
                    if (e is ai.agent.android.domain.models.PipelineValidationException) {
                        e.errors.joinToString(", ") { err ->
                            when (err) {
                                is ai.agent.android.domain.models.PipelineValidationError.MissingInput -> "Missing INPUT node"
                                is ai.agent.android.domain.models.PipelineValidationError.MissingOutput -> "Missing OUTPUT node"
                                is ai.agent.android.domain.models.PipelineValidationError.MultipleInputs -> "Multiple INPUT nodes are not allowed"
                                is ai.agent.android.domain.models.PipelineValidationError.MultipleOutputs -> "Multiple OUTPUT nodes are not allowed"
                                is ai.agent.android.domain.models.PipelineValidationError.HasCycles -> "Pipeline contains cycles"
                                is ai.agent.android.domain.models.PipelineValidationError.DisconnectedInput -> "INPUT node is not connected"
                                is ai.agent.android.domain.models.PipelineValidationError.DisconnectedOutput -> "OUTPUT node is not connected"
                                is ai.agent.android.domain.models.PipelineValidationError.UnreachableNode -> "Some nodes are unreachable from INPUT"
                                is ai.agent.android.domain.models.PipelineValidationError.DeadEndNode -> "Some nodes do not reach OUTPUT"
                                is ai.agent.android.domain.models.PipelineValidationError.NodeEmptyContext -> {
                                    val name = _uiState.value.currentPipeline.nodes
                                        .find { it.id == err.nodeId }?.label ?: err.nodeId
                                    "Node \"$name\" will not receive any data — enable at least one source"
                                }
                            }
                        }
                    } else {
                        e.message
                    }
                }
                state.copy(
                    isLoading = false,
                    errorMessage = errorMsg
                )
            }
        }
    }

    /**
     * Loads a specific pipeline by ID.
     *
     * @param pipelineId The unique identifier of the pipeline to load.
     */
    fun loadPipeline(pipelineId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val pipeline = loadPipelineUseCase.getPipelineById(pipelineId)
            _uiState.update { state ->
                if (pipeline != null) {
                    state.copy(currentPipeline = pipeline, isLoading = false, errorMessage = null)
                } else {
                    state.copy(isLoading = false, errorMessage = "Pipeline not found")
                }
            }
        }
    }

    /**
     * Clears error messages from UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Renders [template] through [PromptTemplateEngine] and exposes the resulting
     * segments via [OrchestratorUiState.previewState].
     *
     * Resolution may suspend on I/O (the `$MEMORY_SUMMARY` provider hits the database),
     * so the call runs on [viewModelScope]. The intermediate `Loading` state lets the UI
     * show a spinner if the user opens the sheet against a slow provider.
     *
     * @param template the raw prompt that may contain `$VARIABLE` placeholders.
     */
    fun requestPromptPreview(template: String) {
        _uiState.update { it.copy(previewState = PromptPreviewState.Loading) }
        viewModelScope.launch {
            val segments = promptTemplateEngine.renderSegments(
                template,
                promptVariableProviders.toList(),
            )
            _uiState.update { it.copy(previewState = PromptPreviewState.Ready(segments)) }
        }
    }

    /**
     * Closes the prompt-preview bottom sheet, returning the UI to its idle state.
     */
    fun dismissPromptPreview() {
        _uiState.update { it.copy(previewState = PromptPreviewState.Hidden) }
    }

    private companion object {
        /**
         * Computes the deterministic, sorted list of `$KEY` tokens advertised by the
         * registered [PromptVariableProvider]s. A provider whose `key()` throws is
         * silently skipped — this mirrors the engine's tolerance for broken providers
         * so a single misbehaving DI binding cannot empty the chip row.
         */
        private fun computeAvailableVariables(
            providers: Set<PromptVariableProvider>,
        ): List<String> = providers
            .mapNotNull { runCatching { it.key() }.getOrNull() }
            .distinct()
            .sorted()
            .map { "$$it" }
    }
}
