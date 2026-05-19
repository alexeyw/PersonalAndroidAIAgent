package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.ChatDao
import ai.agent.android.data.local.dao.TraceStepDao
import ai.agent.android.data.local.models.ChatSessionEntity
import ai.agent.android.data.local.models.TraceStepEntity
import ai.agent.android.data.mappers.toDomain
import ai.agent.android.data.mappers.toEntity
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
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
                    ChatSessionEntity(
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

    override suspend fun renameSession(sessionId: String, newName: String) {
        chatDao.renameSession(sessionId, newName)
    }

    override suspend fun setSessionFavorite(sessionId: String, favorite: Boolean) {
        chatDao.setSessionStarred(sessionId, favorite)
    }

    override suspend fun importChat(json: String): String {
        val trimmed = json.trim()
        var sessionName = DEFAULT_IMPORTED_CHAT_NAME
        val messagesArray: JSONArray = when {
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                sessionName = root.optString("sessionName").takeIf { it.isNotBlank() } ?: sessionName
                root.optJSONArray("messages") ?: throw JSONException("Missing 'messages' array")
            }
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> throw JSONException("Unsupported JSON root")
        }

        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatDao.upsertSession(
            ChatSessionEntity(id = newId, name = sessionName, updatedAt = now),
        )
        for (i in 0 until messagesArray.length()) {
            val item = messagesArray.getJSONObject(i)
            val roleStr = item.optString("role").ifBlank { Role.USER.name }
            val role = runCatching { Role.valueOf(roleStr) }.getOrDefault(Role.USER)
            val text = item.optString("text")
            val timestamp = item.optLong("timestamp", now)
            chatDao.insertMessage(
                ChatMessage(
                    sessionId = newId,
                    role = role,
                    content = text,
                    timestamp = timestamp,
                ).toEntity(),
            )
        }
        return newId
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
            TraceStepEntity(
                sessionId = sessionId,
                nodeName = nodeName,
                outputText = outputText,
                timestamp = System.currentTimeMillis(),
                durationMs = durationMs,
                tokenCount = tokenCount,
            ),
        )
    }

    override fun getTraceSteps(sessionId: String): Flow<List<AgentOrchestratorState.TraceStep>> =
        traceStepDao.getTraceStepsForSession(sessionId).map { entities ->
            entities.map {
                AgentOrchestratorState.TraceStep(
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

        /**
         * Default name assigned to a chat session created via [importChat] when the
         * incoming document carries no `sessionName` field. Mirrors the legacy
         * `ChatViewModel.DEFAULT_IMPORTED_CHAT_NAME` so behaviour is preserved.
         */
        const val DEFAULT_IMPORTED_CHAT_NAME: String = "Imported Chat"
    }
}
