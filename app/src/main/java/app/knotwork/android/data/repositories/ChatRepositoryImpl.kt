package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.ChatDao
import app.knotwork.android.data.local.models.ChatSessionEntity
import app.knotwork.android.data.mappers.toDomain
import app.knotwork.android.data.mappers.toEntity
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.repositories.ChatRepository
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
class ChatRepositoryImpl @Inject constructor(private val chatDao: ChatDao) : ChatRepository {

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
        // Single transaction: messages + pipeline-run records + session row
        // (trace steps cascade via FK) — a crash mid-delete can never leave a
        // half-deleted session behind.
        chatDao.deleteSessionCompletely(sessionId)
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
        // Single round-trip via Room's @Upsert. The DAO conflicts on primary key (`id`).
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
