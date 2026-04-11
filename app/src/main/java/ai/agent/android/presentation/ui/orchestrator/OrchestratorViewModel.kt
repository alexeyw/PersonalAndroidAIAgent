package ai.agent.android.presentation.ui.orchestrator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PromptTemplate
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
    private val toolRepository: ToolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrchestratorUiState())
    
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
                updateProviderKey(NodeType.OPENAI, !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            apiKeyRepository.getAnthropicKey().collect { key ->
                updateProviderKey(NodeType.ANTHROPIC, !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            apiKeyRepository.getGoogleKey().collect { key ->
                updateProviderKey(NodeType.GOOGLE, !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            apiKeyRepository.getDeepSeekKey().collect { key ->
                updateProviderKey(NodeType.DEEPSEEK, !key.isNullOrBlank())
            }
        }
    }

    private fun updateProviderKey(type: NodeType, hasKey: Boolean) {
        _uiState.update { state ->
            val updatedKeys = state.providerKeys.toMutableMap()
            updatedKeys[type] = hasKey
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
            y = y
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
     */
    fun addConnection(sourceNodeId: String, targetNodeId: String, label: String? = null) {
        val newConnection = ConnectionModel(
            id = UUID.randomUUID().toString(),
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId,
            label = label
        )
        _uiState.update { state ->
            val tempPipeline = state.currentPipeline.copy(
                connections = state.currentPipeline.connections + newConnection
            )
            
            // Validate DAG
            if (tempPipeline.isValidDAG()) {
                state.copy(currentPipeline = tempPipeline, errorMessage = null)
            } else {
                state.copy(errorMessage = "Cannot connect: Cycle detected")
            }
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
                state.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message
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
}
