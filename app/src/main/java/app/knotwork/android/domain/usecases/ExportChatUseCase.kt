package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * A chat serialised for hand-off out of the app.
 *
 * @property sessionName Human-readable name of the exported session, used as
 *   the share-sheet subject.
 * @property json Pretty-printed JSON document — the session metadata plus its
 *   messages as `role` / `text` / `timestamp` triples. Round-trips through
 *   [app.knotwork.android.domain.repositories.ChatRepository.importChat].
 */
data class ChatExportDocument(val sessionName: String, val json: String)

/**
 * Serialises one chat session into the transferable JSON document.
 *
 * Lives in `domain` rather than in the chat ViewModel because export is not a
 * property of the *open* chat: the archive surface exports a conversation it is
 * not showing, and archiving a chat must never put it out of reach of the
 * history-transfer path. Both callers therefore produce byte-identical
 * documents, and the shape is covered by one test suite instead of two.
 *
 * @property chatRepository Source of the session row and its messages.
 */
class ExportChatUseCase @Inject constructor(private val chatRepository: ChatRepository) {

    /**
     * Builds the export document for [sessionId].
     *
     * Archived sessions export exactly like active ones — the flag decides
     * which list a chat appears in, never what it is made of.
     *
     * @param sessionId Session to serialise. Blank ids are rejected rather than
     *   producing an empty document.
     * @return [Result.success] with the document, or [Result.failure] for a
     *   blank id, a missing session, or a storage/serialisation error.
     */
    suspend operator fun invoke(sessionId: String): Result<ChatExportDocument> {
        if (sessionId.isBlank()) {
            return Result.failure(IllegalArgumentException("Session id cannot be blank"))
        }
        return try {
            val session = chatRepository.getSessionById(sessionId)
                ?: return Result.failure(NoSuchElementException("No chat session with id $sessionId"))
            val messages = chatRepository.getMessagesForSession(sessionId).first()
            val messagesArray = JSONArray()
            messages.forEach { message ->
                messagesArray.put(
                    JSONObject()
                        .put("role", message.role.name)
                        .put("text", message.content)
                        .put("timestamp", message.timestamp),
                )
            }
            val sessionName = session.name.ifBlank { FALLBACK_SESSION_NAME }
            val root = JSONObject()
                .put("sessionId", sessionId)
                .put("sessionName", sessionName)
                .put("exportedAt", System.currentTimeMillis())
                .put("messages", messagesArray)
            Result.success(
                ChatExportDocument(sessionName = sessionName, json = root.toString(JSON_INDENT)),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Serialisation constants of the exported document. */
    companion object {
        /** Name forwarded as the share subject when the session has none. */
        const val FALLBACK_SESSION_NAME: String = "Chat"

        /** Pretty-print indent of the exported document. */
        const val JSON_INDENT: Int = 2
    }
}
