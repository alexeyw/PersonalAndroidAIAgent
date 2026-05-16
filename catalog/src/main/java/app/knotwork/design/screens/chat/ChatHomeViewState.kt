package app.knotwork.design.screens.chat

import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatMessageStatus
import app.knotwork.design.components.chat.ChatMetadata
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.chat.ComposerState
import app.knotwork.design.components.console.ConsoleFilter
import app.knotwork.design.components.console.ConsoleLine
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleTab
import app.knotwork.design.components.console.ConsoleTraceSpan
import app.knotwork.design.components.console.ConsoleVarRow

/**
 * Visual variant of the chat-home surface. Drives chrome differences
 * (drawer overlay, console overlay, error scrim) and lets snapshot tests
 * iterate the 9 documented states deterministically.
 *
 * The presentation layer in `:app` maps its sealed `ChatHomeUiState` onto
 * this enum at the boundary — the catalog stays free of `:app` types.
 * Mirrors `compose/screens/README.md §C1 · Chat (home)`.
 */
enum class ChatHomeVisualState {
    /** No messages in the active thread; empty-state surface visible. */
    Empty,

    /** History present, no in-flight request. Default. */
    Idle,

    /** Assistant is producing tokens. Loader bubble visible; composer in stop mode. */
    Generating,

    /** A tool call awaits user approval. HITL confirmation card pinned to last bubble. */
    HitlConfirm,

    /** Assistant needs more info. Clarification card pinned to last bubble. */
    Clarification,

    /** Inline error tile + retry. */
    Error,

    /** Alternate nav drawer is open over the chat surface. */
    DrawerOpen,

    /** Console pane expanded over the chat surface. */
    ConsoleExpanded,
}

/**
 * Lightweight projection of one chat message as consumed by [ChatHomeContent].
 *
 * The full `ChatMessage` Composable in `:catalog` requires a [ChatRole],
 * [ChatContent] and [ChatMetadata] — bundling them as one immutable row
 * keeps the screen's `LazyColumn` body declarative.
 *
 * @property id stable identity for `LazyColumn` keys.
 * @property role conversational role of the sender.
 * @property content sealed body to render inside the bubble.
 * @property metadata footer payload (timestamp, model, tokens, status).
 */
data class ChatHomeMessageRow(val id: String, val role: ChatRole, val content: ChatContent, val metadata: ChatMetadata)

/**
 * Single thread row inside the alternate-nav drawer.
 *
 * @property id stable identity used as the `LazyColumn` key.
 * @property title display title (falls back to a localised default at the
 *   call site).
 * @property subtitle pre-formatted secondary line (timestamp + counts).
 * @property selected `true` when this thread is currently loaded.
 */
data class ChatHomeThreadRow(val id: String, val title: String, val subtitle: String, val selected: Boolean = false)

/**
 * Console projection passed to [ChatHomeContent] when the console pane is
 * overlayed. The catalog has no opinion on pagination; tests and previews
 * pass simple lists.
 *
 * @property snap current snap point (Peek / Partial / Full).
 * @property tab currently-selected tab.
 * @property logs Logs-tab data.
 * @property vars Vars-tab data.
 * @property traces Traces-tab data.
 * @property filter source filter applied to [logs].
 */
data class ChatHomeConsoleState(
    val snap: ConsoleSnap = ConsoleSnap.Peek,
    val tab: ConsoleTab = ConsoleTab.Logs,
    val logs: List<ConsoleLine> = emptyList(),
    val vars: List<ConsoleVarRow> = emptyList(),
    val traces: List<ConsoleTraceSpan> = emptyList(),
    val filter: ConsoleFilter = ConsoleFilter.allOn,
)

/**
 * Top-level immutable input to [ChatHomeContent]. Every screen-side concern
 * that can change the visual is funnelled through this one type so the
 * stateless content stays trivially snapshot-testable.
 *
 * Mirrors `compose/screens/README.md §C1 · Chat (home)` and the 9-state
 * matrix locked-in by Phase 21 / Task 8.
 *
 * @property visualState which of the 9 documented states to render.
 * @property threadTitle text displayed as the TopAppBar title.
 * @property modelName text displayed beneath the title (model picker label).
 * @property messages chronological message list rendered inside the body
 *   `LazyColumn`. Empty when [visualState] is [ChatHomeVisualState.Empty].
 * @property composerValue current value of the composer text field.
 * @property composerState composer state-machine slot (drives the
 *   send/stop morph and inline error banner).
 * @property pendingTypedConfirm typed-confirm input for a Destructive
 *   confirmation; ignored unless [visualState] is
 *   [ChatHomeVisualState.HitlConfirm] with risk Destructive.
 * @property errorMessage user-visible error text rendered in
 *   [ChatHomeVisualState.Error]; `null` otherwise.
 * @property threads thread rows surfaced inside the drawer overlay.
 * @property console console-pane snapshot, used when [visualState] is
 *   [ChatHomeVisualState.ConsoleExpanded].
 * @property samplePrompts suggestion chips rendered in the empty state.
 */
data class ChatHomeViewState(
    val visualState: ChatHomeVisualState,
    val threadTitle: String,
    val modelName: String,
    val messages: List<ChatHomeMessageRow> = emptyList(),
    val composerValue: String = "",
    val composerState: ComposerState = ComposerState.Idle,
    val pendingTypedConfirm: String = "",
    val errorMessage: String? = null,
    val threads: List<ChatHomeThreadRow> = emptyList(),
    val console: ChatHomeConsoleState = ChatHomeConsoleState(),
    val samplePrompts: List<String> = emptyList(),
) {
    init {
        require((visualState == ChatHomeVisualState.Error) == (errorMessage != null)) {
            "errorMessage must be non-null iff visualState == Error"
        }
    }
}

/**
 * Stable callback bundle accepted by [ChatHomeContent]. Hoisted out of the
 * composable signature so screen code can pass one parameter object and so
 * tests / previews can construct a single no-op default.
 */
@Suppress("LongParameterList") // Mirrors the user-facing affordances; collapsing further hides intent.
class ChatHomeCallbacks(
    val onComposerValueChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onAttach: () -> Unit = {},
    val onVoice: () -> Unit = {},
    val onOpenDrawer: () -> Unit = {},
    val onCloseDrawer: () -> Unit = {},
    val onSelectThread: (String) -> Unit = {},
    val onNewThread: () -> Unit = {},
    val onOpenModelPicker: () -> Unit = {},
    val onOverflow: () -> Unit = {},
    val onSamplePrompt: (String) -> Unit = {},
    val onConsoleSnapChange: (ConsoleSnap) -> Unit = {},
    val onConsoleTabChange: (ConsoleTab) -> Unit = {},
    val onConsoleFilterChange: (ConsoleFilter) -> Unit = {},
    val onConsoleSearch: () -> Unit = {},
    val onConsoleCopyAll: () -> Unit = {},
    val onConsoleClear: () -> Unit = {},
    val onCloseConsole: () -> Unit = {},
    val onHitlAllowOnce: () -> Unit = {},
    val onHitlAllowAlways: (() -> Unit)? = null,
    val onHitlReject: () -> Unit = {},
    val onHitlTypedConfirmChange: (String) -> Unit = {},
    val onClarificationReply: (String) -> Unit = {},
    val onErrorRetry: () -> Unit = {},
    val onTitleTripleTap: () -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopChatHomeCallbacks(): ChatHomeCallbacks = ChatHomeCallbacks()

/**
 * Default sentinel used as a placeholder when [ChatMetadata.status] is not
 * specifically relevant. Kept module-private so screen code uses the
 * canonical constructor explicitly.
 */
internal val DefaultRowMetadata: ChatMetadata = ChatMetadata(
    timestamp = "—",
    status = ChatMessageStatus.Sent,
)
