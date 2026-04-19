package ai.agent.android.presentation.ui.chat

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession

/**
 * Data class representing the state of the Chat UI.
 *
 * @property messages The list of chat messages to display.
 * @property isGenerating Indicates whether the agent is currently generating a response.
 * @property orchestratorState The current state of the agent orchestrator, if active.
 * @property currentSessionId The ID of the active chat session.
 * @property errorMessage An optional error message to display to the user (transient, shown as a Snackbar).
 * @property inlineError An optional inline error displayed as a persistent banner above the input bar.
 *                       Used e.g. when the user tries to send a message while no model is loaded.
 * @property sessions The list of available chat sessions.
 * @property contextSize The calculated size of the current prompt context window (e.g., character count).
 * @property maxContextSize The maximum allowed size for the prompt context window.
 * @property pipelineTrace The list of pipeline trace steps accumulated during execution.
 * @property currentStep Progress metadata for the pipeline step currently being executed, or null when idle.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val orchestratorState: AgentOrchestratorState? = null,
    val currentSessionId: String = "",
    val errorMessage: String? = null,
    val inlineError: String? = null,
    val sessions: List<ChatSession> = emptyList(),
    val contextSize: Int = 0,
    val maxContextSize: Int = 0,
    val pipelineTrace: List<AgentOrchestratorState.TraceStep> = emptyList(),
    val currentStep: AgentOrchestratorState.PipelineStepInfo? = null,
)