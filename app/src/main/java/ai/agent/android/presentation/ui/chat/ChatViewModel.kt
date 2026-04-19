package ai.agent.android.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
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
        initializeSession()
        observeMaxContextSize()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            chatRepository.getSessionsFlow().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
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
     * Creates a new chat session and switches to it.
     */
    fun createNewSession() {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            chatRepository.saveSession(
                ChatSession(
                    id = newId,
                    name = "New Chat",
                    updatedAt = System.currentTimeMillis()
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
            _uiState.update { it.copy(currentSessionId = sessionId, isGenerating = false, orchestratorState = null) }
            loadMessages(sessionId)
            
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
                    createNewSession()
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
                )
            }

            agentOrchestratorUseCase(currentState.currentSessionId, prompt)
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
                        current.copy(
                            orchestratorState = state,
                            currentStep = when {
                                isTerminal -> null
                                state is AgentOrchestratorState.PipelineStage -> state.stepInfo
                                else -> current.currentStep
                            },
                            pipelineTrace = if (state is AgentOrchestratorState.PipelineTrace) state.steps else current.pipelineTrace,
                            isGenerating = if (isTerminal) false else current.isGenerating,
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
        _uiState.update { it.copy(isGenerating = false, currentStep = null, orchestratorState = null) }
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
}
