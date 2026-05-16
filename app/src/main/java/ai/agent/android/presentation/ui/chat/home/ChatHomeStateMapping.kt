package ai.agent.android.presentation.ui.chat.home

import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatMessageStatus
import app.knotwork.design.components.chat.ChatMetadata
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.chat.ClarificationCardModel
import app.knotwork.design.components.chat.ComposerState
import app.knotwork.design.components.chat.HitlConfirmationModel
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsoleFilter
import app.knotwork.design.components.console.ConsoleLevel
import app.knotwork.design.components.console.ConsoleLine
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleSource
import app.knotwork.design.components.console.ConsoleTab
import app.knotwork.design.components.console.ConsoleTraceSpan
import app.knotwork.design.components.console.ConsoleVarRow
import app.knotwork.design.components.console.SpanStatus
import app.knotwork.design.screens.chat.ChatHomeConsoleState
import app.knotwork.design.screens.chat.ChatHomeMessageRow
import app.knotwork.design.screens.chat.ChatHomeThreadRow
import app.knotwork.design.screens.chat.ChatHomeViewState
import app.knotwork.design.screens.chat.ChatHomeVisualState

/**
 * Pure-Kotlin projection of the sealed [ChatHomeUiState] onto the catalog
 * [ChatHomeViewState] consumed by `ChatHomeContent`. Lives in `:app` because
 * the catalog cannot reach `ai.agent.android.*` (Clean Architecture +
 * `decisions.md §3` keep `:catalog` free of `:app` types).
 *
 * The mapping is intentionally chunky — every state owns a complete fixture
 * block. The stub-VM stage holds no real conversation history, so a
 * deterministic story unique to each variant is the cheapest way to make
 * the state picker meaningful while the real backend wiring is still
 * pending (post-v0.1).
 *
 * @param state which UI state to render.
 * @param threadTitle pre-formatted thread title for the TopAppBar.
 * @param modelName display name of the currently-active model.
 * @param composerValue current composer input value.
 * @param pendingTypedConfirm typed-confirm input for Destructive HITL.
 * @return the immutable view-state passed directly to `ChatHomeContent`.
 */
@Suppress("LongMethod") // Single switch over 8 variants; splitting would just shuffle the fixtures.
fun ChatHomeUiState.toViewState(
    threadTitle: String,
    modelName: String,
    messages: List<ChatHomeMessageRow> = emptyList(),
    composerValue: String = "",
    pendingTypedConfirm: String = "",
): ChatHomeViewState = when (this) {
    is ChatHomeUiState.Empty -> ChatHomeViewState(
        visualState = ChatHomeVisualState.Empty,
        threadTitle = threadTitle,
        modelName = modelName,
        composerValue = composerValue,
        samplePrompts = SAMPLE_PROMPTS,
    )

    is ChatHomeUiState.Idle -> ChatHomeViewState(
        visualState = ChatHomeVisualState.Idle,
        threadTitle = threadTitle,
        modelName = modelName,
        messages = messages,
        composerValue = composerValue,
    )

    is ChatHomeUiState.Generating -> ChatHomeViewState(
        visualState = ChatHomeVisualState.Generating,
        threadTitle = threadTitle,
        modelName = modelName,
        messages = messages,
        composerValue = composerValue,
        composerState = ComposerState.Generating,
    )

    is ChatHomeUiState.HitlConfirm -> ChatHomeViewState(
        visualState = ChatHomeVisualState.HitlConfirm,
        threadTitle = threadTitle,
        modelName = modelName,
        messages = messages + hitlRow(modelName, risk),
        composerValue = composerValue,
        pendingTypedConfirm = pendingTypedConfirm,
    )

    is ChatHomeUiState.Clarification -> ChatHomeViewState(
        visualState = ChatHomeVisualState.Clarification,
        threadTitle = threadTitle,
        modelName = modelName,
        messages = messages + clarificationRow(modelName),
        composerValue = composerValue,
    )

    is ChatHomeUiState.Error -> ChatHomeViewState(
        visualState = ChatHomeVisualState.Error,
        threadTitle = threadTitle,
        modelName = modelName,
        messages = messages,
        composerValue = composerValue,
        composerState = ComposerState.Error(message = message),
        errorMessage = message,
    )

    is ChatHomeUiState.DrawerOpen -> ChatHomeViewState(
        visualState = ChatHomeVisualState.DrawerOpen,
        threadTitle = threadTitle,
        modelName = modelName,
        messages = messages,
        composerValue = composerValue,
        threads = sampleThreads(),
    )

    is ChatHomeUiState.ConsoleExpanded -> ChatHomeViewState(
        visualState = ChatHomeVisualState.ConsoleExpanded,
        threadTitle = threadTitle,
        modelName = modelName,
        messages = messages,
        composerValue = composerValue,
        console = ChatHomeConsoleState(
            snap = snap,
            tab = ConsoleTab.Logs,
            logs = sampleConsoleLines(),
            vars = sampleConsoleVars(),
            traces = sampleConsoleTraces(),
            filter = ConsoleFilter.allOn,
        ),
    )
}

/** Chips surfaced in the empty state — these are the same strings the spec calls out. */
private val SAMPLE_PROMPTS: List<String> = listOf(
    "Summarise the last meeting notes",
    "Plan my afternoon",
    "Search recent emails about \"deploy\"",
)

/** Pre-canned baseline conversation used by every non-Empty state. */
internal fun baselineMessages(modelName: String): List<ChatHomeMessageRow> = listOf(
    ChatHomeMessageRow(
        id = "u1",
        role = ChatRole.User,
        content = ChatContent.Text("Summarise the three PRs that landed yesterday."),
        metadata = ChatMetadata(timestamp = "09:14"),
    ),
    ChatHomeMessageRow(
        id = "a1",
        role = ChatRole.Assistant,
        content = ChatContent.Markdown(
            source = "Pipeline editor refactor, context-window meter for the chat header, " +
                "and a memory-summary regression fix.",
        ),
        metadata = ChatMetadata(
            timestamp = "09:14",
            model = modelName,
            tokens = 64,
            status = ChatMessageStatus.Sent,
        ),
    ),
    ChatHomeMessageRow(
        id = "u2",
        role = ChatRole.User,
        content = ChatContent.Text("Add a 30-minute meeting tomorrow at 10:00 to discuss the rollout."),
        metadata = ChatMetadata(timestamp = "09:15"),
    ),
)

/** Trailing HITL confirmation row appended in the HitlConfirm state. */
internal fun hitlRow(modelName: String, risk: Risk): ChatHomeMessageRow {
    val toolName = when (risk) {
        Risk.Readonly -> "calendar.list_events"
        Risk.Sensitive -> "calendar.create_event"
        Risk.Destructive -> "fs.delete_file"
    }
    val summary = when (risk) {
        Risk.Readonly -> "Read tomorrow's events from your work calendar."
        Risk.Sensitive -> "Add a 30-minute meeting \"Rollout sync\" to your work calendar tomorrow at 10:00."
        Risk.Destructive -> "Permanently remove /Users/me/old-notes.md (4.2 KB)."
    }
    val arguments = when (risk) {
        Risk.Readonly -> mapOf("calendar" to "\"work\"", "range" to "\"tomorrow\"")
        Risk.Sensitive -> mapOf(
            "title" to "\"Rollout sync\"",
            "duration" to "30",
            "calendar" to "\"work\"",
        )
        Risk.Destructive -> mapOf(
            "path" to "\"/Users/me/old-notes.md\"",
            "recursive" to "false",
        )
    }
    return ChatHomeMessageRow(
        id = "a-hitl",
        role = ChatRole.Assistant,
        content = ChatContent.Confirmation(
            model = HitlConfirmationModel(
                risk = risk,
                toolName = toolName,
                summary = summary,
                arguments = arguments,
                timestamp = "09:16",
            ),
        ),
        metadata = ChatMetadata(timestamp = "09:16", model = modelName),
    )
}

/** Trailing clarification row appended in the Clarification state. */
internal fun clarificationRow(modelName: String): ChatHomeMessageRow = ChatHomeMessageRow(
    id = "a-clar",
    role = ChatRole.Assistant,
    content = ChatContent.Clarification(
        model = ClarificationCardModel(
            question = "Which calendar should I add the meeting to?",
            quickReplies = listOf("Work", "Personal", "Family"),
        ),
    ),
    metadata = ChatMetadata(timestamp = "09:16", model = modelName),
)

/** Sample threads shown inside the drawer overlay. */
internal fun sampleThreads(): List<ChatHomeThreadRow> = listOf(
    ChatHomeThreadRow(
        id = "t1",
        title = "Yesterday's deploy",
        subtitle = "Today · 14 messages",
        selected = true,
    ),
    ChatHomeThreadRow(
        id = "t2",
        title = "Plan the weekend trip",
        subtitle = "Yesterday · 22 messages",
    ),
    ChatHomeThreadRow(
        id = "t3",
        title = "Memory cleanup",
        subtitle = "2 days ago · 4 messages",
    ),
)

/** Sample console log lines surfaced when the console pane is expanded. */
internal fun sampleConsoleLines(): List<ConsoleLine> = listOf(
    ConsoleLine(
        timestamp = "09:14:00.012",
        source = ConsoleSource.RUNTIME,
        level = ConsoleLevel.Info,
        text = "pipeline=default loaded (3 nodes)",
    ),
    ConsoleLine(
        timestamp = "09:14:00.118",
        source = ConsoleSource.NODE,
        level = ConsoleLevel.Trace,
        text = "INPUT → LITE_RT prompt rendered (412 tokens)",
    ),
    ConsoleLine(
        timestamp = "09:14:02.341",
        source = ConsoleSource.TOOL,
        level = ConsoleLevel.Warn,
        text = "calendar.create_event awaiting user approval (Sensitive)",
    ),
)

/** Sample console var rows surfaced inside the Vars tab. */
internal fun sampleConsoleVars(): List<ConsoleVarRow> = listOf(
    ConsoleVarRow(node = "lite_rt#1", key = "temperature", valueJson = "0.7"),
    ConsoleVarRow(node = "lite_rt#1", key = "topP", valueJson = "0.9"),
)

/** Sample console trace spans surfaced inside the Traces tab. */
internal fun sampleConsoleTraces(): List<ConsoleTraceSpan> = listOf(
    ConsoleTraceSpan(
        name = "lite_rt#1.generate",
        durationMs = 1840L,
        startedAt = "09:14:00.118",
        status = SpanStatus.Ok,
    ),
    ConsoleTraceSpan(
        name = "calendar.create_event",
        durationMs = 86L,
        startedAt = "09:14:02.341",
        status = SpanStatus.Ok,
    ),
)

/**
 * Stable identifiers for every entry in the debug state picker. Kept apart
 * from [ChatHomeUiState] so adding a state to the picker does not force
 * every consumer of the sealed hierarchy to recompile.
 */
internal object DebugStateIds {
    const val EMPTY: String = "empty"
    const val IDLE: String = "idle"
    const val GENERATING: String = "generating"
    const val HITL_READONLY: String = "hitl_readonly"
    const val HITL_SENSITIVE: String = "hitl_sensitive"
    const val HITL_DESTRUCTIVE: String = "hitl_destructive"
    const val CLARIFICATION: String = "clarification"
    const val ERROR: String = "error"
    const val DRAWER_OPEN: String = "drawer_open"
    const val CONSOLE_PEEK: String = "console_peek"
    const val CONSOLE_PARTIAL: String = "console_partial"
    const val CONSOLE_FULL: String = "console_full"
}

/** Maps a [DebugStateIds] value back to the concrete state the picker should set. */
internal fun debugStateForId(id: String): ChatHomeUiState? = when (id) {
    DebugStateIds.EMPTY -> ChatHomeUiState.Empty
    DebugStateIds.IDLE -> ChatHomeUiState.Idle
    DebugStateIds.GENERATING -> ChatHomeUiState.Generating
    DebugStateIds.HITL_READONLY -> ChatHomeUiState.HitlConfirm(Risk.Readonly)
    DebugStateIds.HITL_SENSITIVE -> ChatHomeUiState.HitlConfirm(Risk.Sensitive)
    DebugStateIds.HITL_DESTRUCTIVE -> ChatHomeUiState.HitlConfirm(Risk.Destructive)
    DebugStateIds.CLARIFICATION -> ChatHomeUiState.Clarification
    DebugStateIds.ERROR -> ChatHomeUiState.Error(message = "Something went wrong while generating the reply.")
    DebugStateIds.DRAWER_OPEN -> ChatHomeUiState.DrawerOpen
    DebugStateIds.CONSOLE_PEEK -> ChatHomeUiState.ConsoleExpanded(ConsoleSnap.Peek)
    DebugStateIds.CONSOLE_PARTIAL -> ChatHomeUiState.ConsoleExpanded(ConsoleSnap.Partial)
    DebugStateIds.CONSOLE_FULL -> ChatHomeUiState.ConsoleExpanded(ConsoleSnap.Full)
    else -> null
}
