package ai.agent.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ai.agent.android.data.local.models.ChatMessageEntity
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
    suspend fun deleteSession(sessionId: String)
}
