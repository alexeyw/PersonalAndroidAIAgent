package app.knotwork.android.data.local.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a chat session in the local database.
 *
 * @property id The unique identifier for the session.
 * @property name The display name of the chat session.
 * @property updatedAt The timestamp of the last activity in this session, in milliseconds since epoch.
 * @property pipelineId Identifier of the pipeline bound to the chat.
 *   `null` means "use the application default pipeline"; this is the value
 *   produced by `MIGRATION_18_19` for every pre-existing row so legacy chats
 *   continue to behave exactly as before.
 * @property isStarred Whether the user has favorited this chat. Favorited
 *   chats sort to the top of the drawer thread list. Added via
 *   `MIGRATION_21_22` (default `0`).
 * @property isArchived Whether the user has archived this chat. Archived
 *   sessions are excluded from the main thread list and are reachable only
 *   through the archive surface; the row itself and all of its messages are
 *   kept intact. Added via `MIGRATION_53_54` (default `0`), so every
 *   pre-existing session upgrades to "not archived".
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val updatedAt: Long,
    val pipelineId: String? = null,
    @ColumnInfo(name = "isStarred", defaultValue = "0")
    val isStarred: Boolean = false,
    @ColumnInfo(name = "isArchived", defaultValue = "0")
    val isArchived: Boolean = false,
)
