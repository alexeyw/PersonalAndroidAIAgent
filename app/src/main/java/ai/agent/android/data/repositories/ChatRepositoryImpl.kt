package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.ChatDao
import ai.agent.android.data.local.dao.TraceStepDao
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
 * Caches the most recently seen [cachedSessionId] to avoid an N+1 SELECT pattern during
 * token streaming: once a session is confirmed to exist, subsequent [saveMessage] calls for
 * the same session skip the [ChatDao.getSessionById] round-trip and update only the timestamp.
 *
 * @property chatDao The Data Access Object for chat messages.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(private val chatDao: ChatDao, private val traceStepDao: TraceStepDao) :
    ChatRepository {

    @Volatile
    private var cachedSessionId: String? = null

    override suspend fun saveMessage(message: ChatMessage) {
        chatDao.insertMessage(message.toEntity())

        if (cachedSessionId == message.sessionId) {
            chatDao.updateSessionTimestamp(message.sessionId, message.timestamp)
        } else {
            val existingSession = chatDao.getSessionById(message.sessionId)
            if (existingSession != null) {
                chatDao.updateSession(existingSession.copy(updatedAt = message.timestamp))
            } else {
                chatDao.insertSession(
                    ai.agent.android.data.local.models.ChatSessionEntity(
                        id = message.sessionId,
                        name = "Chat " + message.sessionId.take(NEW_SESSION_NAME_SUFFIX_LENGTH),
                        updatedAt = message.timestamp,
                    ),
                )
            }
            cachedSessionId = message.sessionId
        }
    }

    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.getMessagesBySessionId(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getDisplayMessagesForSession(sessionId: String): Flow<List<ChatMessage>> =
        chatDao.getDisplayMessagesBySessionId(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun setMessageStarred(messageId: Long, starred: Boolean) {
        chatDao.setMessageStarred(messageId, starred)
    }

    override fun getStarredMessages(): Flow<List<ChatMessage>> = chatDao.getStarredMessages().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSessionMessages(sessionId)
        chatDao.deleteSession(sessionId)
    }

    override suspend fun getAllSessions(): List<String> = chatDao.getAllSessions()

    override suspend fun deleteMessage(messageId: Long) {
        chatDao.deleteMessageById(messageId)
    }

    override fun getRecentSystemMessages(limit: Int): Flow<List<ChatMessage>> =
        chatDao.getRecentMessagesByRole("SYSTEM", limit).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun saveSession(session: ChatSession) {
        // Single round-trip via Room's @Upsert — eliminates the SELECT + INSERT/UPDATE
        // N+1 pattern previously used here. The DAO conflicts on primary key (`id`).
        chatDao.upsertSession(session.toEntity())
    }

    override fun getSessionsFlow(): Flow<List<ChatSession>> = chatDao.getSessionsFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun getSessionById(id: String): ChatSession? = chatDao.getSessionById(id)?.toDomain()

    override suspend fun saveTraceStep(
        sessionId: String,
        nodeName: String,
        outputText: String,
        durationMs: Long,
        tokenCount: Int?,
    ) {
        traceStepDao.insertTraceStep(
            ai.agent.android.data.local.models.TraceStepEntity(
                sessionId = sessionId,
                nodeName = nodeName,
                outputText = outputText,
                timestamp = System.currentTimeMillis(),
                durationMs = durationMs,
                tokenCount = tokenCount,
            ),
        )
    }

    override fun getTraceSteps(
        sessionId: String,
    ): Flow<List<ai.agent.android.domain.models.AgentOrchestratorState.TraceStep>> =
        traceStepDao.getTraceStepsForSession(sessionId).map { entities ->
            entities.map {
                ai.agent.android.domain.models.AgentOrchestratorState.TraceStep(
                    nodeName = it.nodeName,
                    outputText = it.outputText,
                    durationMs = it.durationMs,
                    tokenCount = it.tokenCount,
                )
            }
        }

    private companion object {
        /**
         * Number of leading characters of a session UUID embedded into the auto-generated
         * display name (e.g. `Chat 5b7a2c`) shown when the user has not renamed the chat.
         */
        const val NEW_SESSION_NAME_SUFFIX_LENGTH: Int = 6
    }
}
