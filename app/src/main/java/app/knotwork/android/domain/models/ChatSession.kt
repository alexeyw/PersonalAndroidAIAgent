package app.knotwork.android.domain.models

/**
 * Domain model representing a chat session.
 *
 * @property id The unique identifier for the session.
 * @property name The display name of the chat session.
 * @property updatedAt The timestamp of the last activity in this session.
 * @property pipelineId Identifier of the pipeline bound to this chat. `null` means
 *   the session uses the application-wide default pipeline (the user-marked
 *   `SettingsRepository.defaultPipelineId`), preserving the default
 *   behaviour for legacy sessions and any chat that does not explicitly
 *   opt into a specific pipeline.
 * @property isStarred Whether the user has favorited this chat. Favorited
 *   chats sort to the top of the drawer thread list and render a small star
 *   indicator next to the title. Persisted in `chat_sessions.isStarred`
 *   (migration v21 → v22).
 */
data class ChatSession(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val pipelineId: String? = null,
    val isStarred: Boolean = false,
)
