package ai.agent.android.presentation.ui.chat

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage

/**
 * Data class representing the state of the Chat UI.
 *
 * @property messages The list of chat messages to display.
 * @property isGenerating Indicates whether the agent is currently generating a response.
 * @property orchestratorState The current state of the agent orchestrator, if active.
 * @property currentSessionId The ID of the active chat session.
 * @property errorMessage An optional error message to display to the user.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val orchestratorState: AgentOrchestratorState? = null,
    val currentSessionId: String = "",
    val errorMessage: String? = null
)