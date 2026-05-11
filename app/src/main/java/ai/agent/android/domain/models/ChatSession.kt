package ai.agent.android.domain.models

/**
 * Domain model representing a chat session.
 *
 * @property id The unique identifier for the session.
 * @property name The display name of the chat session.
 * @property updatedAt The timestamp of the last activity in this session.
 * @property pipelineId Identifier of the pipeline bound to this chat. `null` means
 *   the session uses the application-wide default pipeline (the first pipeline
 *   returned by `PipelineRepository.getAllPipelines()`), preserving the
 *   pre-Phase-17.2 behaviour for legacy sessions and any chat that does not
 *   explicitly opt into a specific pipeline.
 */
data class ChatSession(val id: String, val name: String, val updatedAt: Long, val pipelineId: String? = null)
