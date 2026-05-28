package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.R
import ai.agent.android.domain.engine.DefaultPipelineFactory
import ai.agent.android.domain.models.CloudProvider
import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PipelineImportOutcome
import ai.agent.android.domain.models.PipelineValidationError
import ai.agent.android.domain.models.PipelineValidationException
import ai.agent.android.domain.models.PresetCategory
import ai.agent.android.domain.models.PromptPreset
import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.pipelineio.PipelineJsonSerializer
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.PromptPresetRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.usecases.CreatePipelineUseCase
import ai.agent.android.domain.usecases.DeletePipelineUseCase
import ai.agent.android.domain.usecases.DuplicatePipelineUseCase
import ai.agent.android.domain.usecases.GetPromptTemplatesUseCase
import ai.agent.android.domain.usecases.ImportPipelineUseCase
import ai.agent.android.domain.usecases.LoadPipelineUseCase
import ai.agent.android.domain.usecases.RenamePipelineUseCase
import ai.agent.android.domain.usecases.SavePipelineAsPresetUseCase
import ai.agent.android.domain.usecases.SavePipelineUseCase
import ai.agent.android.domain.usecases.SavePromptAsPresetUseCase
import ai.agent.android.domain.usecases.SavePromptTemplateUseCase
import ai.agent.android.presentation.ui.common.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Visual Orchestrator feature.
 * Manages the state of the infinite canvas and coordinates saving/loading pipelines.
 */
@HiltViewModel
@Suppress(
    // Reason: the visual orchestrator is a single screen but coordinates a
    // dozen distinct user flows (graph editing, library management, JSON
    // import/export, validation, pipeline-config dialogs). Splitting into
    // per-feature ViewModels would require lifting all of them into a shared
    // store anyway because the UI state is shared between the editor and the
    // library screen via the `pipelines` nested nav graph. Tracked for a
    // future refactor; not in scope for the static-analysis enforcement task.
    "TooManyFunctions",
    "LargeClass",
)
class OrchestratorViewModel @Inject constructor(
    private val savePipelineUseCase: SavePipelineUseCase,
    private val loadPipelineUseCase: LoadPipelineUseCase,
    private val importPipelineUseCase: ImportPipelineUseCase,
    private val renamePipelineUseCase: RenamePipelineUseCase,
    private val duplicatePipelineUseCase: DuplicatePipelineUseCase,
    private val deletePipelineUseCase: DeletePipelineUseCase,
    private val createPipelineUseCase: CreatePipelineUseCase,
    private val getPromptTemplatesUseCase: GetPromptTemplatesUseCase,
    private val savePromptTemplateUseCase: SavePromptTemplateUseCase,
    private val savePipelineAsPresetUseCase: SavePipelineAsPresetUseCase,
    private val savePromptAsPresetUseCase: SavePromptAsPresetUseCase,
    private val apiKeyRepository: ApiKeyRepository,
    private val toolRepository: ToolRepository,
    private val localModelRepository: LocalModelRepository,
    private val settingsRepository: SettingsRepository,
    private val promptTemplateEngine: PromptTemplateEngine,
    private val promptPresetRepository: PromptPresetRepository,
    private val promptVariableProviders: Set<@JvmSuppressWildcards PromptVariableProvider>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OrchestratorUiState(availableVariables = computeAvailableVariables(promptVariableProviders)),
    )

    /**
     * The current UI state of the Orchestrator screen.
     */
    val uiState: StateFlow<OrchestratorUiState> = _uiState.asStateFlow()

    private val _focusNodeRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * One-shot stream of node ids that the editor should centre the canvas on.
     * Emitted by the Phase 21 [requestFocusNode] hook used by `ValidationBar` taps.
     * Replays are intentionally not retained — every emission represents a fresh tap.
     */
    val focusNodeRequest: SharedFlow<String> = _focusNodeRequest.asSharedFlow()

    private val _runState = MutableStateFlow(PipelineRunState())

    /**
     * Live run state surfaced by the editor's `RunStatusBanner` (Phase 22 /
     * Task 14 — replaces the prior bottom `RunTraceBar`).
     *
     * Wired by [setRunning] / [setActiveRunningNode] today; the real orchestrator
     * integration that drives these fields end-to-end lands post-v0.1 alongside the
     * chat home → agent backend wiring.
     */
    val runState: StateFlow<PipelineRunState> = _runState.asStateFlow()

    init {
        observeSavedPipelines()
        observeProviderKeys()
        loadAvailableTools()
        observeLocalModels()
        observePromptTemplates()
        observeDefaultPipelineId()
    }

    /**
     * Mirrors `LocalModelRepository.getAllModels()` into [OrchestratorUiState.availableLocalModels]
     * so the editor's `NodeConfigSheet` can feed the LITE_RT model dropdown straight from the
     * installed-models registry (with the active model badged). Errors collapse into the same
     * `errorMessage` channel the rest of the VM uses, so a model-list load failure doesn't fail
     * the whole editor screen.
     */
    private fun observeLocalModels() {
        viewModelScope.launch {
            localModelRepository.getAllModels()
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = throwableAsUiText(e)) }
                }
                .collect { models ->
                    _uiState.update { it.copy(availableLocalModels = models) }
                }
        }
    }

    /**
     * Mirrors `SettingsRepository.defaultPipelineId` into [OrchestratorUiState]
     * so the library screen can render the "Default" badge / menu state in
     * real time after [setDefaultPipeline] is invoked.
     */
    private fun observeDefaultPipelineId() {
        viewModelScope.launch {
            settingsRepository.defaultPipelineId.collect { id ->
                _uiState.update { it.copy(defaultPipelineId = id) }
            }
        }
    }

    /**
     * Marks [pipelineId] as the application-wide default pipeline. Used by
     * the library's "Set as default" menu item. The setting is observed by
     * the chat ViewModel which uses it in the TopAppBar subtitle and the
     * "Use default pipeline (…)" label, so chat surfaces stay in sync.
     */
    fun setDefaultPipeline(pipelineId: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultPipelineId(pipelineId)
            _uiState.update {
                it.copy(feedbackMessage = UiText(R.string.orchestrator_feedback_default_pipeline_updated))
            }
        }
    }

    private fun observePromptTemplates() {
        viewModelScope.launch {
            getPromptTemplatesUseCase()
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = throwableAsUiText(e)) }
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
                    PromptTemplate(name = name, text = text, category = category),
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = throwableAsUiText(e)) }
            }
        }
    }

    private fun observeSavedPipelines() {
        viewModelScope.launch {
            loadPipelineUseCase.observeAllPipelines()
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = throwableAsUiText(e)) }
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
                updateProviderKey(CloudProvider.OPENAI, !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            apiKeyRepository.getAnthropicKey().collect { key ->
                updateProviderKey(CloudProvider.ANTHROPIC, !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            apiKeyRepository.getGoogleKey().collect { key ->
                updateProviderKey(CloudProvider.GOOGLE, !key.isNullOrBlank())
            }
        }
        viewModelScope.launch {
            apiKeyRepository.getDeepSeekKey().collect { key ->
                updateProviderKey(CloudProvider.DEEPSEEK, !key.isNullOrBlank())
            }
        }
    }

    private fun updateProviderKey(provider: CloudProvider, hasKey: Boolean) {
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
                _uiState.update { it.copy(errorMessage = throwableAsUiText(e)) }
            }
        }
    }

    /**
     * Adds a new node to the canvas at the specified coordinates.
     *
     * Returns the freshly-generated node id so the caller (typically the editor's quick-add
     * flow) can immediately reference the new node — for example to open its `NodeConfigSheet`
     * before the [uiState] StateFlow has propagated the update. Reading
     * `uiState.currentPipeline.nodes.lastOrNull()` right after this call observes the
     * pre-update value, so the returned id is the only reliable handle.
     *
     * @param type The type of node to add.
     * @param x The x-coordinate for the node's position.
     * @param y The y-coordinate for the node's position.
     * @return The unique identifier assigned to the newly-added node.
     */
    fun addNode(type: NodeType, x: Float, y: Float): String {
        val newNode = NodeModel(
            id = UUID.randomUUID().toString(),
            type = type,
            x = x,
            y = y,
            cloudProvider = if (type == NodeType.CLOUD) CloudProvider.AUTO_KEY else null,
            contextConfig = NodeContextConfig.defaultForType(type),
        )
        _uiState.update { state ->
            val updatedPipeline = state.currentPipeline.copy(
                nodes = state.currentPipeline.nodes + newNode,
            )
            state.copy(currentPipeline = updatedPipeline)
        }
        return newNode.id
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
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes),
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
            label = label,
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
                connections = filteredConnections + newConnection,
            )

            // Validate DAG
            if (tempPipeline.isValidDAG()) {
                createdConnectionId = newConnection.id
                state.copy(currentPipeline = tempPipeline, errorMessage = null)
            } else {
                state.copy(errorMessage = UiText(R.string.errors_orchestrator_cycle_detected))
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
                currentPipeline = state.currentPipeline.copy(connections = updatedConnections),
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
                currentPipeline = state.currentPipeline.copy(connections = updatedConnections),
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
    fun updateNodeConfiguration(
        nodeId: String,
        complexity: Int?,
        keywords: String?,
        prompt: String?,
        systemPrompt: String?,
    ) {
        _uiState.update { state ->
            val updatedNodes = state.currentPipeline.nodes.map {
                if (it.id == nodeId) {
                    it.copy(
                        conditionComplexity = complexity,
                        conditionKeywords = keywords,
                        conditionPrompt = prompt,
                        systemPrompt = systemPrompt,
                    )
                } else {
                    it
                }
            }
            state.copy(
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes),
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
                    connections = updatedConnections,
                ),
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
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes),
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
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes),
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
                    UiText(R.string.errors_orchestrator_at_least_one_source)
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
                currentPipeline = state.currentPipeline.copy(nodes = updatedNodes),
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
                    connections = emptyList(),
                ),
            )
        }
    }

    /**
     * Replaces the nodes and connections of the pipeline currently being
     * edited with the default complex task-routing preset. Preserves the
     * pipeline's `id`, `name`, and `updatedAt` (refreshed to "now") so the
     * preset is *applied to* the current pipeline rather than spawning a
     * new "Base Preset" pipeline alongside it.
     */
    fun applyBasePreset() {
        _uiState.update { state ->
            val preset = DefaultPipelineFactory.create(state.currentPipeline.name)
            state.copy(
                currentPipeline = state.currentPipeline.copy(
                    nodes = preset.nodes,
                    connections = preset.connections,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Exports the current pipeline to a JSON string in the schema-versioned
     * format consumed by the browser-side editor (`pipeline-editor.html`).
     */
    fun exportPipelineToJson(): String = PipelineJsonSerializer.serialize(_uiState.value.currentPipeline)

    /**
     * Parses [jsonString] and, on success, persists the imported pipeline
     * through [SavePipelineUseCase] so it appears in the saved-pipelines
     * list immediately. On a `schemaVersion` mismatch the parsed graph is
     * stashed in [OrchestratorUiState.pendingImport] for the UI to
     * confirm; nothing is written until the user accepts via
     * [confirmPendingImport].
     */
    fun importPipelineFromJson(jsonString: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val invocation = importPipelineUseCase(jsonString)
            _uiState.update { state ->
                when (val outcome = invocation.outcome) {
                    is PipelineImportOutcome.Success -> {
                        val saveErr = invocation.saveResult?.let { res ->
                            res.exceptionOrNull()?.let(::messageForSaveError)
                        }
                        state.copy(
                            currentPipeline = if (saveErr == null) outcome.graph else state.currentPipeline,
                            isLoading = false,
                            pendingImport = null,
                            errorMessage = saveErr,
                        )
                    }
                    is PipelineImportOutcome.SchemaMismatch ->
                        state.copy(
                            isLoading = false,
                            pendingImport = outcome,
                            errorMessage = null,
                        )
                    is PipelineImportOutcome.Failure ->
                        state.copy(
                            isLoading = false,
                            pendingImport = null,
                            errorMessage = UiText.Dynamic(outcome.message),
                        )
                }
            }
        }
    }

    /**
     * Persists the graph captured in [OrchestratorUiState.pendingImport]
     * after the user has accepted the schema-mismatch warning. No-op when
     * no import is pending.
     */
    fun confirmPendingImport() {
        val pending = _uiState.value.pendingImport ?: return
        // Clear pendingImport immediately so the AlertDialog dismisses
        // before the suspending save runs. Holding it while persistConfirmed
        // is in-flight would let the user re-click "Import anyway" or
        // dismiss the dialog mid-save, racing two persists for the same
        // graph.
        _uiState.update { it.copy(isLoading = true, pendingImport = null) }
        viewModelScope.launch {
            val result = importPipelineUseCase.persistConfirmed(pending)
            _uiState.update { state ->
                val saveErr = result.exceptionOrNull()?.let(::messageForSaveError)
                state.copy(
                    currentPipeline = if (saveErr == null) pending.graph else state.currentPipeline,
                    isLoading = false,
                    errorMessage = saveErr,
                )
            }
        }
    }

    /**
     * Discards a pending schema-mismatch import without persisting.
     */
    fun cancelPendingImport() {
        _uiState.update { it.copy(pendingImport = null) }
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
                    errorMessage = result.exceptionOrNull()?.let(::messageForSaveError),
                )
            }
        }
    }

    /**
     * Translates a save failure (validation or generic) into a UI-ready
     * [UiText]. Shared by [saveCurrentPipeline], [importPipelineFromJson]
     * and [confirmPendingImport] so the user sees the same wording
     * regardless of which entry point triggered the validator.
     *
     * Returns a single `UiText.Resource` when the failure is a single
     * `PipelineValidationException` with exactly one error; multi-error
     * validation collapses into a `UiText.Dynamic` with a comma-joined
     * resolved-at-display-time list (this keeps the API typed without
     * forcing the resource layer to model arbitrarily many error
     * combinations). Generic exceptions become `UiText.Dynamic`.
     */
    private fun messageForSaveError(e: Throwable): UiText? {
        if (e !is PipelineValidationException) {
            return e.message?.let { UiText.Dynamic(it) }
        }
        val parts = e.errors.map { err -> validationErrorAsUiText(err) }
        return when (parts.size) {
            0 -> null
            1 -> parts.first()
            else -> UiText.Joined(parts)
        }
    }

    /**
     * Resolves a single [PipelineValidationError] to its `UiText`
     * representation. Pulled out so [messageForSaveError] can decide whether
     * to keep the typed `Resource` (single error) or collapse into a
     * `Dynamic` join (multiple errors).
     */
    private fun validationErrorAsUiText(err: PipelineValidationError): UiText = when (err) {
        is PipelineValidationError.MissingInput ->
            UiText(R.string.errors_orchestrator_validation_missing_input)
        is PipelineValidationError.MissingOutput ->
            UiText(R.string.errors_orchestrator_validation_missing_output)
        is PipelineValidationError.MultipleInputs ->
            UiText(R.string.errors_orchestrator_validation_multiple_inputs)
        is PipelineValidationError.MultipleOutputs ->
            UiText(R.string.errors_orchestrator_validation_multiple_outputs)
        is PipelineValidationError.HasCycles ->
            UiText(R.string.errors_orchestrator_validation_has_cycles)
        is PipelineValidationError.DisconnectedInput ->
            UiText(R.string.errors_orchestrator_validation_disconnected_input)
        is PipelineValidationError.DisconnectedOutput ->
            UiText(R.string.errors_orchestrator_validation_disconnected_output)
        is PipelineValidationError.UnreachableNode ->
            UiText(R.string.errors_orchestrator_validation_unreachable_node)
        is PipelineValidationError.DeadEndNode ->
            UiText(R.string.errors_orchestrator_validation_dead_end)
        is PipelineValidationError.NodeEmptyContext -> {
            val name = _uiState.value.currentPipeline.nodes
                .find { it.id == err.nodeId }?.label ?: err.nodeId
            UiText.of(R.string.errors_orchestrator_validation_node_no_sources, name)
        }
    }

    /**
     * Lifts a thrown exception into a `UiText`, falling back to the generic
     * "unexpected error" resource when the throwable carries no message.
     */
    private fun throwableAsUiText(e: Throwable): UiText =
        e.message?.let { UiText.Dynamic(it) } ?: UiText(R.string.errors_generic_unexpected)

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
                    state.copy(
                        isLoading = false,
                        errorMessage = UiText(R.string.errors_orchestrator_pipeline_not_found),
                    )
                }
            }
        }
    }

    /**
     * Renames the pipeline identified by [pipelineId] to [newName].
     *
     * Delegates validation (blank / over-length name) to [RenamePipelineUseCase] and
     * surfaces failures via [OrchestratorUiState.errorMessage] for the Snackbar to
     * pick up. The list of pipelines refreshes itself through the existing
     * `observeSavedPipelines` flow once the save completes; if the renamed pipeline
     * is the one currently loaded into the editor, [OrchestratorUiState.currentPipeline]
     * is patched in place so the editor's TopAppBar and the library highlight match
     * without waiting for the database round-trip.
     *
     * @param pipelineId Unique identifier of the pipeline to rename.
     * @param newName The new display name; trimmed and validated by the use case.
     */
    fun renamePipeline(pipelineId: String, newName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = renamePipelineUseCase(pipelineId, newName)
            _uiState.update { state ->
                val error = result.exceptionOrNull()?.message?.let { UiText.Dynamic(it) }
                val patchedCurrent = if (
                    result.isSuccess && state.currentPipeline.id == pipelineId
                ) {
                    state.currentPipeline.copy(name = newName.trim())
                } else {
                    state.currentPipeline
                }
                state.copy(
                    isLoading = false,
                    currentPipeline = patchedCurrent,
                    errorMessage = error,
                    feedbackMessage = if (result.isSuccess) {
                        UiText(R.string.orchestrator_feedback_pipeline_renamed)
                    } else {
                        state.feedbackMessage
                    },
                )
            }
        }
    }

    /**
     * Duplicates an existing pipeline and exposes the new graph as the active one.
     *
     * The duplicate is created with fresh ids (pipeline + every node + every
     * connection) by [DuplicatePipelineUseCase]. On success, the duplicate is
     * loaded into [OrchestratorUiState.currentPipeline] so the user can continue
     * editing the copy immediately — this is the expected flow for the library's
     * "Duplicate" context-menu action.
     *
     * @param pipelineId Unique identifier of the source pipeline.
     */
    fun duplicatePipeline(pipelineId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = duplicatePipelineUseCase(pipelineId)
            _uiState.update { state ->
                val duplicate = result.getOrNull()
                val error = result.exceptionOrNull()?.message?.let { UiText.Dynamic(it) }
                state.copy(
                    isLoading = false,
                    currentPipeline = duplicate ?: state.currentPipeline,
                    errorMessage = error,
                    feedbackMessage = if (duplicate != null) {
                        UiText(R.string.orchestrator_feedback_pipeline_duplicated)
                    } else {
                        state.feedbackMessage
                    },
                )
            }
        }
    }

    /**
     * Deletes the pipeline identified by [pipelineId] from the library.
     *
     * Forwards the active pipeline id (taken from [OrchestratorUiState.currentPipeline])
     * to [DeletePipelineUseCase] so attempts to delete the pipeline being edited are
     * blocked at the use-case layer. The error message wired into the UI is
     * deliberately UI-friendly ("Active pipeline cannot be deleted") so the
     * Snackbar reads the same regardless of how the deletion was triggered.
     *
     * @param pipelineId Unique identifier of the pipeline to delete.
     */
    fun deletePipeline(pipelineId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val activeId = _uiState.value.currentPipeline.id
            val result = deletePipelineUseCase(pipelineId, activeId)
            // If the deleted pipeline was the user-marked default, clear the
            // setting so chat surfaces don't dangle on a non-existent id and
            // immediately fall back to the new "first in library" default.
            if (result.isSuccess && _uiState.value.defaultPipelineId == pipelineId) {
                settingsRepository.setDefaultPipelineId(null)
            }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message?.let { UiText.Dynamic(it) },
                    feedbackMessage = if (result.isSuccess) {
                        UiText(R.string.orchestrator_feedback_pipeline_deleted)
                    } else {
                        state.feedbackMessage
                    },
                )
            }
        }
    }

    /**
     * Creates a brand-new pipeline with a minimal `INPUT → OUTPUT` seed and
     * loads it as the current pipeline.
     *
     * Used by the library's "New pipeline" FAB. Persistence happens through
     * [CreatePipelineUseCase], which validates the name and seeds the graph so
     * the freshly created pipeline already passes [PipelineGraph.validate].
     *
     * @param name Display name for the new pipeline.
     */
    fun createNewPipeline(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = createPipelineUseCase(name)
            _uiState.update { state ->
                val created = result.getOrNull()
                state.copy(
                    isLoading = false,
                    currentPipeline = created ?: state.currentPipeline,
                    errorMessage = result.exceptionOrNull()?.message?.let { UiText.Dynamic(it) },
                    feedbackMessage = if (created != null) {
                        UiText(R.string.orchestrator_feedback_pipeline_created)
                    } else {
                        state.feedbackMessage
                    },
                    // Only request navigation on a successful create; a failed
                    // create (validation, persistence error) must keep the user
                    // in the library so they can retry, instead of pushing
                    // them into the editor with the previously active graph.
                    pendingEditorNavigation = state.pendingEditorNavigation || created != null,
                )
            }
        }
    }

    /**
     * Clears the transient feedback string after the Snackbar has shown it.
     * Mirrors [clearError] so the library screen can dismiss the feedback
     * channel independently of the error channel.
     */
    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    /**
     * Acknowledges and resets the [OrchestratorUiState.pendingEditorNavigation]
     * flag. Call from the library screen's `LaunchedEffect` after invoking
     * the navigation callback, so the same trigger never fires twice (e.g.
     * after a configuration change).
     */
    fun consumePendingEditorNavigation() {
        _uiState.update { it.copy(pendingEditorNavigation = false) }
    }

    /**
     * Clears error messages from UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Cold flow of bundled prompt presets targeting [nodeType], for the
     * `PromptPresetPickerDialog`'s Bundled tab. The dialog calls
     * `collectAsState(initial = emptyList())` so a brief empty render is
     * acceptable while the first asset-decode pass resolves.
     *
     * Pure delegation to [PromptPresetRepository] — kept here so the picker
     * does not need to depend on the data layer directly.
     */
    fun bundledPresetsForType(nodeType: NodeType): Flow<List<PromptPreset>> =
        promptPresetRepository.getPresetsForType(nodeType).map { all -> all.filter { it.isBundled } }

    /**
     * Cold flow of user-saved prompt presets targeting [nodeType], for the
     * `PromptPresetPickerDialog`'s Mine tab.
     */
    fun userPresetsForType(nodeType: NodeType): Flow<List<PromptPreset>> =
        promptPresetRepository.getPresetsForType(nodeType).map { all -> all.filter { !it.isBundled } }

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

    // ─── Phase 21 / Task 9 — Pipeline editor hooks ───────────────────────────

    /**
     * Replaces the persisted [NodeModel] for [nodeId] with [updated]. Used by the new
     * `NodeConfigSheet` flow once the user taps Save and the catalog validation passes.
     *
     * The caller is expected to have already projected its catalog `NodeConfig` onto a
     * [NodeModel] via `NodeConfigCodec.apply(source, config)`. This entry point is
     * intentionally generic so future per-type updates do not require dedicated VM
     * methods.
     */
    fun updateNodeFromEditor(nodeId: String, updated: NodeModel) {
        _uiState.update { state ->
            val nextNodes = state.currentPipeline.nodes.map { if (it.id == nodeId) updated else it }
            state.copy(currentPipeline = state.currentPipeline.copy(nodes = nextNodes))
        }
    }

    /**
     * Replaces the entire `currentPipeline` graph in one shot. Used by the Phase-21
     * editor for undo / redo (which restores a previously captured snapshot) and the
     * auto-layout commit (which writes the recomputed node positions in bulk).
     */
    fun replaceCurrentPipeline(graph: PipelineGraph) {
        _uiState.update { it.copy(currentPipeline = graph) }
    }

    /**
     * Requests that the editor centre its canvas on [nodeId] and select it. Fired by the
     * `ValidationBar` so tapping an error focuses the offending node without forcing
     * the validation logic to know anything about the canvas viewport.
     */
    fun requestFocusNode(nodeId: String) {
        _focusNodeRequest.tryEmit(nodeId)
    }

    /**
     * Flips the editor's run banner between idle and active. Phase-21 placeholder —
     * the real run loop lands post-v0.1; until then the editor exposes a debug toggle
     * so the banner can be exercised end-to-end.
     *
     * Clearing the flag (`running = false`) also wipes [PipelineRunState.activeNodeId]
     * so the banner / per-node dimming reset cleanly. Without this, a paused-mid-run
     * `activeNodeId` would leak across runs (and across screen reopens — see
     * [stopRunAndReset]).
     */
    fun setRunning(running: Boolean) {
        _runState.update {
            if (running) it.copy(isRunning = true) else PipelineRunState()
        }
    }

    /**
     * Fully resets the run banner state. Called from the editor when the user taps
     * `Stop` on the banner, and from the screen's `DisposableEffect` when the user
     * leaves the editor — both paths must clear `activeNodeId` and `isRunning`
     * together, otherwise a stale banner would surface on the next pipeline open.
     */
    fun stopRunAndReset() {
        _runState.value = PipelineRunState()
    }

    /**
     * Sets (or clears with `null`) the currently-running node id during a pipeline run.
     * Drives both the [app.knotwork.design.components.pipelineeditor.NodeCard]
     * `running` parameter and the toolbar subtitle / run banner labels.
     */
    fun setActiveRunningNode(nodeId: String?) {
        _runState.update { it.copy(activeNodeId = nodeId) }
    }

    /**
     * Resolves a single [PipelineValidationError] to its user-visible label using the
     * same wording the save-time toast emits. Exposed so the editor's `ValidationBar`
     * can render the same copy without re-implementing the mapping.
     */
    fun labelFor(error: PipelineValidationError): UiText = validationErrorAsUiText(error)

    /**
     * Packages the currently-edited pipeline (whatever is loaded into the
     * editor) as a user preset via [SavePipelineAsPresetUseCase]. Surfaces
     * a success / failure message through [OrchestratorUiState.feedbackMessage]
     * / [OrchestratorUiState.errorMessage] so the calling screen (editor)
     * doesn't need its own Snackbar plumbing.
     */
    fun saveCurrentAsPreset(
        name: String,
        description: String,
        category: PresetCategory,
        tags: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            val result = savePipelineAsPresetUseCase(
                graph = _uiState.value.currentPipeline,
                name = name,
                description = description,
                category = category,
                tags = tags,
            )
            _uiState.update { state ->
                val error = result.exceptionOrNull()?.let(::messageForSaveError)
                state.copy(
                    errorMessage = error,
                    feedbackMessage = if (error == null) {
                        UiText(R.string.orchestrator_preset_save_success)
                    } else {
                        state.feedbackMessage
                    },
                )
            }
        }
    }

    /**
     * Packages an existing library pipeline ([pipelineId]) as a user
     * preset. Resolves the graph through [LoadPipelineUseCase.getPipelineById]
     * before delegating to [SavePipelineAsPresetUseCase], so the editor
     * does not need to be loaded with the source pipeline first.
     */
    fun saveAsPresetFromLibrary(
        pipelineId: String,
        name: String,
        description: String,
        category: PresetCategory,
        tags: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            val pipeline = loadPipelineUseCase.getPipelineById(pipelineId)
            if (pipeline == null) {
                _uiState.update { it.copy(errorMessage = UiText(R.string.errors_orchestrator_pipeline_not_found)) }
                return@launch
            }
            val result = savePipelineAsPresetUseCase(
                graph = pipeline,
                name = name,
                description = description,
                category = category,
                tags = tags,
            )
            _uiState.update { state ->
                val error = result.exceptionOrNull()?.let(::messageForSaveError)
                state.copy(
                    errorMessage = error,
                    feedbackMessage = if (error == null) {
                        UiText(R.string.orchestrator_preset_save_success)
                    } else {
                        state.feedbackMessage
                    },
                )
            }
        }
    }

    /**
     * Packages a freshly-edited system prompt as a user prompt preset via
     * [SavePromptAsPresetUseCase] (Phase 24 / Task 4). Invoked by
     * `PipelineEditorScreen` from the 💾 button on prompt-bearing fields
     * inside `NodeConfigSheet` (Phase 24 / Task 5).
     *
     * Errors are reported through [OrchestratorUiState.errorMessage] /
     * [OrchestratorUiState.feedbackMessage] using the same channel as
     * pipeline-preset saves.
     *
     * @param systemPrompt The raw prompt template to save.
     * @param name Display name for the preset.
     * @param description Free-form description.
     * @param nodeType The node type this preset targets. Must be LLM-driven
     *   (validated by [SavePromptAsPresetUseCase]).
     * @param tags Lower-case kebab-case tags.
     */
    fun saveCurrentPromptAsPreset(
        systemPrompt: String,
        name: String,
        description: String,
        nodeType: NodeType,
        tags: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            val result = savePromptAsPresetUseCase(
                systemPrompt = systemPrompt,
                name = name,
                description = description,
                nodeType = nodeType,
                tags = tags,
            )
            _uiState.update { state ->
                val error = result.exceptionOrNull()?.let(::messageForSaveError)
                state.copy(
                    errorMessage = error,
                    feedbackMessage = if (error == null) {
                        UiText(R.string.orchestrator_prompt_preset_save_success)
                    } else {
                        state.feedbackMessage
                    },
                )
            }
        }
    }

    private companion object {
        /**
         * Computes the deterministic, sorted list of `$KEY` tokens advertised by the
         * registered [PromptVariableProvider]s. A provider whose `key()` throws is
         * silently skipped — this mirrors the engine's tolerance for broken providers
         * so a single misbehaving DI binding cannot empty the chip row.
         */
        private fun computeAvailableVariables(providers: Set<PromptVariableProvider>): List<String> = providers
            .mapNotNull { runCatching { it.key() }.getOrNull() }
            .distinct()
            .sorted()
            .map { "$$it" }
    }
}
