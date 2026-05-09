package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a chat session in the local database.
 *
 * @property id The unique identifier for the session.
 * @property name The display name of the chat session.
 * @property updatedAt The timestamp of the last activity in this session, in milliseconds since epoch.
 * @property pipelineId Identifier of the pipeline bound to the chat (Phase 17.2).
 *   `null` means "use the application default pipeline"; this is the value
 *   produced by `MIGRATION_18_19` for every pre-existing row so legacy chats
 *   continue to behave exactly as before.
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val updatedAt: Long,
    val pipelineId: String? = null,
)
