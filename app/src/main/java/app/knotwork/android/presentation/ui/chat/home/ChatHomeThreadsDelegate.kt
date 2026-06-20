package app.knotwork.android.presentation.ui.chat.home

import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.design.screens.chat.ChatHomeThreadRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Threads / sessions delegate of [ChatHomeViewModel].
 *
 * Owns the live session cache and the set of sessions with a background run,
 * projecting both into the `thread` slice of [ChatHomeScreenState] (title,
 * favorite, drawer rows). Hosts the drawer overlay and the session CRUD
 * surface (new / rename / favorite / delete), and exposes the session-metadata
 * refresh and the auto-rename helper that the ViewModel core composes into the
 * send / session-init / thread-switch paths.
 *
 * The thread *switch* itself stays in the ViewModel ([selectThread] seam): it
 * orchestrates run-lifecycle concerns (cancelling the generation collector,
 * re-subscribing the message stream, reattaching to a live run) that are core,
 * not thread-state. CRUD that ends in a switch routes through that seam.
 *
 * Pipeline-name resolution spans this delegate's session cache and the pipeline
 * delegate's default-binding, so the metadata refresh composes the injected
 * [pipelineNameRefresher] to keep a single atomic emission (see
 * `docs/architecture.md` §1.2).
 *
 * @property scope The ViewModel's [androidx.lifecycle.viewModelScope].
 * @property state The ViewModel's single source-of-truth state flow.
 * @property chatRepository Source of the session list; session CRUD.
 * @property pipelineRunRepository Source of the active-run session-id set (drawer badges).
 * @property selectThread Seam into the ViewModel's thread switch.
 * @property pipelineNameRefresher Recomputes the pipeline subtitle for a snapshot
 *   (supplied by the pipeline-binding delegate) so it rides the metadata refresh.
 * @property onSessionsChanged Invoked after each sessions-flow emission so the
 *   pipeline-binding delegate can rebind a chat whose pipeline was deleted.
 */
class ChatHomeThreadsDelegate(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatHomeScreenState>,
    private val chatRepository: ChatRepository,
    private val pipelineRunRepository: PipelineRunRepository,
    private val selectThread: (String) -> Unit,
    private val pipelineNameRefresher: (ChatHomeScreenState) -> ChatHomeScreenState,
    private val onSessionsChanged: suspend () -> Unit,
) {

    private var sessions: List<ChatSession> = emptyList()

    /**
     * Session ids that currently own a pipeline run in a non-terminal status.
     * Fed by [observeActiveRuns] and projected into the drawer thread rows as
     * the in-progress badge ([buildThreadRows]).
     */
    private var activeRunSessionIds: Set<String> = emptySet()

    /** Latest session cache snapshot — read by the ViewModel core and the other delegates' providers. */
    fun sessionsSnapshot(): List<ChatSession> = sessions

    /** Launches the session-list and active-run observers. Called once from the ViewModel `init`. */
    fun startObservers() {
        observeSessions()
        observeActiveRuns()
    }

    /** Opens the drawer overlay. */
    fun openDrawer() {
        state.update { it.copy(visual = ChatHomeUiState.DrawerOpen) }
    }

    /** Closes the drawer overlay, settling on the right state for the current message list. */
    fun closeDrawer() {
        state.update { current ->
            if (current.visual is ChatHomeUiState.DrawerOpen) {
                current.copy(visual = current.restingVisual())
            } else {
                current
            }
        }
    }

    /**
     * Pure transformer: recomputes thread title + favorite, the drawer rows,
     * and (via [pipelineNameRefresher]) the pipeline subtitle for [s] from the
     * latest session/pipeline caches. Composed into the caller's single
     * `state.update` block so one upstream emission (sessions flow, session
     * init, thread switch) produces exactly one state emission — collectors
     * never observe a frame with a fresh title but stale rows.
     *
     * Title/favorite are left untouched when the active session id is non-blank
     * but missing from the cache (mid-deletion window).
     *
     * @param s The snapshot to refresh.
     * @return [s] with thread metadata and pipeline subtitle recomputed.
     */
    fun sessionMetadataRefreshed(s: ChatHomeScreenState): ChatHomeScreenState {
        val currentId = s.thread.currentSessionId
        val session = sessions.firstOrNull { it.id == currentId }
        val refreshedThread = when {
            session != null -> s.thread.copy(
                title = session.name.ifBlank { ChatHomeThreadState.DEFAULT_TITLE },
                favorite = session.isStarred,
            )
            currentId.isBlank() -> s.thread.copy(
                title = ChatHomeThreadState.DEFAULT_TITLE,
                favorite = false,
            )
            else -> s.thread
        }
        return pipelineNameRefresher(
            s.copy(thread = refreshedThread.copy(rows = buildThreadRows(currentId))),
        )
    }

    /**
     * Auto-renames a newly-created chat to the first user message (truncated to
     * [ChatHomeViewModel.AUTO_RENAME_CHAR_LIMIT] characters). Invoked by the
     * send path so the drawer entry reflects the user's framing.
     *
     * @param sessionId The session being renamed.
     * @param prompt The user's first message text.
     */
    fun autoRenameIfDefault(sessionId: String, prompt: String) {
        if (prompt.isBlank()) return
        val session = sessions.firstOrNull { it.id == sessionId } ?: return
        if (session.name != ChatHomeViewModel.DEFAULT_NEW_CHAT_NAME) return
        val truncated = if (prompt.length > ChatHomeViewModel.AUTO_RENAME_CHAR_LIMIT) {
            prompt.take(ChatHomeViewModel.AUTO_RENAME_CHAR_LIMIT) + ChatHomeViewModel.AUTO_RENAME_SUFFIX
        } else {
            prompt
        }
        scope.launch {
            chatRepository.saveSession(session.copy(name = truncated))
        }
    }

    /**
     * Creates a new chat session bound to [pipelineId] and switches to it.
     *
     * @param pipelineId Pipeline identifier to bind, or `null` to inherit the
     *   application-wide default pipeline.
     */
    fun createNewSessionWithPipeline(pipelineId: String?) {
        scope.launch {
            val newId = UUID.randomUUID().toString()
            chatRepository.saveSession(
                ChatSession(
                    id = newId,
                    name = ChatHomeViewModel.DEFAULT_NEW_CHAT_NAME,
                    updatedAt = System.currentTimeMillis(),
                    pipelineId = pipelineId,
                ),
            )
            selectThread(newId)
        }
    }

    /**
     * Renames the chat session identified by [threadId]. Trims the input and
     * short-circuits when the trimmed value is blank.
     */
    fun renameSession(threadId: String, newName: String) {
        val trimmed = newName.trim()
        if (threadId.isBlank() || trimmed.isEmpty()) return
        scope.launch {
            chatRepository.renameSession(threadId, trimmed)
        }
    }

    /** Flips the session-level favorite flag on the currently active chat. No-op when no session is loaded. */
    fun toggleFavoriteCurrent() {
        val sessionId = state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        val current = sessions.firstOrNull { it.id == sessionId }?.isStarred ?: false
        scope.launch {
            chatRepository.setSessionFavorite(sessionId, !current)
        }
    }

    /**
     * Deletes the currently active session and auto-selects the next available
     * thread; when no other session exists, creates a fresh unbound chat so the
     * user is never stranded on a non-existent session id.
     */
    fun deleteCurrentSession() {
        val sessionId = state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        scope.launch {
            chatRepository.deleteSession(sessionId)
            val remaining = sessions.filter { it.id != sessionId }
            if (remaining.isNotEmpty()) {
                selectThread(remaining.first().id)
            } else {
                createNewSessionWithPipeline(pipelineId = null)
            }
        }
    }

    /** Observes the session list. Keeps a cache for lookups and refreshes the title + subtitle + rows. */
    private fun observeSessions() {
        scope.launch {
            chatRepository.getSessionsFlow().collect { current ->
                sessions = current
                state.update { sessionMetadataRefreshed(it) }
                onSessionsChanged()
            }
        }
    }

    /**
     * Mirrors the set of sessions owning a non-terminal run into
     * [activeRunSessionIds] and re-projects the drawer rows, so threads with a
     * run still working in the background render the in-progress badge. Only the
     * rows are rebuilt — title / pipeline-name resolution is untouched because a
     * badge flip cannot change either.
     */
    private fun observeActiveRuns() {
        scope.launch {
            pipelineRunRepository.observeActiveRunSessionIds().collect { sessionIds ->
                activeRunSessionIds = sessionIds
                state.update { current ->
                    current.copy(
                        thread = current.thread.copy(rows = buildThreadRows(current.thread.currentSessionId)),
                    )
                }
            }
        }
    }

    /**
     * Projects the live session cache into drawer thread rows. Favorited
     * sessions sort to the top of the drawer; the rest follow the repository's
     * `updatedAt DESC` ordering.
     *
     * @param activeId id of the active session used for the `selected`/`active` flags.
     */
    private fun buildThreadRows(activeId: String): List<ChatHomeThreadRow> {
        val sorted = sessions.sortedWith(
            compareByDescending<ChatSession> { it.isStarred }
                .thenByDescending { it.updatedAt },
        )
        return sorted.map { session ->
            ChatHomeThreadRow(
                id = session.id,
                title = session.name.ifBlank { ChatHomeThreadState.DEFAULT_TITLE },
                subtitle = formatThreadSubtitle(session.updatedAt),
                selected = session.id == activeId,
                active = session.id == activeId,
                starred = session.isStarred,
                running = session.id in activeRunSessionIds,
            )
        }
    }

    /** Formats a session `updatedAt` timestamp as the drawer thread subtitle (e.g. `Mon 14:32`). */
    private fun formatThreadSubtitle(updatedAt: Long): String =
        SimpleDateFormat(THREAD_SUBTITLE_PATTERN, Locale.getDefault()).format(Date(updatedAt))

    private companion object {
        /** Pattern used for the drawer thread subtitle (e.g. `Mon 14:32`). */
        const val THREAD_SUBTITLE_PATTERN: String = "EEE HH:mm"
    }
}
