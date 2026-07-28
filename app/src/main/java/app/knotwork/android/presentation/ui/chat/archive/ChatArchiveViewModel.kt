package app.knotwork.android.presentation.ui.chat.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.usecases.ChatExportDocument
import app.knotwork.android.domain.usecases.ExportChatUseCase
import app.knotwork.android.domain.usecases.UnarchiveChatUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel of the chat-archive surface.
 *
 * Observes the archived sessions (most-recently-archived first) and owns the
 * three actions the archive offers: restore, export, and delete forever.
 *
 * Restoring and deleting are the *only* ways a chat leaves this screen —
 * opening one does not un-archive it, so the read-only chat surface handles the
 * open path and this ViewModel never sees it.
 *
 * @property chatRepository Source of the archived sessions; sink for the
 *   irreversible delete.
 * @property unarchiveChatUseCase Puts a chat back into the drawer list.
 * @property exportChatUseCase Serialises an archived chat for the share sheet —
 *   archiving must not put a conversation out of reach of the transfer path.
 */
@HiltViewModel
class ChatArchiveViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val unarchiveChatUseCase: UnarchiveChatUseCase,
    private val exportChatUseCase: ExportChatUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatArchiveUiState())

    /** Single source of truth rendered by `ChatArchiveScreen`. */
    val uiState: StateFlow<ChatArchiveUiState> = _uiState.asStateFlow()

    private val _exportEvents = MutableSharedFlow<ChatExportDocument>(extraBufferCapacity = 1)

    /**
     * One-shot stream of export documents. The screen dispatches the system
     * share sheet, because Hilt ViewModels stay free of `Context`.
     */
    val exportEvents: SharedFlow<ChatExportDocument> = _exportEvents.asSharedFlow()

    private val _restoreEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * One-shot stream carrying the id of a chat the user just restored, so the
     * screen can confirm ("Chat restored") without the confirmation being
     * replayed on every re-subscription.
     */
    val restoreEvents: SharedFlow<String> = _restoreEvents.asSharedFlow()

    init {
        observeArchivedSessions()
    }

    /** Opens the overflow menu of [rowId]. */
    fun openRowMenu(rowId: String) {
        _uiState.update { it.copy(openMenuRowId = rowId) }
    }

    /** Closes any open row overflow menu. */
    fun dismissRowMenu() {
        _uiState.update { it.copy(openMenuRowId = null) }
    }

    /**
     * Restores [sessionId] into the drawer list. The row leaves this screen on
     * the next flow emission; the screen confirms via [restoreEvents].
     */
    fun restore(sessionId: String) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            unarchiveChatUseCase(sessionId)
                .onSuccess { _restoreEvents.tryEmit(sessionId) }
                .onFailure { e -> Timber.w(e, "Restoring chat %s failed", sessionId) }
        }
    }

    /** Raises the delete-forever confirmation for [sessionId]. */
    fun requestDelete(sessionId: String) {
        _uiState.update { it.copy(deleteTargetId = sessionId) }
    }

    /** Dismisses the delete-forever confirmation without deleting anything. */
    fun dismissDelete() {
        _uiState.update { it.copy(deleteTargetId = null) }
    }

    /**
     * Permanently deletes [sessionId] and everything it owns. Only reachable
     * from the row overflow behind the confirmation dialog — never from the
     * swipe, which stays a single safe action.
     */
    fun confirmDelete(sessionId: String) {
        _uiState.update { it.copy(deleteTargetId = null) }
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            try {
                chatRepository.deleteSession(sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Deleting archived chat %s failed", sessionId)
            }
        }
    }

    /** Serialises [sessionId] and raises it on [exportEvents] for the share sheet. */
    fun export(sessionId: String) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            exportChatUseCase(sessionId)
                .onSuccess { document -> _exportEvents.tryEmit(document) }
                .onFailure { e -> Timber.w(e, "Exporting archived chat %s failed", sessionId) }
        }
    }

    /** Re-subscribes after a read failure (the error state's "Try again"). */
    fun retry() {
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        observeArchivedSessions()
    }

    /**
     * Mirrors the archived sessions into [uiState].
     *
     * `now` is sampled per emission rather than per row so every row on one
     * frame is bucketed against the same instant.
     */
    private fun observeArchivedSessions() {
        viewModelScope.launch {
            chatRepository.getArchivedSessionsFlow()
                .catch { e ->
                    Timber.e(e, "Reading the chat archive failed")
                    _uiState.update {
                        it.copy(loading = false, errorMessage = e.localizedMessage ?: GENERIC_READ_FAILURE)
                    }
                }
                .collect { sessions ->
                    val now = System.currentTimeMillis()
                    _uiState.update { current ->
                        current.copy(
                            loading = false,
                            errorMessage = null,
                            rows = sessions.map { it.toRow(now) },
                        )
                    }
                }
        }
    }

    /** Projects one archived session onto its row. */
    private fun ChatSession.toRow(now: Long): ChatArchiveRow = ChatArchiveRow(
        id = id,
        title = name.ifBlank { DEFAULT_CHAT_TITLE },
        archivedAt = archivedAtLabel(archivedAt = archivedAt, now = now),
        starred = isStarred,
        // A background run is allowed to write into an archived chat without
        // un-archiving it, which is exactly the case worth surfacing: the
        // conversation changed after the user put it away.
        ranAfterArchiving = archivedAt != null && updatedAt > archivedAt,
    )

    companion object {
        /** Title shown for an archived chat that was never named. */
        const val DEFAULT_CHAT_TITLE: String = "New conversation"

        /** Fallback error copy when the read failure carries no message. */
        const val GENERIC_READ_FAILURE: String =
            "Your archived chats are still on the device — reading them failed. Try again."
    }
}
