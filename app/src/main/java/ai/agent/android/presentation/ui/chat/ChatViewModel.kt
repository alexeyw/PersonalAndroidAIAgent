package ai.agent.android.presentation.ui.chat

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.agent.android.presentation.state.ActiveSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for managing the state of the Chat screen.
 * Orchestrates the interaction between the user input, the chat history, and the AI agent.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentOrchestratorUseCase: AgentOrchestratorUseCase,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val loadModelUseCase: LoadModelUseCase,
    private val getContextWindowUseCase: GetContextWindowUseCase,
    private val activeSessionTracker: ActiveSessionTracker,
    private val llmInferenceEngine: LlmInferenceEngine,
    private val clarificationRepository: ClarificationRepository,
    private val pipelineRepository: PipelineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    /**
     * The current UI state of the Chat screen.
     */
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _exportEvents = MutableSharedFlow<ChatExportPayload>(extraBufferCapacity = 1)
    /**
     * One-shot events emitted when the user triggers a chat export. Consumed by the UI to
     * launch a system share sheet ([android.content.Intent.ACTION_SEND]).
     */
    val exportEvents: SharedFlow<ChatExportPayload> = _exportEvents.asSharedFlow()

    private var messagesJob: Job? = null
    private var generationJob: Job? = null

    init {
        loadSessions()
        observeAvailablePipelines()
        initializeSession()
        observeMaxContextSize()
    }

    /**
     * Continuously observes the pipeline library and recomputes:
     *  1. [ChatUiState.availablePipelines] — feeds the new-chat selector and
     *     chat-settings dialog.
     *  2. [ChatUiState.currentPipelineName] — TopAppBar subtitle: name of the
     *     pipeline bound to the current chat (or of the default pipeline when
     *     `pipelineId == null`).
     *  3. Deleted-pipeline fallback: if the pipeline currently bound to the
     *     active chat disappears from the library, silently rebind the chat
     *     to the default pipeline and emit a one-shot Snackbar via
     *     [ChatUiState.pipelineFallbackMessage].
     *
     * The observation is keyed on the pair `(availablePipelines, sessions)` so
     * that switching chats while pipelines are unchanged still triggers a
     * subtitle recompute.
     */
    private fun observeAvailablePipelines() {
        viewModelScope.launch {
            pipelineRepository.getAllPipelines().collect { graphs ->
                val summaries = graphs.map { PipelineSummary(id = it.id, name = it.name) }
                _uiState.update { state ->
                    state.copy(
                        availablePipelines = summaries,
                        currentPipelineName = resolvePipelineName(
                            sessions = state.sessions,
                            currentSessionId = state.currentSessionId,
                            summaries = summaries,
                        ),
                    )
                }
                handleDeletedBoundPipeline(summaries)
            }
        }
    }

    /**
     * Detects whether the pipeline bound to the currently active chat has been
     * deleted. When it has, persists `pipelineId = null` on the session
     * (falling back to the default pipeline) and surfaces a one-shot Snackbar.
     *
     * No-op when the chat is unbound (`pipelineId == null`) or when the bound
     * pipeline still exists.
     */
    private suspend fun handleDeletedBoundPipeline(summaries: List<PipelineSummary>) {
        val state = _uiState.value
        val session = state.sessions.firstOrNull { it.id == state.currentSessionId } ?: return
        val boundId = session.pipelineId ?: return
        if (summaries.any { it.id == boundId }) return

        chatRepository.saveSession(session.copy(pipelineId = null))
        _uiState.update {
            it.copy(pipelineFallbackMessage = "The pipeline was deleted. Default pipeline is used now.")
        }
    }

    /**
     * Resolves the display name of the pipeline currently bound to the active
     * chat — either the explicit binding when set, or the default pipeline
     * (the first entry in [summaries]) otherwise. Returns `null` when no
     * pipelines exist yet, so the TopAppBar can omit the subtitle entirely.
     */
    private fun resolvePipelineName(
        sessions: List<ChatSession>,
        currentSessionId: String,
        summaries: List<PipelineSummary>,
    ): String? {
        if (summaries.isEmpty()) return null
        val session = sessions.firstOrNull { it.id == currentSessionId }
        val boundId = session?.pipelineId
        val match = boundId?.let { id -> summaries.firstOrNull { it.id == id } }
        return (match ?: summaries.first()).name
    }

    private fun loadSessions() {
        viewModelScope.launch {
            chatRepository.getSessionsFlow().collect { sessions ->
                _uiState.update { state ->
                    state.copy(
                        sessions = sessions,
                        currentPipelineName = resolvePipelineName(
                            sessions = sessions,
                            currentSessionId = state.currentSessionId,
                            summaries = state.availablePipelines,
                        ),
                    )
                }
                handleDeletedBoundPipeline(_uiState.value.availablePipelines)
            }
        }
    }

    private fun observeMaxContextSize() {
        viewModelScope.launch {
            settingsRepository.maxContextLength.collect { maxLength ->
                _uiState.update { it.copy(maxContextSize = maxLength) }
            }
        }
    }

    /**
     * Sets whether the chat screen is currently visible to the user.
     * This helps suppress push notifications for approvals when the inline UI is active.
     *
     * @param isVisible True if the screen is actively displayed, false otherwise.
     */
    fun setChatVisible(isVisible: Boolean) {
        val currentSessionId = _uiState.value.currentSessionId
        if (isVisible && currentSessionId.isNotBlank()) {
            activeSessionTracker.setActiveSessionId(currentSessionId)
        } else {
            activeSessionTracker.setActiveSessionId(null)
        }
    }

    /**
     * Initializes the chat session by either restoring the last active session ID
     * or generating a new one if none exists.
     * Also verifies that the LLM model is loaded and ready.
     */
    private fun initializeSession() {
        viewModelScope.launch {
            // Check if model is loaded by attempting to load it
            val modelResult = loadModelUseCase()
            if (modelResult is Result.Error) {
                _uiState.update { 
                    it.copy(errorMessage = "Model Error: ${modelResult.message}. Please check your model settings.") 
                }
            }

            val savedSessionId = settingsRepository.currentChatSessionId.first()
            val sessionId = if (savedSessionId.isNullOrBlank()) {
                val newId = UUID.randomUUID().toString()
                settingsRepository.setCurrentChatSessionId(newId)
                
                chatRepository.saveSession(
                    ChatSession(
                        id = newId,
                        name = "New Chat",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                newId
            } else {
                savedSessionId
            }
            
            _uiState.update { it.copy(currentSessionId = sessionId) }
            loadMessages(sessionId)
            // activeSessionTracker is managed exclusively by setChatVisible via Lifecycle events
        }
    }

    /**
     * Opens the new-chat pipeline selector `ModalBottomSheet`. The bottom
     * sheet pre-selects the application-wide default pipeline so a user who
     * just taps "Create" gets the same behaviour as before Phase 17.2.
     *
     * When no pipelines exist yet, falls back to creating an unbound chat
     * directly without prompting — the selector would be empty otherwise.
     */
    fun requestNewSession() {
        val pipelines = _uiState.value.availablePipelines
        if (pipelines.isEmpty()) {
            createNewSession(pipelineId = null)
            return
        }
        _uiState.update {
            it.copy(
                newChatPipelinePrompt = NewChatPipelinePrompt(
                    preselectedPipelineId = pipelines.first().id,
                ),
            )
        }
    }

    /**
     * Dismisses the new-chat pipeline selector without creating a chat.
     */
    fun dismissNewChatPrompt() {
        _uiState.update { it.copy(newChatPipelinePrompt = null) }
    }

    /**
     * Confirms the new-chat pipeline selection and creates the session.
     *
     * @param pipelineId Identifier of the pipeline the new chat should bind
     *   to, or `null` to use the application-wide default pipeline.
     */
    fun confirmNewSession(pipelineId: String?) {
        _uiState.update { it.copy(newChatPipelinePrompt = null) }
        createNewSession(pipelineId = pipelineId)
    }

    /**
     * Creates a new chat session bound to [pipelineId] and switches to it.
     *
     * Internal entry-point used by both [requestNewSession] (no-pipelines
     * shortcut) and [confirmNewSession] (after the selector resolves).
     *
     * @param pipelineId Pipeline identifier to bind, or `null` for the
     *   default pipeline.
     */
    private fun createNewSession(pipelineId: String?) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            chatRepository.saveSession(
                ChatSession(
                    id = newId,
                    name = "New Chat",
                    updatedAt = System.currentTimeMillis(),
                    pipelineId = pipelineId,
                )
            )
            switchSession(newId)
        }
    }

    /**
     * Switches the active chat session.
     *
     * @param sessionId The ID of the session to switch to.
     */
    fun switchSession(sessionId: String) {
        viewModelScope.launch {
            settingsRepository.setCurrentChatSessionId(sessionId)
            _uiState.update { state ->
                state.copy(
                    currentSessionId = sessionId,
                    isGenerating = false,
                    orchestratorState = null,
                    clarificationCards = emptyList(),
                    currentPipelineName = resolvePipelineName(
                        sessions = state.sessions,
                        currentSessionId = sessionId,
                        summaries = state.availablePipelines,
                    ),
                )
            }
            loadMessages(sessionId)
            handleDeletedBoundPipeline(_uiState.value.availablePipelines)
            
            // Re-evaluate active session tracking if the UI is currently visible.
            // setChatVisible logic relies on UI state, so we update the tracker if we were already active.
            if (activeSessionTracker.activeSessionId.value != null) {
                activeSessionTracker.setActiveSessionId(sessionId)
            }
        }
    }

    /**
     * Renames an existing chat session.
     *
     * @param sessionId The ID of the session to rename.
     * @param newName The new name for the session.
     */
    fun renameSession(sessionId: String, newName: String) {
        viewModelScope.launch {
            val session = chatRepository.getSessionById(sessionId)
            if (session != null) {
                chatRepository.saveSession(session.copy(name = newName))
            }
        }
    }

    /**
     * Deletes a chat session and its history.
     *
     * @param sessionId The ID of the session to delete.
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            val currentState = _uiState.value
            if (currentState.currentSessionId == sessionId) {
                // Switch to the first available or create new
                val sessions = currentState.sessions.filter { it.id != sessionId }
                if (sessions.isNotEmpty()) {
                    switchSession(sessions.first().id)
                } else {
                    createNewSession(pipelineId = null)
                }
            }
        }
    }

    /**
     * Loads the chat messages for the given session ID and observes changes.
     *
     * @param sessionId The ID of the chat session to load.
     */
    private fun loadMessages(sessionId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesForSession(sessionId).collect { messages ->
                val contextString = getContextWindowUseCase(sessionId)
                _uiState.update { 
                    it.copy(
                        messages = messages,
                        contextSize = contextString.length
                    ) 
                }
            }
        }
    }

    /**
     * Sends a user prompt to the agent and starts the generation cycle.
     * Updates the UI state with intermediate orchestrator steps (streaming).
     *
     * @param prompt The user's input text.
     */
    fun sendMessage(prompt: String) {
        val currentState = _uiState.value
        if (currentState.isGenerating || prompt.isBlank()) return

        if (!llmInferenceEngine.isInitialized) {
            _uiState.update {
                it.copy(inlineError = "Please load a model in Settings before sending a message.")
            }
            return
        }

        generationJob = viewModelScope.launch {
            // Auto-rename logic for new chats
            val currentSession = currentState.sessions.find { it.id == currentState.currentSessionId }
            if (currentSession?.name == "New Chat") {
                val newName = if (prompt.length > 20) prompt.take(20) + "..." else prompt
                renameSession(currentState.currentSessionId, newName)
            }

            _uiState.update {
                it.copy(
                    isGenerating = true,
                    errorMessage = null,
                    inlineError = null,
                    orchestratorState = null,
                    pipelineTrace = emptyList(),
                    currentStep = null,
                    clarificationCards = emptyList(),
                )
            }

            val pipelineId = currentState.sessions
                .firstOrNull { it.id == currentState.currentSessionId }
                ?.pipelineId
            agentOrchestratorUseCase(currentState.currentSessionId, prompt, pipelineId)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            currentStep = null,
                            errorMessage = error.message ?: "An unexpected error occurred",
                            orchestratorState = AgentOrchestratorState.Error(error.message ?: "Unknown error")
                        )
                    }
                }
                .collect { state ->
                    val isTerminal = state is AgentOrchestratorState.Completed || state is AgentOrchestratorState.Error
                    _uiState.update { current ->
                        val updatedCards = if (state is AgentOrchestratorState.AwaitingClarification) {
                            appendClarificationCard(current.clarificationCards, state.request)
                        } else {
                            current.clarificationCards
                        }
                        current.copy(
                            orchestratorState = state,
                            currentStep = when {
                                isTerminal -> null
                                state is AgentOrchestratorState.PipelineStage -> state.stepInfo
                                else -> current.currentStep
                            },
                            pipelineTrace = if (state is AgentOrchestratorState.PipelineTrace) state.steps else current.pipelineTrace,
                            isGenerating = if (isTerminal) false else current.isGenerating,
                            clarificationCards = updatedCards,
                        )
                    }
                }
        }
    }
    
    /**
     * Cancels the active generation job and saves the partial response as a stopped message.
     * If the orchestrator was in a [AgentOrchestratorState.Thinking] or [AgentOrchestratorState.Answering]
     * state, the accumulated text is persisted with a "[остановлено]" suffix so the user
     * does not lose the partial output.
     */
    fun stopGeneration() {
        generationJob?.cancel()
        val partial = when (val s = _uiState.value.orchestratorState) {
            is AgentOrchestratorState.Thinking  -> s.partialText
            is AgentOrchestratorState.Answering -> s.partialText
            else -> null
        }
        if (!partial.isNullOrBlank()) {
            viewModelScope.launch {
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = _uiState.value.currentSessionId,
                        role = Role.AGENT,
                        content = "$partial [stopped]",
                        timestamp = System.currentTimeMillis(),
                    )
                )
            }
        }
        _uiState.update {
            it.copy(
                isGenerating = false,
                currentStep = null,
                orchestratorState = null,
                clarificationCards = it.clarificationCards.filterNot { card ->
                    card.status == ClarificationCardUiModel.Status.PENDING
                },
            )
        }
    }

    /**
     * Clears the current error message from the UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Clears the inline error banner (e.g. after the user starts typing a new message or loads a model).
     */
    fun clearInlineError() {
        if (_uiState.value.inlineError != null) {
            _uiState.update { it.copy(inlineError = null) }
        }
    }

    /**
     * Exports the chat history for the given session as a JSON document and emits a
     * [ChatExportPayload] through [exportEvents]. The UI layer is responsible for handing
     * the payload to the Android share sheet via [android.content.Intent.ACTION_SEND].
     *
     * The exported JSON has the shape:
     * ```
     * {
     *   "sessionId": "...",
     *   "sessionName": "...",
     *   "exportedAt": 1700000000000,
     *   "messages": [
     *     { "role": "USER", "text": "...", "timestamp": 1700000000000 },
     *     ...
     *   ]
     * }
     * ```
     *
     * @param sessionId The ID of the session to export.
     */
    fun exportChat(sessionId: String) {
        viewModelScope.launch {
            try {
                val messages = chatRepository.getMessagesForSession(sessionId).first()
                val session = chatRepository.getSessionById(sessionId)
                val sessionName = session?.name ?: "Chat"

                val messagesArray = JSONArray()
                messages.forEach { message ->
                    val item = JSONObject()
                        .put("role", message.role.name)
                        .put("text", message.content)
                        .put("timestamp", message.timestamp)
                    messagesArray.put(item)
                }
                val root = JSONObject()
                    .put("sessionId", sessionId)
                    .put("sessionName", sessionName)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("messages", messagesArray)

                _exportEvents.emit(ChatExportPayload(sessionName = sessionName, json = root.toString(2)))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to export chat: ${e.localizedMessage ?: "unknown error"}")
                }
            }
        }
    }

    /**
     * Imports a chat from the provided JSON string into a newly-created session.
     * Messages are parsed and stored via [ChatRepository], and the UI switches to the
     * newly created session on success.
     *
     * Accepted shapes are the one produced by [exportChat] (`{ messages: [...] }`) and
     * a bare top-level array of message objects.
     *
     * @param json The JSON content to import.
     */
    fun importChat(json: String) {
        viewModelScope.launch {
            try {
                val trimmed = json.trim()
                val messagesArray: JSONArray
                var importedSessionName = "Imported Chat"
                when {
                    trimmed.startsWith("{") -> {
                        val root = JSONObject(trimmed)
                        importedSessionName = root.optString("sessionName").takeIf { it.isNotBlank() }
                            ?: importedSessionName
                        messagesArray = root.optJSONArray("messages")
                            ?: throw JSONException("Missing 'messages' array")
                    }
                    trimmed.startsWith("[") -> {
                        messagesArray = JSONArray(trimmed)
                    }
                    else -> throw JSONException("Unsupported JSON root")
                }

                val newId = UUID.randomUUID().toString()
                chatRepository.saveSession(
                    ChatSession(
                        id = newId,
                        name = importedSessionName,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )

                for (i in 0 until messagesArray.length()) {
                    val item = messagesArray.getJSONObject(i)
                    val roleStr = item.optString("role").ifBlank { Role.USER.name }
                    val role = runCatching { Role.valueOf(roleStr) }.getOrDefault(Role.USER)
                    val text = item.optString("text")
                    val timestamp = item.optLong("timestamp", System.currentTimeMillis())
                    chatRepository.saveMessage(
                        ChatMessage(
                            sessionId = newId,
                            role = role,
                            content = text,
                            timestamp = timestamp,
                        ),
                    )
                }

                switchSession(newId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to import chat: ${e.localizedMessage ?: "invalid JSON"}")
                }
            }
        }
    }

    /**
     * Resumes the paused orchestrator cycle by approving or denying a tool execution.
     *
     * @param isApproved True to execute the tool, False to cancel.
     */
    fun resumeWithApproval(isApproved: Boolean) {
        val currentState = _uiState.value
        agentOrchestratorUseCase.resumeWithApproval(currentState.currentSessionId, isApproved)
    }

    /**
     * Forwards the user's reply to the suspended pipeline coroutine via
     * [ClarificationRepository.submitClarification] and updates the matching
     * [ClarificationCardUiModel] based on whether the repository accepted the reply.
     *
     * - When the repository returns `true` (the suspended coroutine consumed
     *   [answer]), the card flips to [ClarificationCardUiModel.Status.ANSWERED]
     *   showing what the user typed.
     * - When the repository returns `false` (the request already timed out,
     *   was already answered, or the id is unknown), the card flips to
     *   [ClarificationCardUiModel.Status.TIMED_OUT] showing the default answer the
     *   pipeline actually consumed. This prevents "You answered: …" from being
     *   shown when the agent in fact moved on with a different value.
     *
     * No-op if [requestId] does not match any currently pending card.
     *
     * @param requestId The id of the clarification card being answered.
     * @param answer The user's reply text (option label or free-form input).
     */
    fun submitClarification(requestId: String, answer: String) {
        val matched = _uiState.value.clarificationCards.firstOrNull {
            it.id == requestId && it.status == ClarificationCardUiModel.Status.PENDING
        } ?: return

        viewModelScope.launch {
            val accepted = clarificationRepository.submitClarification(matched.id, answer)
            _uiState.update { current ->
                current.copy(
                    clarificationCards = current.clarificationCards.map { card ->
                        if (card.id == requestId && card.status == ClarificationCardUiModel.Status.PENDING) {
                            if (accepted) {
                                card.copy(
                                    status = ClarificationCardUiModel.Status.ANSWERED,
                                    answer = answer,
                                )
                            } else {
                                card.copy(
                                    status = ClarificationCardUiModel.Status.TIMED_OUT,
                                    answer = card.options?.firstOrNull().orEmpty(),
                                )
                            }
                        } else {
                            card
                        }
                    },
                )
            }
        }
    }

    /**
     * Marks a clarification card as timed out from the UI's perspective.
     *
     * The repository owns the authoritative timeout (`withTimeout`); this call only
     * updates the visual state of the card so the user sees that the agent fell back
     * to a default answer. It deliberately does NOT call the repository — the
     * suspended pipeline coroutine resumes on its own when the repository's timer
     * fires, and double-submitting would race against that resumption.
     *
     * @param requestId The id of the clarification card whose visual countdown elapsed.
     * @param defaultAnswer The default answer the agent will receive (first option, or
     *   empty string for free-form requests).
     */
    fun markClarificationTimedOut(requestId: String, defaultAnswer: String) {
        _uiState.update { current ->
            current.copy(
                clarificationCards = current.clarificationCards.map { card ->
                    if (card.id == requestId && card.status == ClarificationCardUiModel.Status.PENDING) {
                        card.copy(status = ClarificationCardUiModel.Status.TIMED_OUT, answer = defaultAnswer)
                    } else {
                        card
                    }
                },
            )
        }
    }

    /**
     * Opens the chat-settings dialog (pipeline rebind for the active chat).
     * Pre-selects the currently bound pipeline so an accidental "Save" is a
     * no-op.
     */
    fun openChatSettings() {
        val state = _uiState.value
        val currentBoundId = state.sessions
            .firstOrNull { it.id == state.currentSessionId }
            ?.pipelineId
        _uiState.update {
            it.copy(chatSettingsDialog = ChatSettingsDialogState(selectedPipelineId = currentBoundId))
        }
    }

    /**
     * Updates the highlighted pipeline inside the open chat-settings dialog
     * without persisting anything yet.
     */
    fun updateChatSettingsSelection(pipelineId: String?) {
        _uiState.update { state ->
            state.chatSettingsDialog?.let { dialog ->
                state.copy(chatSettingsDialog = dialog.copy(selectedPipelineId = pipelineId))
            } ?: state
        }
    }

    /**
     * Closes the chat-settings dialog without applying the selection.
     */
    fun dismissChatSettings() {
        _uiState.update { it.copy(chatSettingsDialog = null) }
    }

    /**
     * Confirms the chat-settings dialog: writes the chosen pipeline id (or
     * `null` for default) into the active session.
     *
     * If a generation is currently in flight, instead of mutating the
     * session immediately the ViewModel raises a `PipelineSwitchConfirm`
     * dialog asking the user whether to cancel generation and switch, or
     * wait. This implements UX option (a) agreed in the plan.
     */
    fun confirmChatSettings() {
        val state = _uiState.value
        val dialog = state.chatSettingsDialog ?: return
        val targetId = dialog.selectedPipelineId
        val currentBoundId = state.sessions
            .firstOrNull { it.id == state.currentSessionId }
            ?.pipelineId

        if (targetId == currentBoundId) {
            _uiState.update { it.copy(chatSettingsDialog = null) }
            return
        }

        if (state.isGenerating) {
            _uiState.update {
                it.copy(
                    chatSettingsDialog = null,
                    pipelineSwitchConfirm = PipelineSwitchConfirmState(targetPipelineId = targetId),
                )
            }
            return
        }

        applySessionPipeline(targetId)
        _uiState.update { it.copy(chatSettingsDialog = null) }
    }

    /**
     * Resolves the pipeline-switch confirmation: cancels the in-flight
     * generation and applies the requested pipeline id.
     */
    fun confirmPipelineSwitchCancelGeneration() {
        val target = _uiState.value.pipelineSwitchConfirm?.targetPipelineId
        stopGeneration()
        applySessionPipeline(target)
        _uiState.update { it.copy(pipelineSwitchConfirm = null) }
    }

    /**
     * Resolves the pipeline-switch confirmation by waiting: simply dismisses
     * the dialog without changing anything. The user may try again once
     * generation has settled.
     */
    fun dismissPipelineSwitchConfirm() {
        _uiState.update { it.copy(pipelineSwitchConfirm = null) }
    }

    /**
     * Persists the new pipeline binding for the currently active session and
     * refreshes the TopAppBar subtitle. No-op when no chat is loaded.
     */
    private fun applySessionPipeline(pipelineId: String?) {
        viewModelScope.launch {
            val state = _uiState.value
            val session = state.sessions.firstOrNull { it.id == state.currentSessionId } ?: return@launch
            chatRepository.saveSession(session.copy(pipelineId = pipelineId))
        }
    }

    /**
     * Clears the one-shot deleted-pipeline fallback Snackbar after the UI has
     * displayed it.
     */
    fun clearPipelineFallback() {
        if (_uiState.value.pipelineFallbackMessage != null) {
            _uiState.update { it.copy(pipelineFallbackMessage = null) }
        }
    }

    /**
     * Appends a UI card for [request] to [existing], deduplicating by request id so the
     * same `AwaitingClarification` state replayed by re-collection (e.g. after a
     * recomposition or state restoration) does not create a duplicate card.
     */
    private fun appendClarificationCard(
        existing: List<ClarificationCardUiModel>,
        request: ClarificationRequest,
    ): List<ClarificationCardUiModel> {
        if (existing.any { it.id == request.id }) return existing
        return existing + ClarificationCardUiModel(
            id = request.id,
            question = request.question,
            options = request.options,
            timeoutMs = request.timeoutMs,
            startedAtMs = SystemClock.uptimeMillis(),
        )
    }
}
