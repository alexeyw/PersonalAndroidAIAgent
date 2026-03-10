package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single chat message in the local database.
 *
 * @property id The unique identifier for the message (auto-generated).
 * @property sessionId The ID of the chat session this message belongs to.
 * @property role The role of the sender (e.g., USER, AGENT, SYSTEM).
 * @property content The text content of the message.
 * @property timestamp The time the message was created, in milliseconds since epoch.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long
)
