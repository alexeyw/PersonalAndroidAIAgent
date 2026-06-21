package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.knotwork.android.data.local.models.ChatMessageEntity
import app.knotwork.android.data.local.models.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chat messages.
 * Provides methods to interact with the chat_messages table in the Room database.
 */
@Dao
interface ChatDao {
    /**
     * Inserts a new chat message into the database.
     *
     * @param message The [ChatMessageEntity] to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    /**
     * Retrieves all chat messages for a specific session as a stream.
     * Messages are ordered by timestamp in ascending order.
     *
     * @param sessionId The ID of the session to retrieve messages for.
     * @return A [Flow] emitting a list of [ChatMessageEntity] ordered by time.
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySessionId(sessionId: String): Flow<List<ChatMessageEntity>>

    /**
     * Retrieves only the user-facing (final) chat messages for a session, ordered by time.
     * Intermediate node outputs (`isFinal = 0`) are excluded — they remain queryable via
     * [getMessagesBySessionId] for the agent console / context-window logic.
     *
     * @param sessionId The ID of the session to retrieve messages for.
     * @return A [Flow] emitting a list of final [ChatMessageEntity]s ordered by time.
     */
    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE sessionId = :sessionId AND isFinal = 1 " +
            "ORDER BY timestamp ASC",
    )
    fun getDisplayMessagesBySessionId(sessionId: String): Flow<List<ChatMessageEntity>>

    /**
     * Updates the starred state of a single message.
     *
     * @param messageId The id of the row to update.
     * @param starred `true` to mark as starred, `false` to unstar.
     */
    @Query("UPDATE chat_messages SET isStarred = :starred WHERE id = :messageId")
    suspend fun setMessageStarred(messageId: Long, starred: Boolean)

    /**
     * Retrieves every starred message across all sessions, ordered chronologically
     * (ascending timestamp) to match the main chat list — keeps `ChatScreen`'s
     * "scroll-to-last" auto-scroll consistent across filter toggles.
     *
     * @return A [Flow] emitting the current list of starred [ChatMessageEntity]s.
     */
    @Query("SELECT * FROM chat_messages WHERE isStarred = 1 ORDER BY timestamp ASC")
    fun getStarredMessages(): Flow<List<ChatMessageEntity>>

    /**
     * Deletes all chat messages associated with a specific session.
     *
     * @param sessionId The ID of the session to delete.
     */
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteSessionMessages(sessionId: String)

    /**
     * Retrieves a list of all distinct chat session IDs from messages.
     * Deprecated: Use getSessionsFlow() instead.
     *
     * @return A list of unique session IDs.
     */
    @Query("SELECT DISTINCT sessionId FROM chat_messages")
    suspend fun getAllSessions(): List<String>

    /**
     * Deletes a specific chat message by its ID.
     *
     * @param messageId The ID of the message to delete.
     */
    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    /**
     * Returns the attachment path of a single message, or `null` when the
     * message has no attachment or does not exist. Used by the repository to
     * locate the on-disk image file before deleting the message row, so the
     * file can be cleaned up alongside it.
     *
     * @param messageId The ID of the message.
     * @return The store-relative attachment path, or `null`.
     */
    @Query("SELECT attachmentPath FROM chat_messages WHERE id = :messageId")
    suspend fun getAttachmentPathById(messageId: Long): String?

    /**
     * Returns the attachment paths of every message in a session that has one.
     * Used by the repository to delete the on-disk image files when a whole
     * session is removed.
     *
     * @param sessionId The ID of the session.
     * @return The store-relative attachment paths owned by the session.
     */
    @Query(
        "SELECT attachmentPath FROM chat_messages " +
            "WHERE sessionId = :sessionId AND attachmentPath IS NOT NULL",
    )
    suspend fun getAttachmentPathsForSession(sessionId: String): List<String>

    /**
     * Returns every attachment path still referenced by a chat message. The
     * attachment orphan-cleanup sweep diffs the files present in the attachment
     * store against this set and deletes the unreferenced remainder.
     *
     * @return The store-relative paths of all currently referenced attachments.
     */
    @Query("SELECT attachmentPath FROM chat_messages WHERE attachmentPath IS NOT NULL")
    suspend fun getAllAttachmentPaths(): List<String>

    /**
     * Retrieves recent messages of a specific role, used for monitoring logs.
     *
     * @param role The role of the messages to retrieve.
     * @param limit The maximum number of messages to retrieve.
     * @return A [Flow] emitting a list of recent [ChatMessageEntity].
     */
    @Query("SELECT * FROM chat_messages WHERE role = :role ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessagesByRole(role: String, limit: Int = 100): Flow<List<ChatMessageEntity>>

    /**
     * Inserts a new chat session into the database.
     *
     * @param session The [ChatSessionEntity] to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    /**
     * Updates an existing chat session.
     *
     * @param session The [ChatSessionEntity] to update.
     */
    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    /**
     * Retrieves all chat sessions ordered by the last update time (descending).
     *
     * @return A [Flow] emitting a list of [ChatSessionEntity].
     */
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getSessionsFlow(): Flow<List<ChatSessionEntity>>

    /**
     * Retrieves a specific chat session by its ID.
     *
     * @param id The ID of the session.
     * @return The [ChatSessionEntity] if found, null otherwise.
     */
    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): ChatSessionEntity?

    /**
     * Deletes **only** the `chat_sessions` row (its FK cascade reaches
     * `trace_steps`, but `chat_messages` has no FK onto sessions and is NOT
     * cascaded). This is a building block of [deleteSessionCompletely] and the
     * DAO cascade tests — **do not call it directly to discard a conversation**:
     * doing so orphans every message row of the session (no orphan sweep exists
     * for messages). Route all user-facing deletes through
     * [deleteSessionCompletely].
     *
     * @param id The ID of the session row to delete.
     */
    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    /**
     * Deletes every persistent pipeline-run record of [sessionId]. Lives here
     * (not in `PipelineRunDao`) because `pipeline_runs` deliberately has no
     * FK cascade onto `chat_sessions` — a run row may be created before its
     * session row exists — so run cleanup is part of the session-deletion
     * transaction instead.
     *
     * @param sessionId The ID of the deleted session.
     */
    @Query("DELETE FROM pipeline_runs WHERE sessionId = :sessionId")
    suspend fun deleteSessionRuns(sessionId: String)

    /**
     * Atomically deletes a session with everything it owns: its messages,
     * its pipeline-run records (no FK cascade — see [deleteSessionRuns]) and
     * the session row itself (whose deletion FK-cascades the trace steps).
     * The single transaction guarantees a process death mid-delete can never
     * leave a half-deleted session behind.
     *
     * If a pipeline run of this session is still executing, its record is
     * deleted with the session — subsequent lifecycle writes for that run
     * match zero rows and are deliberately tolerated: the user explicitly
     * discarded the conversation the run belongs to.
     *
     * @param sessionId The ID of the session to delete.
     */
    @Transaction
    suspend fun deleteSessionCompletely(sessionId: String) {
        deleteSessionMessages(sessionId)
        deleteSessionRuns(sessionId)
        deleteSession(sessionId)
    }

    /**
     * Updates only the [updatedAt] timestamp of an existing session without a SELECT round-trip.
     * Used by [app.knotwork.android.data.repositories.ChatRepositoryImpl] to avoid an N+1 query
     * pattern during streaming, where the session is already known to exist.
     *
     * @param sessionId The ID of the session to update.
     * @param timestamp The new timestamp in milliseconds since epoch.
     */
    @Query("UPDATE chat_sessions SET updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: String, timestamp: Long)

    /**
     * Inserts the given session, or updates it if a row with the same primary key already
     * exists. Collapses what would otherwise be a SELECT-then-INSERT/UPDATE into a single
     * round-trip.
     *
     * @param session The [ChatSessionEntity] to persist.
     */
    @Upsert
    suspend fun upsertSession(session: ChatSessionEntity)

    /**
     * Renames an existing session in place. No-op when no row matches [sessionId].
     *
     * @param sessionId The id of the session to rename.
     * @param newName The new display name to persist.
     */
    @Query("UPDATE chat_sessions SET name = :newName WHERE id = :sessionId")
    suspend fun renameSession(sessionId: String, newName: String)

    /**
     * Toggles the session-level favorite flag. No-op when no row matches [sessionId].
     *
     * @param sessionId The id of the session to update.
     * @param starred The new favorite flag to persist.
     */
    @Query("UPDATE chat_sessions SET isStarred = :starred WHERE id = :sessionId")
    suspend fun setSessionStarred(sessionId: String, starred: Boolean)
}
