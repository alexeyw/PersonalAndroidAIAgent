package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Room entity holding the compressed-history summary for a chat session
 * (`chat_history_summaries` table, added v37 → v38).
 *
 * One row per session (the [sessionId] is the primary key, 1:1 with
 * `chat_sessions`). A foreign key onto `chat_sessions(id)` with
 * `ON DELETE CASCADE` means the summary is removed automatically when its
 * session is deleted, so a stale summary can never outlive its conversation.
 *
 * @property sessionId Id of the chat session this summary belongs to (PK + FK).
 * @property summary Dense prose summary of the covered prefix of the history.
 * @property coveredMessageCount Number of leading session messages (in
 *   unfiltered chronological order) the [summary] represents — the incremental
 *   compression cursor.
 * @property updatedAt Wall-clock time the summary was last computed, in
 *   milliseconds since epoch.
 */
@Entity(
    tableName = "chat_history_summaries",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChatHistorySummaryEntity(
    @PrimaryKey
    val sessionId: String,
    val summary: String,
    val coveredMessageCount: Int,
    val updatedAt: Long,
)
