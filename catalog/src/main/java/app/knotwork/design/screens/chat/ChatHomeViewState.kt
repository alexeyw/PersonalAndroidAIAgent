package app.knotwork.design.screens.chat

import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatContextAction
import app.knotwork.design.components.chat.ChatMessageStatus
import app.knotwork.design.components.chat.ChatMetadata
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.chat.ComposerState
import app.knotwork.design.components.console.ConsoleFilter
import app.knotwork.design.components.console.ConsoleLine
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleSource
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
 */
enum class ChatHomeVisualState {
    /**
     * Cold-start state — emitted before the chat repository delivers its
     * first message snapshot. Rendered as a centred progress indicator with
     * no placeholder copy so the user never sees the empty-state hero flash
     * for a frame on every launch.
     */
    Loading,

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

    /**
     * The session's most recent run was interrupted by a process death.
     * Interrupted-run status card (Resume / Discard) pinned to last bubble.
     */
    Interrupted,

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
data class ChatHomeThreadRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val selected: Boolean = false,
    /**
     * `true` when the thread is currently active (used as the leading
     * status-dot color in the drawer). Distinct from [selected] which
     * only flags multi-select; an active thread is also visually
     * highlighted with a cream row background.
     */
    val active: Boolean = false,
    /**
     * `true` when the user has favorited this thread. Renders a small
     * leading star glyph in the drawer and lets the host sort favorited
     * threads to the top of the list. Mirrors the session-level
     * `isStarred` flag persisted in `chat_sessions.isStarred`.
     */
    val starred: Boolean = false,
    /**
     * `true` when the thread owns a pipeline run in a non-terminal status
     * (queued, executing, or suspended on a human-in-the-loop request).
     * Renders a small trailing progress indicator so the user can spot
     * background conversations that are still working at a glance.
     */
    val running: Boolean = false,
)

/**
 * Suggestion card rendered in the empty-state body. Composes a title
 * (`"Summarise this week's emails"`) with a monospace `uses · {tools}`
 * subtitle so the user knows which tools the prompt will reach for.
 *
 * @property id stable identity for `LazyColumn`-style snapshot pinning.
 * @property title prompt headline.
 * @property toolsUsed pre-formatted comma-separated tool list
 * (`"search_tool, delegate_task"`). Empty string hides the subtitle.
 */
data class ChatHomeSamplePromptCard(val id: String, val title: String, val toolsUsed: String)

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
    /**
     * When non-null the console pane is rendered as an overlay anchored to
     * the bottom of the chat surface at the requested snap height. `null`
     * means the pane is closed — the orthogonal sealed [ChatHomeVisualState]
     * keeps its meaning and the body underneath renders unchanged. The
     * console is therefore truly independent of the chat state machine
     * (Generating / HitlConfirm / Clarification / Idle / Empty / Error all
     * stay visible behind the pane).
     */
    val snap: ConsoleSnap? = null,
    val tab: ConsoleTab = ConsoleTab.Logs,
    val logs: List<ConsoleLine> = emptyList(),
    val vars: List<ConsoleVarRow> = emptyList(),
    val traces: List<ConsoleTraceSpan> = emptyList(),
    val filter: ConsoleFilter = ConsoleFilter.allOn,
    /**
     * When non-null, the inline search field above the Logs list is rendered
     * and the query is substring-matched against each log line. `null`
     * means the search bar is hidden; `""` means visible but matching every
     * line. The host (chat ViewModel) toggles between these via the
     * `onConsoleSearchToggle` callback.
     */
    val searchQuery: String? = null,
)

/**
 * Top-level immutable input to [ChatHomeContent]. Every screen-side concern
 * that can change the visual is funnelled through this one type so the
 * stateless content stays trivially snapshot-testable.
 *
 * Covers the 9-state matrix for Chat (home).
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
    /** Pipeline currently bound to the thread; rendered in the TopAppBar subtitle and the empty-state caption. */
    val pipelineName: String = "default",
    /** Used / max token counts rendered in the TopAppBar subtitle (`"1.4k / 8k tok"`). */
    val tokensUsed: Int = 0,
    val tokensMax: Int = 0,
    /** Star/favorite flag rendered in the trailing TopAppBar action. */
    val favorite: Boolean = false,
    /**
     * Rich suggestion cards rendered inside the empty-state body. When
     * non-empty this list replaces the legacy [samplePrompts] chip row;
     * if both are empty the body renders the legacy "no prompts" copy.
     */
    val samplePromptCards: List<ChatHomeSamplePromptCard> = emptyList(),
    /**
     * Single-line agent status pill rendered above the composer
     * (`"[NODE]  idle · ready"`). `null` hides the pill.
     */
    val agentStatusLine: String? = null,
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
    val onConsoleSearchQueryChange: (String) -> Unit = {},
    val onConsoleCopyLine: (ConsoleLine) -> Unit = {},
    val onConsoleFilterByLineSource: (ConsoleSource) -> Unit = {},
    val onConsoleCopyAll: () -> Unit = {},
    val onConsoleClear: () -> Unit = {},
    val onCloseConsole: () -> Unit = {},
    val onHitlAllowOnce: () -> Unit = {},
    val onHitlAllowAlways: (() -> Unit)? = null,
    val onHitlReject: () -> Unit = {},
    val onHitlTypedConfirmChange: (String) -> Unit = {},
    val onClarificationReply: (String) -> Unit = {},
    /**
     * Fired when the user taps the Resume CTA on the interrupted-run card.
     * Hosts wire this to the checkpoint-resume mechanism.
     */
    val onResumeRun: () -> Unit = {},
    /**
     * Fired when the user taps the Discard CTA on the interrupted-run card.
     * Hosts settle the interrupted run as failed and drop the card.
     */
    val onDiscardRun: () -> Unit = {},
    val onErrorRetry: () -> Unit = {},
    val onTitleTripleTap: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onEditThread: (String) -> Unit = {},
    val onImportChat: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onSamplePromptCard: (ChatHomeSamplePromptCard) -> Unit = {},
    /**
     * Fired when the user taps the agent-status pill above the composer.
     * Hosts wire this to opening the console pane at the Partial snap so
     * the user can drill into pipeline activity in one tap (the pill
     * itself surfaces only a one-line summary).
     */
    val onAgentStatusClick: () -> Unit = {},
    /**
     * Fired when the user picks an action from a message-bubble's
     * long-press context menu (Copy / Rerun / Rate). The first argument
     * is the `ChatHomeMessageRow.id` of the row that was long-pressed —
     * hosts use it to look up the underlying domain message and act
     * (write to clipboard, re-send the prompt, open the rating sheet).
     *
     * Default no-op disables the long-press menu — the catalog only
     * enables `combinedClickable` when the underlying `onContextAction`
     * is non-null.
     */
    val onMessageContextAction: (rowId: String, action: ChatContextAction) -> Unit = { _, _ -> },
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
