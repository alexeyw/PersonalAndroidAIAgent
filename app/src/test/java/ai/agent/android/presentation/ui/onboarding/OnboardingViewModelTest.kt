package ai.agent.android.presentation.ui.onboarding

import ai.agent.android.data.network.AndroidModelDownloadManager
import ai.agent.android.domain.constants.OnboardingModelCatalog
import ai.agent.android.domain.models.DownloadState
import ai.agent.android.domain.models.LocalModel
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.ModelDownloadManager
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.LoadModelUseCase
import app.knotwork.design.screens.onboarding.OnboardingLiteRtModel
import app.knotwork.design.screens.onboarding.OnboardingStep
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the orchestrated download / load flow added in Phase 22 /
 * Task 12 on top of the existing `hasCompletedOnboarding` persistence.
 *
 * The wired flow has three independent invariants that all need to hold
 * for onboarding to leave the user with a working chat:
 *  1. Step-2 CTA stays disabled until the picked model is installed or
 *     a download is in flight — preventing skip-past-without-model;
 *  2. Downloaded models are persisted in `LocalModelRepository` and the
 *     resulting path is fed to `LoadModelUseCase` to warm the LiteRT
 *     handle;
 *  3. Errors surface in the catalog `downloadError` field instead of
 *     blowing up the coroutine.
 *
 * The test class is intentionally narrow: it covers the VM contract,
 * not the catalog rendering (the latter is exercised by Roborazzi
 * snapshots in Task 13).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var localModelRepository: LocalModelRepository
    private lateinit var downloadManager: ModelDownloadManager
    private lateinit var loadModelUseCase: LoadModelUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        coEvery { settingsRepository.setHasCompletedOnboarding(any()) } returns Unit
        localModelRepository = mockk(relaxed = true)
        coEvery { localModelRepository.isInstalled(any()) } returns false
        coEvery { localModelRepository.getActiveModel() } returns null
        coEvery { localModelRepository.insertModel(any()) } returns 1L
        coEvery { localModelRepository.setActiveModel(any()) } returns Unit
        downloadManager = mockk(relaxed = true)
        every { downloadManager.downloadModel(any(), any(), any()) } returns flowOf()
        loadModelUseCase = mockk(relaxed = true)
        coEvery { loadModelUseCase.invoke(any()) } returns Result.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): OnboardingViewModel = OnboardingViewModel(
        settingsRepository = settingsRepository,
        localModelRepository = localModelRepository,
        downloadManager = downloadManager,
        loadModelUseCase = loadModelUseCase,
    )

    @Test
    fun `given onboarding completion when invoked then sets hasCompletedOnboarding to true`() = runTest {
        val viewModel = newViewModel()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
        // The VM must not also touch `isFirstLaunch` — that flag is owned
        // by `InitializeAppUseCase` and resetting it would re-trigger
        // one-shot seeding (default prompts, the seeded pipeline).
        coVerify(exactly = 0) { settingsRepository.setFirstLaunch(any()) }
    }

    @Test
    fun `startDownload propagates progress from DownloadManager`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        every { downloadManager.downloadModel(any(), any(), any()) } returns flowOf(
            DownloadState.Pending,
            DownloadState.Downloading(progress = 50),
            DownloadState.Success(fileUri = "/tmp/gemma-4-E2B-it.litertlm"),
        )

        viewModel.startDownload()
        advanceUntilIdle()

        val finalState = viewModel.state.value
        assertNull(finalState.downloadProgress)
        assertEquals(OnboardingLiteRtModel.Gemma4E2B.id, finalState.installedModelId)
        coVerify(exactly = 1) { localModelRepository.insertModel(any()) }
        coVerify(exactly = 1) { loadModelUseCase.invoke("/tmp/gemma-4-E2B-it.litertlm") }
    }

    @Test
    fun `finishOnboarding loads installed model into inference engine`() = runTest {
        coEvery { localModelRepository.isInstalled(any()) } returns true
        coEvery { localModelRepository.getActiveModel() } returns LocalModel(
            id = 7L,
            name = "gemma-4-E2B-it.litertlm",
            path = "/data/model.litertlm",
            size = 0L,
            isActive = true,
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.finishOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
        coVerify(atLeast = 1) { loadModelUseCase.invoke("/data/model.litertlm") }
    }

    @Test
    fun `cannot advance from step 2 without installed model`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.next() // 1 → 2
        advanceUntilIdle()
        val onStep2 = viewModel.state.value
        assertEquals(OnboardingStep.LiteRtModel, onStep2.step)
        assertNull(onStep2.installedModelId)
        assertNull(onStep2.downloadProgress)
        assertFalse(onStep2.isPrimaryCtaEnabled)
    }

    @Test
    fun `pickLiteRtModel sets installedId when matching file already on disk`() = runTest {
        val e4bFileName = OnboardingModelCatalog.entryById(OnboardingLiteRtModel.Gemma4E4B.id)!!.fileName
        coEvery { localModelRepository.isInstalled(e4bFileName) } returns true
        coEvery { localModelRepository.getActiveModel() } returns LocalModel(
            id = 9L,
            name = e4bFileName,
            path = "/data/e4b.litertlm",
            size = 0L,
            isActive = true,
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.pickLiteRtModel(OnboardingLiteRtModel.Gemma4E4B)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(OnboardingLiteRtModel.Gemma4E4B.id, state.installedModelId)
        // No download started since the model is already on disk.
        coVerify(exactly = 0) { downloadManager.downloadModel(any(), any(), any()) }
    }

    @Test
    fun `startDownload propagates error to downloadError`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        every { downloadManager.downloadModel(any(), any(), any()) } returns flowOf(
            DownloadState.Pending,
            DownloadState.Error(AndroidModelDownloadManager.DownloadError(message = "Server returned 500")),
        )

        viewModel.startDownload()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.downloadProgress)
        assertEquals("Server returned 500", state.downloadError)
        assertNull(state.installedModelId)
    }

    @Test
    fun `skipOnboarding emits snackbar hint`() = runTest {
        val viewModel = newViewModel()
        val received = mutableListOf<String>()
        val collector = CoroutineScope(testDispatcher).launch {
            viewModel.skipSnackbarEvents.toList(received)
        }
        advanceUntilIdle()

        viewModel.skipOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
        assertTrue(
            "Expected SKIP_SNACKBAR_MESSAGE in snackbar events, got $received",
            received.contains(OnboardingViewModel.SKIP_SNACKBAR_MESSAGE),
        )
        collector.cancel()
    }
}
