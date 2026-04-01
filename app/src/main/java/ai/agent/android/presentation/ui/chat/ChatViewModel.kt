package ai.agent.android.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.agent.android.presentation.state.ActiveSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val activeSessionTracker: ActiveSessionTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    /**
     * The current UI state of the Chat screen.
     */
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null

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
            activeSessionTracker.setActiveSessionId(sessionId)
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
            activeSessionTracker.setActiveSessionId(sessionId)
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

        viewModelScope.launch {
            // Auto-rename logic for new chats
            val currentSession = currentState.sessions.find { it.id == currentState.currentSessionId }
            if (currentSession?.name == "New Chat") {
                val newName = if (prompt.length > 20) prompt.take(20) + "..." else prompt
                renameSession(currentState.currentSessionId, newName)
            }

            _uiState.update { it.copy(isGenerating = true, errorMessage = null, orchestratorState = null) }
            
            agentOrchestratorUseCase(currentState.currentSessionId, prompt)
                .catch { error ->
                    _uiState.update { 
                        it.copy(
                            isGenerating = false, 
                            errorMessage = error.message ?: "An unexpected error occurred",
                            orchestratorState = AgentOrchestratorState.Error(error.message ?: "Unknown error")
                        ) 
                    }
                }
                .collect { state ->
                    _uiState.update { it.copy(orchestratorState = state) }
                    
                    if (state is AgentOrchestratorState.Completed || state is AgentOrchestratorState.Error) {
                        _uiState.update { it.copy(isGenerating = false) }
                    }
                }
        }
    }
    
    /**
     * Clears the current error message from the UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
