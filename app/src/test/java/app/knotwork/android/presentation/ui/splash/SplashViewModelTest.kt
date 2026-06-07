package app.knotwork.android.presentation.ui.splash

import app.knotwork.android.domain.models.InitProgress
import app.knotwork.android.domain.models.InitStage
import app.knotwork.android.domain.usecases.AppInitializationUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies that [SplashViewModel] folds [InitProgress] emissions into the
 * UI state correctly across the success path, the failed path, and the
 * retry path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appInitializationUseCase: AppInitializationUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appInitializationUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given full successful run when collected then ends in done state`() = runTest {
        every { appInitializationUseCase() } returns flowOf(
            InitProgress(InitStage.Initializing, "Preparing…", 0, 5),
            InitProgress(InitStage.LoadingModel, "Loading model…", 1, 5),
            InitProgress(InitStage.LoadingPipelines, "Reading pipelines…", 2, 5),
            InitProgress(InitStage.LoadingChats, "Reading chats…", 3, 5),
            InitProgress(InitStage.LoadingMemory, "Reading memory…", 4, 5),
            InitProgress(InitStage.Done, "Ready", 5, 5),
        )

        val viewModel = SplashViewModel(appInitializationUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isDone)
        assertEquals(1f, state.progressFraction, 0.001f)
        assertNull(state.errorMessage)
        assertEquals("Ready", state.message)
    }

    @Test
    fun `given mid-flight emission when collected then progressFraction reflects completed steps`() = runTest {
        every { appInitializationUseCase() } returns flowOf(
            InitProgress(InitStage.Initializing, "Preparing…", 0, 5),
            InitProgress(InitStage.LoadingPipelines, "Reading pipelines…", 2, 5),
        )

        val viewModel = SplashViewModel(appInitializationUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isDone)
        assertEquals(2f / 5f, state.progressFraction, 0.001f)
        assertEquals("Reading pipelines…", state.message)
        assertNull(state.errorMessage)
    }

    @Test
    fun `given Failed emission when collected then surfaces error message`() = runTest {
        every { appInitializationUseCase() } returns flowOf(
            InitProgress(InitStage.Initializing, "Preparing…", 0, 5),
            InitProgress(
                stage = InitStage.Failed("seed prompts failed", InitStage.Initializing),
                message = "seed prompts failed",
                completedSteps = 0,
                totalSteps = 5,
            ),
        )

        val viewModel = SplashViewModel(appInitializationUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isDone)
        assertNotNull(state.errorMessage)
        assertEquals("seed prompts failed", state.errorMessage)
    }

    @Test
    fun `given retry called after failure when invoked then re-runs the use case`() = runTest {
        // First run fails; second run succeeds.
        var invocationCount = 0
        every { appInitializationUseCase() } answers {
            invocationCount++
            if (invocationCount == 1) {
                flowOf(
                    InitProgress(
                        stage = InitStage.Failed("boom", InitStage.LoadingPipelines),
                        message = "boom",
                        completedSteps = 0,
                        totalSteps = 5,
                    ),
                )
            } else {
                flowOf(
                    InitProgress(InitStage.Done, "Ready", 5, 5),
                )
            }
        }

        val viewModel = SplashViewModel(appInitializationUseCase)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isDone)
        assertNull(state.errorMessage)
        assertEquals(2, invocationCount)
    }

    @Test
    fun `given retry called while idle when invoked then is no-op`() = runTest {
        // A flow that never terminates so the VM stays in flight.
        every { appInitializationUseCase() } returns MutableSharedFlow<InitProgress>()

        val viewModel = SplashViewModel(appInitializationUseCase)
        advanceUntilIdle()

        // Retry while the run is in flight (errorMessage == null && !isDone)
        // must not start a second collection.
        viewModel.retry()
        advanceUntilIdle()

        // Use case still invoked exactly once (the implicit init call).
        io.mockk.verify(exactly = 1) { appInitializationUseCase() }
    }
}
