package ai.agent.android.presentation.ui.chat.home

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit-tests for the stub [ChatHomeViewModel] driving the 9-state matrix
 * of the redesigned chat home (Phase 21 / Task 8).
 *
 * The VM has no collaborators — every behaviour is observable via the
 * `StateFlow`s it exposes. Tests cover:
 *  - initial state == `Idle`;
 *  - `forceState(...)` flips state without side effects;
 *  - composer/value/typed-confirm hoisting;
 *  - drawer / console open + close transitions;
 *  - `sendMessage` performs the stub `Idle → Generating → Idle` round-trip
 *    inside [STUB_GENERATING_DELAY_MS];
 *  - `stopGeneration` collapses `Generating` to `Idle` immediately.
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
    fun `initial state is Idle`() {
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
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
    fun `openDrawer flips state to DrawerOpen and closeDrawer settles on Idle`() {
        viewModel.openDrawer()
        assertEquals(ChatHomeUiState.DrawerOpen, viewModel.state.value)
        viewModel.closeDrawer()
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `closeDrawer is a no-op when state is not DrawerOpen`() {
        viewModel.forceState(ChatHomeUiState.Empty)
        viewModel.closeDrawer()
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `selectThread updates the title and closes an open drawer`() {
        viewModel.openDrawer()
        viewModel.selectThread("t42")
        assertEquals("Thread t42", viewModel.threadTitle.value)
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
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
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `closeConsole settles on Idle when the pane is expanded`() {
        viewModel.openConsole(ConsoleSnap.Partial)
        viewModel.closeConsole()
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `sendMessage with blank composer is a no-op`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("   ")
        viewModel.sendMessage()
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `sendMessage flips to Generating then collapses to Idle after the stub delay`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        assertEquals("", viewModel.composerValue.value)
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
        advanceTimeBy(ChatHomeViewModel.STUB_GENERATING_DELAY_MS - 1)
        advanceUntilIdle()
        // Window is closed — state must have settled back to Idle.
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `stopGeneration collapses Generating to Idle immediately`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
        viewModel.stopGeneration()
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `stopGeneration is a no-op when state is not Generating`() {
        viewModel.forceState(ChatHomeUiState.Empty)
        viewModel.stopGeneration()
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `pending sendMessage delay does not overwrite a forced non-Generating state`() = runTest(testDispatcher) {
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        viewModel.forceState(ChatHomeUiState.Empty)
        advanceUntilIdle()
        assertTrue("Forced state must survive the in-flight stub", viewModel.state.value is ChatHomeUiState.Empty)
    }
}
