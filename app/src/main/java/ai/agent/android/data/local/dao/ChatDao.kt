package ai.agent.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import ai.agent.android.data.local.models.ChatMessageEntity
import ai.agent.android.data.local.models.ChatSessionEntity
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
     * Deletes a chat session by its ID.
     *
     * @param id The ID of the session to delete.
     */
    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    /**
     * Updates only the [updatedAt] timestamp of an existing session without a SELECT round-trip.
     * Used by [ai.agent.android.data.repositories.ChatRepositoryImpl] to avoid an N+1 query
     * pattern during streaming, where the session is already known to exist.
     *
     * @param sessionId The ID of the session to update.
     * @param timestamp The new timestamp in milliseconds since epoch.
     */
    @Query("UPDATE chat_sessions SET updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: String, timestamp: Long)

    /**
     * Inserts the given session, or updates it if a row with the same primary key already
     * exists. Replaces the SELECT-then-INSERT/UPDATE pattern previously used in
     * [ai.agent.android.data.repositories.ChatRepositoryImpl.saveSession], collapsing two
     * round-trips into one.
     *
     * @param session The [ChatSessionEntity] to persist.
     */
    @Upsert
    suspend fun upsertSession(session: ChatSessionEntity)
}
