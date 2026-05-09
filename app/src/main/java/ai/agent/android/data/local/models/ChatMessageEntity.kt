package ai.agent.android.data.local.models

import androidx.room.ColumnInfo
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
 * @property isFinal Whether the message is part of the user-facing chat history.
 *   Intermediate node outputs are persisted with `false` so they remain available
 *   for the agent console while staying out of the main chat list. Defaults to
 *   `true`; the migration backfills `1` for pre-existing rows so legacy chats
 *   continue to render unchanged.
 * @property isStarred Whether the user has marked this message as a favourite.
 *   Defaults to `false`; surfaced via the chat-screen "starred only" filter.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "1")
    val isFinal: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isStarred: Boolean = false,
)
