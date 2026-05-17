package ai.agent.android.presentation.ui.chat.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatMessageStatus
import app.knotwork.design.components.chat.ChatMetadata
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.console.ConsoleFilter
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleSource
import app.knotwork.design.screens.chat.ChatHomeMessageRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Stub Hilt [ViewModel] backing `ChatHomeScreen`. Phase 21 / Task 8 ships
 * the redesigned visual surface; this VM exposes a deterministic
 * [StateFlow] of [ChatHomeUiState] plus a live [messages] list so the
 * screen behaves like a real chat — typing and sending appends a real
 * user row and a canned assistant reply.
 *
 * **Not** wired to the real `GraphExecutionEngine` / `ChatRepository` —
 * the legacy `chat.legacy.ChatViewModel` retains that integration and
 * will be reconnected to this screen in a follow-up task after v0.1. The
 * canned-reply behaviour is the smallest stub that keeps the surface
 * usable while we ship the visual RC; the debug-picker still drives the
 * 9 documented states for visual QA.
 *
 * Constructor-injected (Hilt) but accepts no collaborators today —
 * keeping the empty constructor parameter list intact so the follow-up
 * integration only has to add dependencies, not change the wiring shape.
 */
@HiltViewModel
class ChatHomeViewModel @Inject constructor() : ViewModel() {

    /** Thread title surfaced on the TopAppBar. Tracked separately from [state] for cheap reads. */
    private val _threadTitle: MutableStateFlow<String> = MutableStateFlow(DEFAULT_THREAD_TITLE)

    /** Display name of the currently-active model. */
    private val _modelName: MutableStateFlow<String> = MutableStateFlow(DEFAULT_MODEL_NAME)

    /** Mutable composer input value — hoisted here so state survives recompositions. */
    private val _composerValue: MutableStateFlow<String> = MutableStateFlow("")

    /** Typed-confirm input for Destructive HITL confirmations. */
    private val _pendingTypedConfirm: MutableStateFlow<String> = MutableStateFlow("")

    /**
     * Live conversation history. Stub-only: every user turn appends one user
     * row + one canned assistant reply. Cleared on `selectThread` so picking
     * a new thread feels like a fresh conversation.
     */
    private val _messages: MutableStateFlow<List<ChatHomeMessageRow>> = MutableStateFlow(emptyList())
    val messages: StateFlow<List<ChatHomeMessageRow>> = _messages.asStateFlow()

    /** Current sealed UI state — single source of truth for the chat-home surface. */
    private val _state: MutableStateFlow<ChatHomeUiState> = MutableStateFlow(ChatHomeUiState.Empty)
    val state: StateFlow<ChatHomeUiState> = _state.asStateFlow()

    /**
     * Inline-search query for the console Logs tab. `null` means the search
     * field is hidden; `""` means visible but matching every line. Hoisted
     * to the VM so the value survives state-picker round-trips and tab
     * switches.
     */
    private val _consoleSearchQuery: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Active source-set filter applied to the console Logs tab. */
    private val _consoleFilter: MutableStateFlow<ConsoleFilter> = MutableStateFlow(ConsoleFilter.allOn)

    /** Externally-observable thread title used by the screen-level composable. */
    val threadTitle: StateFlow<String> = _threadTitle.asStateFlow()

    /** Externally-observable model display name. */
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    /** Externally-observable composer input value. */
    val composerValue: StateFlow<String> = _composerValue.asStateFlow()

    /** Externally-observable typed-confirm input. */
    val pendingTypedConfirm: StateFlow<String> = _pendingTypedConfirm.asStateFlow()

    /** Externally-observable console search query (null = hidden, "" = visible but unfiltered). */
    val consoleSearchQuery: StateFlow<String?> = _consoleSearchQuery.asStateFlow()

    /** Externally-observable console source-set filter. */
    val consoleFilter: StateFlow<ConsoleFilter> = _consoleFilter.asStateFlow()

    /**
     * Updates the composer input value. Hoisted to the VM so screen
     * recompositions never own the text.
     */
    fun onComposerValueChange(value: String) {
        _composerValue.value = value
    }

    /**
     * Updates the typed-confirm input shown next to the Destructive HITL
     * confirmation row. Mirrors `HitlConfirmationCard`'s typed-confirm
     * contract.
     */
    fun onTypedConfirmChange(value: String) {
        _pendingTypedConfirm.value = value
    }

    /**
     * Stub send pipeline: appends the user's message to [messages], flips
     * the state to [ChatHomeUiState.Generating] for [STUB_GENERATING_DELAY_MS],
     * then appends a canned assistant reply and settles back to
     * [ChatHomeUiState.Idle]. No-op when the composer is empty.
     */
    fun sendMessage() {
        val draft = _composerValue.value.trim()
        if (draft.isEmpty()) return
        _composerValue.value = ""
        val now = nowTimestamp()
        _messages.update { it + userRow(text = draft, timestamp = now) }
        _state.value = ChatHomeUiState.Generating
        viewModelScope.launch {
            delay(STUB_GENERATING_DELAY_MS)
            // Only collapse + append the canned reply if the user did not
            // pick a different state via the debug picker while the stub
            // was running.
            if (_state.value !is ChatHomeUiState.Generating) return@launch
            _messages.update { it + assistantRow(text = CANNED_REPLY, timestamp = nowTimestamp()) }
            _state.value = ChatHomeUiState.Idle
        }
    }

    /** Cancels a stub generation early — collapses [ChatHomeUiState.Generating] to [ChatHomeUiState.Idle]. */
    fun stopGeneration() {
        _state.update { current ->
            if (current is ChatHomeUiState.Generating) ChatHomeUiState.Idle else current
        }
    }

    /** Opens the drawer overlay. */
    fun openDrawer() {
        _state.value = ChatHomeUiState.DrawerOpen
    }

    /** Closes the drawer overlay, settling on the right state for the current message list. */
    fun closeDrawer() {
        if (_state.value is ChatHomeUiState.DrawerOpen) {
            _state.value = restingState()
        }
    }

    /**
     * Selects a thread by id. Stub-only: persists nothing beyond updating
     * the TopAppBar title, clearing the live message list (so the new
     * thread feels fresh), and closing any open overlay.
     */
    fun selectThread(threadId: String) {
        _threadTitle.value = "Thread $threadId"
        _messages.value = emptyList()
        _state.value = ChatHomeUiState.Empty
    }

    /** Opens the console pane at the given [snap] (default: Partial). */
    fun openConsole(snap: ConsoleSnap = ConsoleSnap.Partial) {
        _state.value = ChatHomeUiState.ConsoleExpanded(snap)
    }

    /** Updates the snap point of the currently-open console pane. */
    fun setConsoleSnap(snap: ConsoleSnap) {
        _state.update { current ->
            if (current is ChatHomeUiState.ConsoleExpanded) ChatHomeUiState.ConsoleExpanded(snap) else current
        }
    }

    /** Dismisses the console pane and settles on the right resting state. */
    fun closeConsole() {
        if (_state.value is ChatHomeUiState.ConsoleExpanded) {
            _state.value = restingState()
        }
    }

    /**
     * Toggles the console inline-search field. Calling this twice cycles
     * `null → "" → null`. The host wires this to the header `Search` button.
     */
    fun toggleConsoleSearch() {
        _consoleSearchQuery.update { current -> if (current == null) "" else null }
    }

    /** Updates the console inline-search query while the field is visible. */
    fun onConsoleSearchQueryChange(query: String) {
        _consoleSearchQuery.value = query
    }

    /** Replaces the active console source-set filter. */
    fun onConsoleFilterChange(filter: ConsoleFilter) {
        _consoleFilter.value = filter
    }

    /**
     * Reacts to the long-press "Only show this source" menu item by
     * narrowing [consoleFilter] to a single-source set.
     */
    fun filterConsoleByLineSource(source: ConsoleSource) {
        _consoleFilter.value = ConsoleFilter(sources = setOf(source))
    }

    /**
     * Debug-only escape hatch used by the triple-tap state picker to force
     * the surface into an arbitrary state. The picker UI is gated on
     * `BuildConfig.DEBUG`, but the VM accepts the call in any build so
     * unit tests can drive the state machine deterministically.
     */
    fun forceState(state: ChatHomeUiState) {
        _state.value = state
    }

    /** Resting (non-overlay) state given the current message list — `Empty` if no messages, else `Idle`. */
    private fun restingState(): ChatHomeUiState =
        if (_messages.value.isEmpty()) ChatHomeUiState.Empty else ChatHomeUiState.Idle

    /** Builds a user message row stamped with [timestamp]. */
    private fun userRow(text: String, timestamp: String): ChatHomeMessageRow = ChatHomeMessageRow(
        id = "u-${UUID.randomUUID()}",
        role = ChatRole.User,
        content = ChatContent.Text(text),
        metadata = ChatMetadata(timestamp = timestamp, status = ChatMessageStatus.Sent),
    )

    /** Builds an assistant message row stamped with [timestamp]. */
    private fun assistantRow(text: String, timestamp: String): ChatHomeMessageRow = ChatHomeMessageRow(
        id = "a-${UUID.randomUUID()}",
        role = ChatRole.Assistant,
        content = ChatContent.Text(text),
        metadata = ChatMetadata(
            timestamp = timestamp,
            model = _modelName.value,
            status = ChatMessageStatus.Sent,
        ),
    )

    /**
     * Pre-formats the current device-local time as `HH:mm`. The formatter
     * is constructed per call (not cached) so a locale change while the
     * app is running is picked up on the next stub message.
     */
    private fun nowTimestamp(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    companion object {
        /** Pre-formatted fallback thread title surfaced before any thread is selected. */
        const val DEFAULT_THREAD_TITLE: String = "New conversation"

        /** Pre-formatted fallback model name surfaced when no local model is loaded. */
        const val DEFAULT_MODEL_NAME: String = "Local model"

        /** Duration of the stub `Idle → Generating → Idle` round-trip in milliseconds. */
        const val STUB_GENERATING_DELAY_MS: Long = 1_200L

        /** Canned assistant reply appended after every stub send round-trip. */
        const val CANNED_REPLY: String =
            "The on-device backend isn't wired up to this surface yet — this is a stub reply so " +
                "you can see the chat round-trip. The orchestrator integration ships in a " +
                "follow-up task after v0.1."
    }
}
