package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.ChatDao
import ai.agent.android.data.mappers.toDomain
import ai.agent.android.data.mappers.toEntity
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.repositories.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ChatRepository] that uses a local Room database via [ChatDao].
 *
 * @property chatDao The Data Access Object for chat messages.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao
) : ChatRepository {

    override suspend fun saveMessage(message: ChatMessage) {
        chatDao.insertMessage(message.toEntity())
        
        // Auto-create or update session timestamp
        val existingSession = chatDao.getSessionById(message.sessionId)
        if (existingSession != null) {
            chatDao.updateSession(existingSession.copy(updatedAt = message.timestamp))
        } else {
            val sessionName = "Chat " + message.sessionId.take(6)
            chatDao.insertSession(
                ai.agent.android.data.local.models.ChatSessionEntity(
                    id = message.sessionId,
                    name = sessionName,
                    updatedAt = message.timestamp
                )
            )
        }
    }

    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesBySessionId(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSessionMessages(sessionId)
        chatDao.deleteSession(sessionId)
    }

    override suspend fun getAllSessions(): List<String> {
        return chatDao.getAllSessions()
    }

    override suspend fun deleteMessage(messageId: Long) {
        chatDao.deleteMessageById(messageId)
    }

    override fun getRecentSystemMessages(limit: Int): Flow<List<ChatMessage>> {
        return chatDao.getRecentMessagesByRole("SYSTEM", limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveSession(session: ChatSession) {
        val existing = chatDao.getSessionById(session.id)
        if (existing != null) {
            chatDao.updateSession(session.toEntity())
        } else {
            chatDao.insertSession(session.toEntity())
        }
    }

    override fun getSessionsFlow(): Flow<List<ChatSession>> {
        return chatDao.getSessionsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSessionById(id: String): ChatSession? {
        return chatDao.getSessionById(id)?.toDomain()
    }
}
