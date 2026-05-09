package ai.agent.android.presentation.ui.chat

/**
 * Lightweight UI projection of a pipeline used by the chat screen for the
 * new-chat selector, the chat-settings dialog and the TopAppBar subtitle.
 *
 * Only the bits the chat UI needs (id and display name) are carried here so
 * that observing `PipelineRepository.getAllPipelines()` in `ChatViewModel`
 * does not pull entire `PipelineGraph` payloads (with nodes and connections)
 * into the UI state.
 *
 * @property id Pipeline identifier matching `ChatSession.pipelineId`.
 * @property name Display name shown to the user.
 */
data class PipelineSummary(
    val id: String,
    val name: String,
)
