package ai.agent.android.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val loadModelUseCase: LoadModelUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        initializeSession()
    }

    /**
     * Initializes the chat session by either restoring the last active session ID
     * or generating a new one if none exists.
     * Also verifies that the LLM model is loaded and ready.
     */
    private fun initializeSession() {
        viewModelScope.launch {
            // Check if model is loaded by attempting to load it (LoadModelUseCase handles already loaded state if needed,
            // but primarily it ensures the current active model is initialized in the engine).
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
                newId
            } else {
                savedSessionId
            }
            
            _uiState.update { it.copy(currentSessionId = sessionId) }
            loadMessages(sessionId)
        }
    }

    /**
     * Loads the chat messages for the given session ID and observes changes.
     *
     * @param sessionId The ID of the chat session to load.
     */
    private fun loadMessages(sessionId: String) {
        viewModelScope.launch {
            chatRepository.getMessagesForSession(sessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
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
}
