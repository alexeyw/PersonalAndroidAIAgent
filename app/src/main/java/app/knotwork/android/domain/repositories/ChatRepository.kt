package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.ChatSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing chat history and sessions.
 */
interface ChatRepository {
    /**
     * Saves a new chat message.
     *
     * @param message The [ChatMessage] to save.
     */
    suspend fun saveMessage(message: ChatMessage)

    /**
     * Retrieves the full history of messages for a given session, including
     * intermediate (`isFinal = false`) entries. Use this for context-window
     * computations, exports, and console logging.
     *
     * @param sessionId The unique ID of the chat session.
     * @return A [Flow] emitting the list of [ChatMessage] ordered by time.
     */
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    /**
     * Retrieves only the user-facing messages for a session — that is, messages
     * with `isFinal = true`. This is the source of truth for the main chat list
     * UI; intermediate node outputs are filtered out and surfaced separately
     * in the agent console.
     *
     * @param sessionId The unique ID of the chat session.
     * @return A [Flow] emitting the list of final [ChatMessage]s ordered by time.
     */
    fun getDisplayMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    /**
     * Toggles the starred state of a single message. Starred messages are
     * preserved across sessions and accessible via the chat-screen filter.
     *
     * @param messageId The id of the message to update.
     * @param starred The new starred state to persist.
     */
    suspend fun setMessageStarred(messageId: Long, starred: Boolean)

    /**
     * Retrieves all starred messages across every session ordered chronologically
     * (oldest first), matching the main chat list. Backs the chat-screen
     * "starred only" filter; preserving `ASC` order keeps the screen's
     * "scroll-to-last" auto-scroll consistent across filter toggles.
     *
     * @return A [Flow] emitting the current list of starred [ChatMessage]s.
     */
    fun getStarredMessages(): Flow<List<ChatMessage>>

    /**
     * Deletes all messages associated with the given session, and the session itself.
     *
     * @param sessionId The unique ID of the chat session to delete.
     */
    suspend fun deleteSession(sessionId: String)

    /**
     * Retrieves a list of all distinct chat session IDs.
     * Deprecated: Use getSessionsFlow() instead.
     *
     * @return A list of unique session IDs.
     */
    suspend fun getAllSessions(): List<String>

    /**
     * Deletes a specific chat message by its ID.
     *
     * @param messageId The ID of the message to delete.
     */
    suspend fun deleteMessage(messageId: Long)

    /**
     * Retrieves recent system messages (logs/observations).
     *
     * @param limit The maximum number of messages to retrieve.
     * @return A [Flow] emitting a list of recent system [ChatMessage].
     */
    fun getRecentSystemMessages(limit: Int = 100): Flow<List<ChatMessage>>

    /**
     * Creates or updates a chat session.
     *
     * @param session The [ChatSession] to create or update.
     */
    suspend fun saveSession(session: ChatSession)

    /**
     * Renames an existing chat session without rewriting other fields. Faster
     * than reading the row and calling [saveSession] and avoids the race
     * inherent in a read-modify-write performed off the main thread while
     * the orchestrator is concurrently updating `updatedAt`.
     *
     * No-op when no session with [sessionId] exists.
     *
     * @param sessionId The id of the session to rename.
     * @param newName The new display name to persist. Callers are expected to
     *   trim / validate the value upstream.
     */
    suspend fun renameSession(sessionId: String, newName: String)

    /**
     * Toggles the session-level favorite flag persisted on
     * `chat_sessions.isStarred`. Favorited chats sort to the top of the
     * drawer thread list and render a small star indicator.
     *
     * Distinct from [setMessageStarred] which operates on individual messages.
     *
     * @param sessionId The id of the session to update.
     * @param favorite The new favorite flag to persist.
     */
    suspend fun setSessionFavorite(sessionId: String, favorite: Boolean)

    /**
     * Imports a chat from a JSON document into a freshly-created session and
     * returns the new session id. The caller is expected to switch the
     * active session to the returned id.
     *
     * Accepted shapes:
     *  - the document produced by an export (`{"sessionName": ..., "messages": [...]}`);
     *  - a bare top-level array of message objects (`[{...}, ...]`).
     *
     * Each message must carry `role`, `text`, and `timestamp`. Unknown roles
     * default to `USER`; missing timestamps default to "now".
     *
     * @param json The JSON content to import.
     * @return The id of the newly created session.
     * @throws org.json.JSONException If the document cannot be parsed.
     */
    suspend fun importChat(json: String): String

    /**
     * Retrieves all chat sessions as a flow, ordered by the last update time.
     *
     * @return A [Flow] emitting the list of [ChatSession].
     */
    fun getSessionsFlow(): Flow<List<ChatSession>>

    /**
     * Retrieves a specific chat session by its ID.
     *
     * @param id The ID of the session.
     * @return The [ChatSession] if found, null otherwise.
     */
    suspend fun getSessionById(id: String): ChatSession?

    /**
     * Saves a trace step for a session.
     *
     * @param sessionId The ID of the session.
     * @param nodeName The name of the node.
     * @param outputText The output text generated by the node.
     * @param durationMs Wall-clock time the node took to execute, in milliseconds.
     * @param tokenCount Approximate number of tokens produced by the node (LLM nodes only);
     *   `null` for nodes that do not consume or emit tokens.
     */
    suspend fun saveTraceStep(
        sessionId: String,
        nodeName: String,
        outputText: String,
        durationMs: Long,
        tokenCount: Int?,
    )

    /**
     * Retrieves trace steps for a given session.
     *
     * @param sessionId The ID of the session.
     * @return A [Flow] emitting the list of [AgentOrchestratorState.TraceStep] ordered by time.
     */
    fun getTraceSteps(sessionId: String): Flow<List<AgentOrchestratorState.TraceStep>>
}
