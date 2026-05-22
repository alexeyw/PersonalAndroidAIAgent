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
import ai.agent.android.presentation.state.TransientMessageRelay
import app.knotwork.design.screens.onboarding.OnboardingLiteRtModel
import app.knotwork.design.screens.onboarding.OnboardingStep
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
    private lateinit var transientMessageRelay: TransientMessageRelay

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)
        coEvery { settingsRepository.setHasCompletedOnboarding(any()) } returns Unit
        localModelRepository = mockk(relaxed = true)
        coEvery { localModelRepository.isInstalled(any()) } returns false
        coEvery { localModelRepository.findByFileName(any()) } returns null
        coEvery { localModelRepository.getActiveModel() } returns null
        coEvery { localModelRepository.insertModel(any()) } returns 1L
        coEvery { localModelRepository.setActiveModel(any()) } returns Unit
        downloadManager = mockk(relaxed = true)
        every { downloadManager.downloadModel(any(), any(), any()) } returns flowOf()
        loadModelUseCase = mockk(relaxed = true)
        coEvery { loadModelUseCase.invoke(any()) } returns Result.Success(Unit)
        transientMessageRelay = mockk(relaxed = true)
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
        transientMessageRelay = transientMessageRelay,
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
        val installedModel = LocalModel(
            id = 7L,
            name = "gemma-4-E2B-it.litertlm",
            path = "/data/model.litertlm",
            size = 0L,
            isActive = true,
        )
        coEvery { localModelRepository.isInstalled(any()) } returns true
        coEvery { localModelRepository.findByFileName(any()) } returns installedModel
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.finishOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
        coVerify(atLeast = 1) { loadModelUseCase.invoke("/data/model.litertlm") }
    }

    @Test
    fun `step 2 CTA is disabled while a download is in flight`() = runTest {
        // Suspend-forever flow simulates a long-running download: the
        // VM marks `downloadProgress = 0f` and never gets a Success,
        // so the CTA renders "Downloading…" and must stay disabled
        // (re-tapping would be a no-op anyway, but the visual state
        // matters).
        every { downloadManager.downloadModel(any(), any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(DownloadState.Pending)
            // never completes
            kotlinx.coroutines.awaitCancellation()
        }
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.next() // 1 → 2
        viewModel.startDownload()
        advanceUntilIdle()

        val onStep2 = viewModel.state.value
        assertEquals(OnboardingStep.LiteRtModel, onStep2.step)
        assertNull(onStep2.installedModelId)
        assertEquals(0f, onStep2.downloadProgress)
        assertFalse(onStep2.isPrimaryCtaEnabled)
    }

    @Test
    fun `pickLiteRtModel sets installedId when matching file already on disk`() = runTest {
        val e4bFileName = OnboardingModelCatalog.entryById(OnboardingLiteRtModel.Gemma4E4B.id)!!.fileName
        val e4bModel = LocalModel(
            id = 9L,
            name = e4bFileName,
            path = "/data/e4b.litertlm",
            size = 0L,
            isActive = false,
        )
        coEvery { localModelRepository.isInstalled(e4bFileName) } returns true
        coEvery { localModelRepository.findByFileName(e4bFileName) } returns e4bModel
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.pickLiteRtModel(OnboardingLiteRtModel.Gemma4E4B)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(OnboardingLiteRtModel.Gemma4E4B.id, state.installedModelId)
        // No download started since the model is already on disk.
        coVerify(exactly = 0) { downloadManager.downloadModel(any(), any(), any()) }
        // Warm-up resolved the *picked* model's path (not the active-row
        // fallback) — guards against the regression flagged in PR review.
        coVerify(atLeast = 1) { loadModelUseCase.invoke("/data/e4b.litertlm") }
    }

    @Test
    fun `pickLiteRtModel warms picked path even when a different model is active`() = runTest {
        // Active row points at E2B; user picks E4B; warm-up must run on
        // E4B's path, not the active E2B path.
        val e4bFileName = OnboardingModelCatalog.entryById(OnboardingLiteRtModel.Gemma4E4B.id)!!.fileName
        coEvery { localModelRepository.isInstalled(e4bFileName) } returns true
        coEvery { localModelRepository.findByFileName(e4bFileName) } returns LocalModel(
            id = 11L,
            name = e4bFileName,
            path = "/data/e4b.litertlm",
            size = 0L,
            isActive = false,
        )
        coEvery { localModelRepository.getActiveModel() } returns LocalModel(
            id = 1L,
            name = "gemma-4-E2B-it.litertlm",
            path = "/data/e2b.litertlm",
            size = 0L,
            isActive = true,
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.pickLiteRtModel(OnboardingLiteRtModel.Gemma4E4B)
        advanceUntilIdle()

        coVerify(atLeast = 1) { loadModelUseCase.invoke("/data/e4b.litertlm") }
        coVerify(exactly = 0) { loadModelUseCase.invoke("/data/e2b.litertlm") }
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
    fun `skipOnboarding posts hint through TransientMessageRelay`() = runTest {
        val viewModel = newViewModel()

        viewModel.skipOnboarding()
        advanceUntilIdle()

        // Posting through the relay (not a per-VM SharedFlow) is what
        // lets the snackbar survive the back-stack pop that
        // `onCompleted()` triggers right after this call.
        verify(exactly = 1) { transientMessageRelay.post(OnboardingViewModel.SKIP_SNACKBAR_MESSAGE) }
        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
    }

    @Test
    fun `step 2 CTA is enabled for a fresh-install preset pick`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.next() // 1 → 2
        advanceUntilIdle()

        // Fresh install: no install yet, no download in flight, picked
        // row is a preset → CTA *must* be enabled so the user can start
        // the download. Regression guard for the PR-review feedback
        // about the dead-locked onboarding flow.
        assertTrue(viewModel.state.value.isPrimaryCtaEnabled)
    }
}
