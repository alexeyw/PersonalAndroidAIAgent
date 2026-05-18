package ai.agent.android.presentation.ui.chat.home

import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsoleSnap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit-tests for the stub [ChatHomeViewModel] driving the 9-state matrix
 * of the redesigned chat home (Phase 21 / Task 8).
 *
 * The VM has no collaborators — every behaviour is observable via the
 * `StateFlow`s it exposes. Tests cover:
 *  - initial state == `Empty` (no messages yet);
 *  - `forceState(...)` flips state without side effects;
 *  - composer / typed-confirm hoisting;
 *  - drawer / console open + close transitions, including the
 *    "resting state depends on whether messages exist" rule;
 *  - `sendMessage` appends the user row, flips to `Generating`, then
 *    appends a canned assistant reply and settles to `Idle`;
 *  - `stopGeneration` collapses `Generating` to `Idle` immediately
 *    (and does not append the canned reply).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatHomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ChatHomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChatHomeViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Empty with no messages`() {
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `forceState flips state to the supplied variant`() {
        viewModel.forceState(ChatHomeUiState.Empty)
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
        viewModel.forceState(ChatHomeUiState.HitlConfirm(Risk.Destructive))
        assertEquals(ChatHomeUiState.HitlConfirm(Risk.Destructive), viewModel.state.value)
    }

    @Test
    fun `composer value is hoisted via onComposerValueChange`() {
        viewModel.onComposerValueChange("hello")
        assertEquals("hello", viewModel.composerValue.value)
    }

    @Test
    fun `typed-confirm value is hoisted via onTypedConfirmChange`() {
        viewModel.onTypedConfirmChange("yes")
        assertEquals("yes", viewModel.pendingTypedConfirm.value)
    }

    @Test
    fun `openDrawer + closeDrawer with no messages settles back on Empty`() {
        viewModel.openDrawer()
        assertEquals(ChatHomeUiState.DrawerOpen, viewModel.state.value)
        viewModel.closeDrawer()
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `closeDrawer settles on Idle when there is at least one message`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
        viewModel.openDrawer()
        viewModel.closeDrawer()
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `closeDrawer is a no-op when state is not DrawerOpen`() {
        viewModel.forceState(ChatHomeUiState.HitlConfirm(Risk.Sensitive))
        viewModel.closeDrawer()
        assertEquals(ChatHomeUiState.HitlConfirm(Risk.Sensitive), viewModel.state.value)
    }

    @Test
    fun `selectThread updates the title, clears messages, and lands on Empty`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()
        viewModel.openDrawer()
        viewModel.selectThread("t42")
        assertEquals("Thread t42", viewModel.threadTitle.value)
        assertTrue(viewModel.messages.value.isEmpty())
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `openConsole sets ConsoleExpanded with the supplied snap`() {
        viewModel.openConsole(ConsoleSnap.Full)
        assertEquals(ChatHomeUiState.ConsoleExpanded(ConsoleSnap.Full), viewModel.state.value)
    }

    @Test
    fun `setConsoleSnap updates the current snap point in-place`() {
        viewModel.openConsole(ConsoleSnap.Peek)
        viewModel.setConsoleSnap(ConsoleSnap.Partial)
        assertEquals(ChatHomeUiState.ConsoleExpanded(ConsoleSnap.Partial), viewModel.state.value)
    }

    @Test
    fun `setConsoleSnap is a no-op when console is not expanded`() {
        viewModel.setConsoleSnap(ConsoleSnap.Full)
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `closeConsole settles on Empty when there are no messages`() {
        viewModel.openConsole(ConsoleSnap.Partial)
        viewModel.closeConsole()
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `sendMessage with blank composer is a no-op`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("   ")
        viewModel.sendMessage()
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `sendMessage appends the user row immediately and clears the composer`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("hello")
        viewModel.sendMessage()
        assertEquals("", viewModel.composerValue.value)
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
        val messages = viewModel.messages.value
        assertEquals(1, messages.size)
        assertEquals(ChatRole.User, messages.single().role)
        assertEquals("hello", (messages.single().content as ChatContent.Text).text)
    }

    @Test
    fun `sendMessage round-trip appends a canned assistant reply and settles on Idle`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceTimeBy(ChatHomeViewModel.STUB_GENERATING_DELAY_MS + 1)
        advanceUntilIdle()
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertEquals(ChatRole.User, messages[0].role)
        assertEquals(ChatRole.Assistant, messages[1].role)
        val reply = messages[1].content as ChatContent.Text
        assertEquals(ChatHomeViewModel.CANNED_REPLY, reply.text)
        assertNotNull(messages[1].metadata.model)
    }

    @Test
    fun `stopGeneration collapses Generating to Idle without appending the canned reply`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
        viewModel.stopGeneration()
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
        advanceUntilIdle()
        // The deferred canned reply must NOT land after the user cancelled.
        assertEquals(1, viewModel.messages.value.size)
    }

    @Test
    fun `stopGeneration is a no-op when state is not Generating`() {
        viewModel.forceState(ChatHomeUiState.HitlConfirm(Risk.Sensitive))
        viewModel.stopGeneration()
        assertEquals(ChatHomeUiState.HitlConfirm(Risk.Sensitive), viewModel.state.value)
    }

    @Test
    fun `pending sendMessage delay does not overwrite a forced non-Generating state`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        viewModel.forceState(ChatHomeUiState.Empty)
        advanceUntilIdle()
        assertTrue("Forced state must survive the in-flight stub", viewModel.state.value is ChatHomeUiState.Empty)
        // Canned reply must NOT have landed since the state was forced away.
        assertEquals(1, viewModel.messages.value.size)
    }
}
