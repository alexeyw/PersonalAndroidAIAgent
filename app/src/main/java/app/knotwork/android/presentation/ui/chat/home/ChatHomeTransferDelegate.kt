package app.knotwork.android.presentation.ui.chat.home

import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.usecases.SaveMessageToMemoryUseCase
import app.knotwork.android.domain.usecases.SaveToMemoryOutcome
import app.knotwork.design.components.chat.ChatContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Chat import / export / save-to-memory delegate of [ChatHomeViewModel].
 *
 * Groups the three isolated transfer actions that move chat content in or out of
 * the app: importing a chat from JSON, exporting the active session as a
 * share-sheet payload, and saving a single message's text into long-term
 * memory. None of them owns a render slice of [ChatHomeScreenState] — they read
 * the current messages / active session and communicate through one-shot
 * [SharedFlow] channels ([exportEvents] / [importErrorEvents] /
 * [memorySaveEvents]) consumed by the screen.
 *
 * Shares the ViewModel's [scope] and single [state] reducer (see
 * `docs/architecture.md` §1.2).
 *
 * @property scope The ViewModel's [androidx.lifecycle.viewModelScope].
 * @property state The ViewModel's single source-of-truth state flow.
 * @property chatRepository Reads messages / sessions for export, imports a chat.
 * @property saveMessageToMemoryUseCase Persists a message's text as a manual memory entry.
 * @property selectThread Seam into the ViewModel's thread switch — an imported
 *   chat becomes the active thread.
 */
class ChatHomeTransferDelegate(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatHomeScreenState>,
    private val chatRepository: ChatRepository,
    private val saveMessageToMemoryUseCase: SaveMessageToMemoryUseCase,
    private val selectThread: (String) -> Unit,
) {

    private val _exportEvents: MutableSharedFlow<ChatExportPayload> = MutableSharedFlow(extraBufferCapacity = 1)
    private val _importErrorEvents: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)
    private val _memorySaveEvents: MutableSharedFlow<MemorySaveEvent> = MutableSharedFlow(extraBufferCapacity = 1)

    /**
     * One-shot stream raised when the user picks `Export chat` from the
     * overflow menu. The screen consumes each payload via a `LaunchedEffect`
     * and dispatches a system share-sheet (`Intent.ACTION_SEND`).
     */
    val exportEvents: SharedFlow<ChatExportPayload> = _exportEvents.asSharedFlow()

    /**
     * One-shot stream of import-failure messages. Surfaced via the shared
     * `SnackbarHostState`; the carried string is a localised user-visible
     * description ("Could not read the selected file", JSON parse error, …).
     */
    val importErrorEvents: SharedFlow<String> = _importErrorEvents.asSharedFlow()

    /**
     * One-shot stream of "Save to memory" outcomes raised by the message
     * long-press action. The screen maps each event to a snackbar
     * ("Saved to memory" / failure copy).
     */
    val memorySaveEvents: SharedFlow<MemorySaveEvent> = _memorySaveEvents.asSharedFlow()

    /**
     * Extracts the plain-text body of a chat-home row by its catalog id
     * (`ChatHomeMessageRow.id`). Returns `null` for rows whose content
     * is not a plain-text bubble (Clarification cards, HITL confirmations,
     * tool-call tiles, inline errors) — those have their own affordances and
     * don't expose a copyable payload.
     *
     * Used by the long-press context menu (`onMessageContextAction`) to
     * resolve Copy / Rerun targets.
     */
    fun textForRow(rowId: String): String? {
        val row = state.value.messages.firstOrNull { it.id == rowId } ?: return null
        return when (val content = row.content) {
            is ChatContent.Text -> content.text
            is ChatContent.Markdown -> content.source
            else -> null
        }
    }

    /**
     * Persists the text of the message identified by [rowId] into long-term
     * memory as a manual entry, then raises a [MemorySaveEvent] so the screen
     * can confirm via snackbar. Rows without a copyable text payload, and
     * blank texts, are silently ignored (no event).
     *
     * Backs the long-press "Save to memory" context action.
     */
    fun saveMessageToMemory(rowId: String) {
        val text = textForRow(rowId) ?: return
        scope.launch {
            when (saveMessageToMemoryUseCase(text)) {
                is SaveToMemoryOutcome.Saved -> _memorySaveEvents.tryEmit(MemorySaveEvent.Saved)
                is SaveToMemoryOutcome.Failed -> _memorySaveEvents.tryEmit(MemorySaveEvent.Failed)
                SaveToMemoryOutcome.Skipped -> Unit
            }
        }
    }

    /**
     * Imports a chat from a JSON document. Surfaces a snackbar event on
     * failure via [importErrorEvents]; on success the newly created session
     * becomes the active thread (via the [selectThread] seam).
     *
     * @param json Raw JSON payload (export shape or bare message array).
     */
    fun importChatFromJson(json: String) {
        scope.launch {
            try {
                val newId = chatRepository.importChat(json)
                selectThread(newId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _importErrorEvents.tryEmit(
                    e.localizedMessage ?: IMPORT_GENERIC_FAILURE_MESSAGE,
                )
            }
        }
    }

    /**
     * Serialises the currently active session and emits the resulting
     * [ChatExportPayload] via [exportEvents]. The screen consumes the payload
     * and dispatches a system share-sheet (`Intent.ACTION_SEND`) — kept in the
     * screen because Hilt-ViewModels stay free of `Context`.
     */
    fun exportCurrentSession() {
        val sessionId = state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        scope.launch {
            try {
                val rawMessages = chatRepository.getMessagesForSession(sessionId).first()
                val session = chatRepository.getSessionById(sessionId)
                val sessionName = session?.name ?: EXPORT_FALLBACK_SESSION_NAME
                val messagesArray = JSONArray()
                rawMessages.forEach { message ->
                    messagesArray.put(
                        JSONObject()
                            .put("role", message.role.name)
                            .put("text", message.content)
                            .put("timestamp", message.timestamp),
                    )
                }
                val root = JSONObject()
                    .put("sessionId", sessionId)
                    .put("sessionName", sessionName)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("messages", messagesArray)
                val payload =
                    ChatExportPayload(sessionName = sessionName, json = root.toString(EXPORT_JSON_INDENT))
                _exportEvents.tryEmit(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Mirrors the previous silent-failure contract: an export
                // that cannot be serialised simply emits nothing.
                Timber.w(e, "Chat export failed")
            }
        }
    }

    companion object {
        /** Fallback session name forwarded as the share-sheet subject when the session has no name. */
        const val EXPORT_FALLBACK_SESSION_NAME: String = "Chat"

        /** JSON pretty-print indent used for export payloads. */
        const val EXPORT_JSON_INDENT: Int = 2

        /** Fallback localised-error string used when the import path throws without a message. */
        const val IMPORT_GENERIC_FAILURE_MESSAGE: String = "Could not import the chat."
    }
}
