package ai.agent.android.domain.models

/**
 * Domain model representing a chat session.
 *
 * @property id The unique identifier for the session.
 * @property name The display name of the chat session.
 * @property updatedAt The timestamp of the last activity in this session.
 */
data class ChatSession(
    val id: String,
    val name: String,
    val updatedAt: Long
)
