package ai.agent.android.presentation.ui.chat.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.design.components.console.ConsoleSnap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stub Hilt [ViewModel] backing `ChatHomeScreen`. Phase 21 / Task 8 ships
 * the redesigned visual surface; this VM exposes a deterministic
 * [StateFlow] of [ChatHomeUiState] so the screen can demo every documented
 * state via the debug picker.
 *
 * **Not** wired to the real `GraphExecutionEngine` / `ChatRepository` —
 * the legacy `chat.legacy.ChatViewModel` retains that integration and will
 * be reconnected to this screen in a follow-up task after v0.1. Until then
 * `sendMessage` simulates a 1.2 s `Idle → Generating → Idle` round-trip so
 * the composer morph remains testable.
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

    /** Current sealed UI state — single source of truth for the chat-home surface. */
    private val _state: MutableStateFlow<ChatHomeUiState> = MutableStateFlow(ChatHomeUiState.Idle)
    val state: StateFlow<ChatHomeUiState> = _state.asStateFlow()

    /** Externally-observable thread title used by the screen-level composable. */
    val threadTitle: StateFlow<String> = _threadTitle.asStateFlow()

    /** Externally-observable model display name. */
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    /** Externally-observable composer input value. */
    val composerValue: StateFlow<String> = _composerValue.asStateFlow()

    /** Externally-observable typed-confirm input. */
    val pendingTypedConfirm: StateFlow<String> = _pendingTypedConfirm.asStateFlow()

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
     * Stub send pipeline: flips the state to [ChatHomeUiState.Generating]
     * for a fixed 1.2 s, then settles back to [ChatHomeUiState.Idle].
     * Clears the composer input on entry so the morph is visible to the
     * user. No-op when the composer is empty.
     */
    fun sendMessage() {
        if (_composerValue.value.isBlank()) return
        _composerValue.value = ""
        _state.value = ChatHomeUiState.Generating
        viewModelScope.launch {
            delay(STUB_GENERATING_DELAY_MS)
            // Only collapse to Idle if the user did not pick a different
            // state via the debug picker while the stub was running.
            _state.update { current -> if (current is ChatHomeUiState.Generating) ChatHomeUiState.Idle else current }
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

    /** Closes the drawer overlay, settling on Idle. */
    fun closeDrawer() {
        if (_state.value is ChatHomeUiState.DrawerOpen) {
            _state.value = ChatHomeUiState.Idle
        }
    }

    /**
     * Selects a thread by id. Stub-only: persists nothing beyond updating
     * the TopAppBar title and closing the drawer.
     */
    fun selectThread(threadId: String) {
        _threadTitle.value = "Thread $threadId"
        if (_state.value is ChatHomeUiState.DrawerOpen) {
            _state.value = ChatHomeUiState.Idle
        }
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

    /** Dismisses the console pane and settles on Idle. */
    fun closeConsole() {
        if (_state.value is ChatHomeUiState.ConsoleExpanded) {
            _state.value = ChatHomeUiState.Idle
        }
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

    companion object {
        /** Pre-formatted fallback thread title surfaced before any thread is selected. */
        const val DEFAULT_THREAD_TITLE: String = "New conversation"

        /** Pre-formatted fallback model name surfaced when no local model is loaded. */
        const val DEFAULT_MODEL_NAME: String = "Local model"

        /** Duration of the stub `Idle → Generating → Idle` round-trip in milliseconds. */
        const val STUB_GENERATING_DELAY_MS: Long = 1_200L
    }
}
