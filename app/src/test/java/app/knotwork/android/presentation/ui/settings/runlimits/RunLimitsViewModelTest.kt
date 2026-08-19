package app.knotwork.android.presentation.ui.settings.runlimits

import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the one piece of real logic on the run-limits screen: when a
 * background ceiling stops following the interactive one.
 *
 * That relationship is invisible in the value — the background step ceiling
 * *defaults to* the interactive one, so both rows read the same number — and it
 * ends permanently the moment the key is written. Every test here is about not
 * ending it by accident.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunLimitsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val steps = MutableStateFlow(DEFAULT_STEPS)
    private val stepsBackground = MutableStateFlow(DEFAULT_STEPS)
    private val stepsBackgroundIsSet = MutableStateFlow(false)
    private val tokens = MutableStateFlow(DEFAULT_TOKENS)
    private val tokensBackground = MutableStateFlow(DEFAULT_TOKENS_BACKGROUND)

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Stubbed explicitly rather than relaxed: a relaxed mock hands back a
        // Flow that never emits, so every assertion below would pass against
        // the ViewModel's defaults instead of against the repository.
        repository = mockk(relaxed = true)
        every { repository.pipelineMaxSteps } returns steps
        every { repository.pipelineMaxStepsBackground } returns stepsBackground
        every { repository.pipelineMaxStepsBackgroundIsSet } returns stepsBackgroundIsSet
        every { repository.runMaxTokens } returns tokens
        every { repository.runMaxTokensBackground } returns tokensBackground
        coEvery { repository.setPipelineMaxStepsBackground(any()) } returns Unit
        coEvery { repository.setPipelineMaxSteps(any()) } returns Unit
        coEvery { repository.setRunMaxTokens(any()) } returns Unit
        coEvery { repository.setRunMaxTokensBackground(any()) } returns Unit
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `given the background key is unset then the row reports itself inherited`() = runTest(dispatcher) {
        val viewModel = RunLimitsViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.stepsBackgroundInherited)
    }

    @Test
    fun `given the background key is set then the row stands on its own`() = runTest(dispatcher) {
        stepsBackgroundIsSet.value = true
        val viewModel = RunLimitsViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.stepsBackgroundInherited)
    }

    @Test
    fun `given the interactive cap was raised then the inherited background row follows it`() = runTest(dispatcher) {
        // The repository resolves the inherited value; the screen must show it
        // rather than the shipped constant, or a user who raised their cap sees
        // a number their triggers do not actually use.
        steps.value = 40
        stepsBackground.value = 40
        val viewModel = RunLimitsViewModel(repository)
        advanceUntilIdle()

        assertEquals(40, viewModel.state.value.stepsBackground)
        assertTrue(viewModel.state.value.stepsBackgroundInherited)
    }

    @Test
    fun `given a drag that lands back on the current value then the key is not written`() = runTest(dispatcher) {
        val viewModel = RunLimitsViewModel(repository)
        advanceUntilIdle()

        // Touch, move, return, lift. The user changed nothing, so the
        // inheritance must survive — writing the key here would detach the
        // background ceiling for good on a gesture that expressed no decision.
        viewModel.onStepsBackgroundChange(30)
        viewModel.onStepsBackgroundChange(DEFAULT_STEPS)
        viewModel.onStepsBackgroundCommit()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.setPipelineMaxStepsBackground(any()) }
    }

    @Test
    fun `given a committed change then the background key is written once`() = runTest(dispatcher) {
        val viewModel = RunLimitsViewModel(repository)
        advanceUntilIdle()

        viewModel.onStepsBackgroundChange(30)
        viewModel.onStepsBackgroundCommit()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setPipelineMaxStepsBackground(30) }
    }

    @Test
    fun `given a drag in progress then nothing is persisted until it ends`() = runTest(dispatcher) {
        val viewModel = RunLimitsViewModel(repository)
        advanceUntilIdle()

        viewModel.onStepsChange(20)
        viewModel.onStepsChange(25)
        viewModel.onStepsChange(30)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.setPipelineMaxSteps(any()) }
        assertEquals("the slider still has to move while dragging", 30, viewModel.state.value.steps)
    }

    @Test
    fun `given the interactive drag ends then the final value is persisted`() = runTest(dispatcher) {
        val viewModel = RunLimitsViewModel(repository)
        advanceUntilIdle()

        viewModel.onStepsChange(20)
        viewModel.onStepsChange(30)
        viewModel.onStepsCommit()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setPipelineMaxSteps(30) }
    }

    @Test
    fun `given a background token change then it is persisted with no inheritance to protect`() = runTest(dispatcher) {
        val viewModel = RunLimitsViewModel(repository)
        advanceUntilIdle()

        viewModel.onTokensBackgroundChange(250_000)
        viewModel.onTokensBackgroundCommit()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setRunMaxTokensBackground(250_000) }
    }

    private companion object {
        const val DEFAULT_STEPS: Int = 15
        const val DEFAULT_TOKENS: Int = 1_000_000
        const val DEFAULT_TOKENS_BACKGROUND: Int = 100_000
    }
}
