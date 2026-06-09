package app.knotwork.android.presentation.ui.chat.home

import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ComposerState
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsoleLevel
import app.knotwork.design.components.console.ConsoleLine
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleSource
import app.knotwork.design.components.console.ConsoleTab
import app.knotwork.design.components.console.ConsoleTraceSpan
import app.knotwork.design.components.console.ConsoleVarRow
import app.knotwork.design.components.console.SpanStatus
import app.knotwork.design.screens.chat.ChatHomeVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin unit tests for [ChatHomeUiState.toViewState] — the boundary
 * mapper between the sealed UI state owned by `:app` and the
 * [app.knotwork.design.screens.chat.ChatHomeViewState] consumed by the
 * stateless `ChatHomeContent` in `:catalog`.
 *
 * Each test pins one variant of the sealed state and asserts the
 * downstream view-state has the right [ChatHomeVisualState], the right
 * trailing tile (HITL / clarification / error), and the right composer
 * machinery.
 */
class ChatHomeStateMappingTest {

    private val title = "Yesterday's deploy"
    private val model = "Gemma 2 · 2B"

    @Test
    fun `Empty maps to ChatHomeVisualState_Empty with sample prompt cards and no messages`() {
        val view = ChatHomeUiState.Empty.toViewState(title, model)
        assertEquals(ChatHomeVisualState.Empty, view.visualState)
        assertTrue(view.messages.isEmpty())
        // The empty-state body now renders rich suggestion cards
        // (mockup) instead of the legacy
        // single-line chip row.
        assertTrue(view.samplePromptCards.isNotEmpty())
        assertNull(view.errorMessage)
    }

    @Test
    fun `Idle maps to ChatHomeVisualState_Idle and threads supplied messages`() {
        val supplied = baselineMessages(model)
        val view = ChatHomeUiState.Idle.toViewState(title, model, messages = supplied)
        assertEquals(ChatHomeVisualState.Idle, view.visualState)
        assertEquals(supplied, view.messages)
        assertEquals(ComposerState.Idle, view.composerState)
    }

    @Test
    fun `Idle with no supplied messages renders an empty list`() {
        val view = ChatHomeUiState.Idle.toViewState(title, model)
        assertEquals(ChatHomeVisualState.Idle, view.visualState)
        assertTrue(view.messages.isEmpty())
    }

    @Test
    fun `Generating pairs the visual with ComposerState_Generating`() {
        val view = ChatHomeUiState.Generating.toViewState(title, model)
        assertEquals(ChatHomeVisualState.Generating, view.visualState)
        assertTrue(view.composerState is ComposerState.Generating)
    }

    @Test
    fun `HitlConfirm appends a Sensitive Confirmation row to the baseline`() {
        val view = ChatHomeUiState.HitlConfirm(Risk.Sensitive).toViewState(title, model)
        assertEquals(ChatHomeVisualState.HitlConfirm, view.visualState)
        val tail = view.messages.last().content
        assertTrue(tail is ChatContent.Confirmation)
        val confirmation = tail as ChatContent.Confirmation
        assertEquals(Risk.Sensitive, confirmation.model.risk)
        assertEquals("calendar.create_event", confirmation.model.toolName)
    }

    @Test
    fun `HitlConfirm with Destructive risk surfaces a destructive tool`() {
        val view = ChatHomeUiState.HitlConfirm(Risk.Destructive).toViewState(title, model)
        val tail = view.messages.last().content as ChatContent.Confirmation
        assertEquals(Risk.Destructive, tail.model.risk)
        assertEquals("fs.delete_file", tail.model.toolName)
    }

    @Test
    fun `HitlConfirm threads pendingTypedConfirm through to the view state`() {
        val view = ChatHomeUiState.HitlConfirm(Risk.Destructive)
            .toViewState(title, model, pendingTypedConfirm = "ye")
        assertEquals("ye", view.pendingTypedConfirm)
    }

    @Test
    fun `Clarification appends a clarification row with quick replies`() {
        val view = ChatHomeUiState.Clarification.toViewState(title, model)
        assertEquals(ChatHomeVisualState.Clarification, view.visualState)
        val tail = view.messages.last().content
        assertTrue(tail is ChatContent.Clarification)
        val clarification = tail as ChatContent.Clarification
        assertEquals(listOf("Work", "Personal", "Family"), clarification.model.quickReplies)
    }

    @Test
    fun `Error carries the message into both the inline tile and the composer banner`() {
        val state = ChatHomeUiState.Error(message = "Network unreachable")
        val view = state.toViewState(title, model)
        assertEquals(ChatHomeVisualState.Error, view.visualState)
        assertEquals("Network unreachable", view.errorMessage)
        val banner = view.composerState
        assertTrue(banner is ComposerState.Error)
        assertEquals("Network unreachable", (banner as ComposerState.Error).message)
    }

    @Test
    fun `DrawerOpen populates the threads list and hides any error message`() {
        val view = ChatHomeUiState.DrawerOpen.toViewState(title, model)
        assertEquals(ChatHomeVisualState.DrawerOpen, view.visualState)
        assertTrue(view.threads.isNotEmpty())
        assertNull(view.errorMessage)
    }

    @Test
    fun `consoleSnap threads through and forwards supplied console data regardless of state`() {
        val logs = listOf(
            ConsoleLine(
                timestamp = "09:14:00.000",
                source = ConsoleSource.NODE,
                level = ConsoleLevel.Trace,
                text = "▶ LITE_RT",
            ),
        )
        val vars = listOf(ConsoleVarRow(node = "LITE_RT#a", key = "input", valueJson = "\"x\""))
        val traces = listOf(
            ConsoleTraceSpan(name = "LITE_RT", durationMs = 10L, startedAt = "09:14:00.000", status = SpanStatus.Ok),
        )

        // Console pane open while the chat state is Generating — overlay
        // and underlying state are orthogonal post-refactor.
        val view = ChatHomeUiState.Generating.toViewState(
            threadTitle = title,
            modelName = model,
            consoleLogs = logs,
            consoleVars = vars,
            consoleTraces = traces,
            consoleTab = ConsoleTab.Traces,
            consoleSnap = ConsoleSnap.Full,
        )

        assertEquals(ChatHomeVisualState.Generating, view.visualState)
        assertEquals(ConsoleSnap.Full, view.console.snap)
        assertEquals(ConsoleTab.Traces, view.console.tab)
        assertEquals(logs, view.console.logs)
        assertEquals(vars, view.console.vars)
        assertEquals(traces, view.console.traces)
    }

    @Test
    fun `console snap null means the overlay is closed`() {
        val view = ChatHomeUiState.Idle.toViewState(title, model)
        assertNull(view.console.snap)
        assertTrue(view.console.logs.isEmpty())
        assertTrue(view.console.vars.isEmpty())
        assertTrue(view.console.traces.isEmpty())
        assertEquals(ConsoleTab.Logs, view.console.tab)
    }

    @Test
    fun `composerValue is threaded through every state`() {
        val states = listOf(
            ChatHomeUiState.Empty,
            ChatHomeUiState.Idle,
            ChatHomeUiState.Generating,
            ChatHomeUiState.HitlConfirm(Risk.Readonly),
            ChatHomeUiState.Clarification,
            ChatHomeUiState.Error("boom"),
            ChatHomeUiState.DrawerOpen,
        )
        states.forEach { state ->
            val view = state.toViewState(title, model, composerValue = "draft")
            assertEquals("composer for ${state::class.simpleName}", "draft", view.composerValue)
        }
    }

    @Test
    fun `debugStateForId maps top-level state ids back to concrete states`() {
        val ids = listOf(
            DebugStateIds.EMPTY,
            DebugStateIds.IDLE,
            DebugStateIds.GENERATING,
            DebugStateIds.HITL_READONLY,
            DebugStateIds.HITL_SENSITIVE,
            DebugStateIds.HITL_DESTRUCTIVE,
            DebugStateIds.CLARIFICATION,
            DebugStateIds.ERROR,
            DebugStateIds.DRAWER_OPEN,
        )
        ids.forEach { id ->
            assertNotNull("missing mapping for $id", debugStateForId(id))
        }
        // Console picker ids no longer round-trip through `debugStateForId`
        // — the console is an independent overlay and is opened via
        // `debugConsoleSnapForId`.
        assertNull(debugStateForId(DebugStateIds.CONSOLE_PARTIAL))
        assertNull(debugStateForId(DebugStateIds.CONSOLE_FULL))
        assertNull(debugStateForId("not_a_real_id"))
    }

    @Test
    fun `debugConsoleSnapForId maps console picker ids to snaps`() {
        assertEquals(ConsoleSnap.Partial, debugConsoleSnapForId(DebugStateIds.CONSOLE_PARTIAL))
        assertEquals(ConsoleSnap.Full, debugConsoleSnapForId(DebugStateIds.CONSOLE_FULL))
        assertNull(debugConsoleSnapForId(DebugStateIds.EMPTY))
    }
}
