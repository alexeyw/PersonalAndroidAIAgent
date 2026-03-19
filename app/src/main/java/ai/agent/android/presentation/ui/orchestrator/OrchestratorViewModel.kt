package ai.agent.android.presentation.ui.orchestrator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.usecases.LoadPipelineUseCase
import ai.agent.android.domain.usecases.SavePipelineUseCase
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
    private val loadPipelineUseCase: LoadPipelineUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrchestratorUiState())
    val uiState: StateFlow<OrchestratorUiState> = _uiState.asStateFlow()

    init {
        observeSavedPipelines()
    }

    private fun observeSavedPipelines() {
        viewModelScope.launch {
            loadPipelineUseCase.observeAllPipelines()
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
                .collect { pipelines ->
                    _uiState.update { it.copy(savedPipelines = pipelines) }
                }
        }
    }

    /**
     * Adds a new node to the canvas at the specified coordinates.
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
     * Updates the coordinates of an existing node.
     */
    fun updateNodePosition(nodeId: String, x: Float, y: Float) {
        _uiState.update { state ->
            val updatedNodes = state.currentPipeline.nodes.map {
                if (it.id == nodeId) it.copy(x = x, y = y) else it
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes)
            )
        }
    }

    /**
     * Creates a connection between two nodes.
     */
    fun addConnection(sourceNodeId: String, targetNodeId: String) {
        val newConnection = ConnectionModel(
            id = UUID.randomUUID().toString(),
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId
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
     * Removes a node and any connections attached to it.
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
