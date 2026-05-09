package ai.agent.android.presentation.ui.chat

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ConsoleEvent

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
 * @property clarificationCards Cards rendered inline in the chat timeline whenever the
 *   orchestrator emits an [AgentOrchestratorState.AwaitingClarification] state. Each
 *   card tracks its own pending/answered/timed-out lifecycle and persists in the UI
 *   state for the duration of the chat session so the user can see what they answered.
 * @property availablePipelines Lightweight projection of every pipeline currently
 *   stored in the library, observed from `PipelineRepository.getAllPipelines()`.
 *   Drives the new-chat pipeline selector and the chat-settings dialog.
 * @property currentPipelineName Display name of the pipeline bound to the current
 *   chat (or of the default pipeline when the session has `pipelineId == null`).
 *   Rendered as the TopAppBar subtitle. `null` when no pipelines exist at all.
 * @property newChatPipelinePrompt When non-null the UI shows a `ModalBottomSheet`
 *   asking the user which pipeline to attach to a brand-new chat.
 * @property chatSettingsDialog When non-null the chat-settings dialog is open and
 *   allows the user to rebind the active chat to a different pipeline.
 * @property pipelineSwitchConfirm When non-null the user has requested a pipeline
 *   change while a generation is in flight; the dialog asks whether to cancel
 *   the in-flight generation and switch.
 * @property pipelineFallbackMessage One-shot Snackbar message emitted exactly once
 *   when the chat's bound pipeline is detected as deleted and the chat has been
 *   silently rebound to the default pipeline. Cleared via `clearPipelineFallback()`.
 * @property snackbarMessage One-shot transient text surfaced as a Snackbar (e.g. the
 *   "Copied" feedback after the user copies a message via the long-press menu).
 *   Cleared by the UI via `consumeSnackbar()` after the auto-dismiss timeout.
 * @property showStarredOnly When `true`, the chat list is sourced from
 *   `getStarredMessages()` and shows every starred message across all sessions.
 *   Toggled from the TopAppBar action bar.
 * @property consoleLines Append-only log of [ConsoleEvent]s for the current
 *   pipeline run. Populated from [AgentOrchestratorState.ConsoleLog]
 *   emissions; cleared on new send and on session switch. Drives the
 *   collapsed mini-console (Phase 17.4) and the full bottom-sheet log
 *   (Phase 17.5).
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
    val clarificationCards: List<ClarificationCardUiModel> = emptyList(),
    val availablePipelines: List<PipelineSummary> = emptyList(),
    val currentPipelineName: String? = null,
    val newChatPipelinePrompt: NewChatPipelinePrompt? = null,
    val chatSettingsDialog: ChatSettingsDialogState? = null,
    val pipelineSwitchConfirm: PipelineSwitchConfirmState? = null,
    val pipelineFallbackMessage: String? = null,
    val snackbarMessage: String? = null,
    val showStarredOnly: Boolean = false,
    val consoleLines: List<ConsoleEvent> = emptyList(),
)

/**
 * Carries the UI-side selection state for the new-chat pipeline selector
 * `ModalBottomSheet`. Decoupled from the persisted session so the user can
 * freely change the highlight without committing until they tap "Create".
 *
 * @property preselectedPipelineId Id of the pipeline highlighted as default
 *   when the sheet first appears (the application-wide default). `null`
 *   means the sheet opens with "Use default" highlighted.
 */
data class NewChatPipelinePrompt(
    val preselectedPipelineId: String?,
)

/**
 * State of the chat-settings dialog opened from the TopAppBar `⋮` menu.
 *
 * @property selectedPipelineId Currently highlighted pipeline id in the
 *   dialog (`null` for "Use default"). Initialised from the active chat's
 *   `pipelineId` and updated as the user makes a different pick before
 *   confirming.
 */
data class ChatSettingsDialogState(
    val selectedPipelineId: String?,
)

/**
 * Pending pipeline-switch confirmation surfaced when the user picks a
 * different pipeline (either via the chat-settings dialog or another
 * entry-point) while a generation is currently in flight.
 *
 * Following the agreed UX (option A): the dialog offers exactly two actions
 * — "Cancel and switch" (which calls `stopGeneration()` and applies the
 * change) or "Wait" (which dismisses the dialog without changing anything,
 * letting the user retry once generation completes).
 *
 * @property targetPipelineId The pipeline the user wants to switch to
 *   (`null` = the application default).
 */
data class PipelineSwitchConfirmState(
    val targetPipelineId: String?,
)
