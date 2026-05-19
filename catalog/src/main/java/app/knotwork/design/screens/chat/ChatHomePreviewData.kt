@file:Suppress(
    // File hosts ChatHomePreview + snapshotTag(); the data-oriented name reads better.
    "MatchingDeclarationName",
)

package app.knotwork.design.screens.chat

import app.knotwork.design.components.chat.ChatContent
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
import app.knotwork.design.components.console.SpanStatus

/**
 * Deterministic fixtures backing the 9-state `ChatHomeScreen` preview and
 * Roborazzi snapshot matrix.
 *
 * All timestamps are pre-formatted, all model / tool names are fictional but
 * representative of the agent's real surface. The factory functions are
 * `internal` so they can be exercised from the snapshot suite and from any
 * Android Studio preview inside `:catalog`, but `:app` code never reaches
 * them (it owns its own production stub).
 */
internal object ChatHomePreview {

    /** Pre-formatted thread title surfaced by every state. */
    const val THREAD_TITLE: String = "Yesterday's deploy"

    /** Display name of the (mock) currently-active local model. */
    const val MODEL_NAME: String = "Gemma 2 · 2B"

    /** Sample baseline conversation history used by Idle / Generating / HITL / Clarification / Error. */
    fun baselineMessages(): List<ChatHomeMessageRow> = listOf(
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
                source = "Pipeline editor refactor (UI overhaul), context-window meter " +
                    "for the chat header, and a memory-summary regression fix.",
            ),
            metadata = ChatMetadata(
                timestamp = "09:14",
                model = MODEL_NAME,
                tokens = 64,
            ),
        ),
        ChatHomeMessageRow(
            id = "u2",
            role = ChatRole.User,
            content = ChatContent.Text("Add a 30-minute meeting tomorrow at 10:00 to discuss the rollout."),
            metadata = ChatMetadata(timestamp = "09:15"),
        ),
    )

    /** Sample thread rows surfaced in the drawer overlay. */
    fun threadRows(activeId: String = "t1"): List<ChatHomeThreadRow> = listOf(
        ChatHomeThreadRow(
            id = "t1",
            title = "Yesterday's deploy",
            subtitle = "Today · 14 messages",
            selected = activeId == "t1",
        ),
        ChatHomeThreadRow(
            id = "t2",
            title = "Plan the weekend trip",
            subtitle = "Yesterday · 22 messages",
            selected = activeId == "t2",
        ),
        ChatHomeThreadRow(
            id = "t3",
            title = "Memory cleanup",
            subtitle = "2 days ago · 4 messages",
            selected = activeId == "t3",
        ),
    )

    /** Sample log lines surfaced inside the console overlay. */
    fun consoleLines(): List<ConsoleLine> = listOf(
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

    /** Sample variable rows surfaced inside the console Vars tab. */
    fun consoleVars(): List<app.knotwork.design.components.console.ConsoleVarRow> = listOf(
        app.knotwork.design.components.console.ConsoleVarRow(
            node = "lite_rt#1",
            key = "temperature",
            valueJson = "0.7",
        ),
        app.knotwork.design.components.console.ConsoleVarRow(
            node = "lite_rt#1",
            key = "topP",
            valueJson = "0.9",
        ),
    )

    /** Sample trace spans surfaced inside the console Traces tab. */
    fun consoleTraces(): List<ConsoleTraceSpan> = listOf(
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

    /** Sample prompt chips shown in the empty state. */
    fun samplePrompts(): List<String> = listOf(
        "Summarise the last meeting notes",
        "Plan my afternoon",
        "Search recent emails about \"deploy\"",
    )

    /** Empty state — no messages, no in-flight request. */
    fun empty(): ChatHomeViewState = ChatHomeViewState(
        visualState = ChatHomeVisualState.Empty,
        threadTitle = THREAD_TITLE,
        modelName = MODEL_NAME,
        samplePrompts = samplePrompts(),
    )

    /** Idle state — populated conversation, composer ready. */
    fun idle(): ChatHomeViewState = ChatHomeViewState(
        visualState = ChatHomeVisualState.Idle,
        threadTitle = THREAD_TITLE,
        modelName = MODEL_NAME,
        messages = baselineMessages(),
    )

    /** Generating state — the assistant is producing tokens. */
    fun generating(): ChatHomeViewState = ChatHomeViewState(
        visualState = ChatHomeVisualState.Generating,
        threadTitle = THREAD_TITLE,
        modelName = MODEL_NAME,
        messages = baselineMessages(),
        composerState = ComposerState.Generating,
    )

    /**
     * HITL Confirm state. Default risk is `Sensitive` (most common path); the
     * Phase 22 / Task 5 audit expanded the matrix to the 3 risk variants so
     * the snapshot baseline catches palette / glyph regressions across every
     * level the spec defines (see `compose/components/README.md §Buttons` and
     * `domain/models/ToolRisk.kt`).
     */
    fun hitlConfirm(risk: Risk = Risk.Sensitive): ChatHomeViewState {
        val toolName = when (risk) {
            Risk.Readonly -> "calendar.read_events"
            Risk.Sensitive -> "calendar.create_event"
            Risk.Destructive -> "calendar.delete_event"
        }
        val summary = when (risk) {
            Risk.Readonly -> "List the next three events on your work calendar."
            Risk.Sensitive ->
                "Add a 30-minute meeting \"Rollout sync\" to your work calendar tomorrow at 10:00."
            Risk.Destructive ->
                "Permanently delete the meeting \"Old sync\" from your work calendar."
        }
        return ChatHomeViewState(
            visualState = ChatHomeVisualState.HitlConfirm,
            threadTitle = THREAD_TITLE,
            modelName = MODEL_NAME,
            messages = baselineMessages() + ChatHomeMessageRow(
                id = "a-hitl",
                role = ChatRole.Assistant,
                content = ChatContent.Confirmation(
                    model = HitlConfirmationModel(
                        risk = risk,
                        toolName = toolName,
                        summary = summary,
                        arguments = mapOf(
                            "calendar" to "\"work\"",
                        ),
                        timestamp = "09:16",
                    ),
                ),
                metadata = ChatMetadata(timestamp = "09:16", model = MODEL_NAME),
            ),
        )
    }

    /** Clarification state — assistant asks the user a structured question. */
    fun clarification(): ChatHomeViewState = ChatHomeViewState(
        visualState = ChatHomeVisualState.Clarification,
        threadTitle = THREAD_TITLE,
        modelName = MODEL_NAME,
        messages = baselineMessages() + ChatHomeMessageRow(
            id = "a-clar",
            role = ChatRole.Assistant,
            content = ChatContent.Clarification(
                model = ClarificationCardModel(
                    question = "Which calendar should I add the meeting to?",
                    quickReplies = listOf("Work", "Personal", "Family"),
                ),
            ),
            metadata = ChatMetadata(timestamp = "09:16", model = MODEL_NAME),
        ),
    )

    /** Error state — model failed, inline error tile + retry. */
    fun error(): ChatHomeViewState = ChatHomeViewState(
        visualState = ChatHomeVisualState.Error,
        threadTitle = THREAD_TITLE,
        modelName = MODEL_NAME,
        messages = baselineMessages(),
        errorMessage = "Inference timed out after 30 s. Tap retry to try again.",
        composerState = ComposerState.Error(message = "Network unreachable"),
    )

    /** DrawerOpen state — alt-nav drawer overlayed. */
    fun drawerOpen(): ChatHomeViewState = ChatHomeViewState(
        visualState = ChatHomeVisualState.DrawerOpen,
        threadTitle = THREAD_TITLE,
        modelName = MODEL_NAME,
        messages = baselineMessages(),
        threads = threadRows(activeId = "t1"),
    )

    /** ConsoleExpanded state — console sheet overlayed at the Partial snap. */
    fun consoleExpanded(): ChatHomeViewState = ChatHomeViewState(
        visualState = ChatHomeVisualState.ConsoleExpanded,
        threadTitle = THREAD_TITLE,
        modelName = MODEL_NAME,
        messages = baselineMessages(),
        console = ChatHomeConsoleState(
            snap = ConsoleSnap.Partial,
            tab = ConsoleTab.Logs,
            logs = consoleLines(),
            vars = consoleVars(),
            traces = consoleTraces(),
            filter = ConsoleFilter.allOn,
        ),
    )

    /** All nine canonical states in the spec's documented order. */
    fun allStates(): List<ChatHomeViewState> = listOf(
        empty(),
        idle(),
        generating(),
        hitlConfirm(),
        clarification(),
        error(),
        drawerOpen(),
        consoleExpanded(),
    )
}

/**
 * Tag attached to each snapshot file name. Keeps the snapshot order in
 * lock-step with [ChatHomePreview.allStates] without depending on the enum
 * being persisted across renames.
 */
internal fun ChatHomeVisualState.snapshotTag(): String = when (this) {
    ChatHomeVisualState.Empty -> "empty"
    ChatHomeVisualState.Idle -> "idle"
    ChatHomeVisualState.Generating -> "generating"
    ChatHomeVisualState.HitlConfirm -> "hitl_confirm"
    ChatHomeVisualState.Clarification -> "clarification"
    ChatHomeVisualState.Error -> "error"
    ChatHomeVisualState.DrawerOpen -> "drawer_open"
    ChatHomeVisualState.ConsoleExpanded -> "console_expanded"
}
