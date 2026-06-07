package app.knotwork.android.domain.models

/**
 * Domain model representing a single chat message.
 *
 * @property id The unique identifier of the message. Null if not yet saved.
 * @property sessionId The ID of the chat session this message belongs to.
 * @property role The role of the sender (e.g., USER or AGENT).
 * @property content The text content of the message.
 * @property timestamp The time the message was created, in milliseconds since epoch.
 * @property isFinal Whether the message is a user-facing final message (USER input or final
 *   AGENT answer) that should appear in the main chat list. Intermediate node outputs
 *   (tool observations, internal SYSTEM logs) are persisted with `isFinal = false` so they
 *   are kept for the agent console while staying out of the main history.
 *   Defaults to `true` to preserve behavior for legacy save sites.
 * @property isStarred Whether the user has saved (starred) this message. Starred messages
 *   are surfaced via the chat-screen "starred only" filter.
 */
data class ChatMessage(
    val id: Long? = null,
    val sessionId: String,
    val role: Role,
    val content: String,
    val timestamp: Long,
    val isFinal: Boolean = true,
    val isStarred: Boolean = false,
)
