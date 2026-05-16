package ai.agent.android.presentation.ui.chat.home

import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ComposerState
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsoleSnap
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
    fun `Empty maps to ChatHomeVisualState_Empty with sample prompts and no messages`() {
        val view = ChatHomeUiState.Empty.toViewState(title, model)
        assertEquals(ChatHomeVisualState.Empty, view.visualState)
        assertTrue(view.messages.isEmpty())
        assertTrue(view.samplePrompts.isNotEmpty())
        assertNull(view.errorMessage)
    }

    @Test
    fun `Idle maps to ChatHomeVisualState_Idle with baseline messages`() {
        val view = ChatHomeUiState.Idle.toViewState(title, model)
        assertEquals(ChatHomeVisualState.Idle, view.visualState)
        assertEquals(baselineMessages(model), view.messages)
        assertEquals(ComposerState.Idle, view.composerState)
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
    fun `ConsoleExpanded threads the snap point through and populates the panel`() {
        val view = ChatHomeUiState.ConsoleExpanded(ConsoleSnap.Full).toViewState(title, model)
        assertEquals(ChatHomeVisualState.ConsoleExpanded, view.visualState)
        assertEquals(ConsoleSnap.Full, view.console.snap)
        assertTrue(view.console.logs.isNotEmpty())
        assertTrue(view.console.vars.isNotEmpty())
        assertTrue(view.console.traces.isNotEmpty())
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
            ChatHomeUiState.ConsoleExpanded(ConsoleSnap.Peek),
        )
        states.forEach { state ->
            val view = state.toViewState(title, model, composerValue = "draft")
            assertEquals("composer for ${state::class.simpleName}", "draft", view.composerValue)
        }
    }

    @Test
    fun `debugStateForId maps every documented id back to a concrete state`() {
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
            DebugStateIds.CONSOLE_PEEK,
            DebugStateIds.CONSOLE_PARTIAL,
            DebugStateIds.CONSOLE_FULL,
        )
        ids.forEach { id ->
            assertNotNull("missing mapping for $id", debugStateForId(id))
        }
        assertNull(debugStateForId("not_a_real_id"))
    }
}
