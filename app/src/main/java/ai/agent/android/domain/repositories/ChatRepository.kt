package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing chat history.
 */
interface ChatRepository {
    /**
     * Saves a new chat message.
     *
     * @param message The [ChatMessage] to save.
     */
    suspend fun saveMessage(message: ChatMessage)

    /**
     * Retrieves the history of messages for a given session.
     *
     * @param sessionId The unique ID of the chat session.
     * @return A [Flow] emitting the list of [ChatMessage] ordered by time.
     */
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    /**
     * Deletes all messages associated with the given session.
     *
     * @param sessionId The unique ID of the chat session to delete.
     */
    suspend fun deleteSession(sessionId: String)
}
