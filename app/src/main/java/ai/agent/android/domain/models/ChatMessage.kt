package ai.agent.android.domain.models

/**
 * Domain model representing a single chat message.
 *
 * @property id The unique identifier of the message. Null if not yet saved.
 * @property sessionId The ID of the chat session this message belongs to.
 * @property role The role of the sender (e.g., USER or AGENT).
 * @property content The text content of the message.
 * @property timestamp The time the message was created, in milliseconds since epoch.
 */
data class ChatMessage(
    val id: Long? = null,
    val sessionId: String,
    val role: Role,
    val content: String,
    val timestamp: Long
)
