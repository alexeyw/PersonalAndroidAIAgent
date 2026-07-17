package app.knotwork.android.presentation.ui.onboarding

import app.knotwork.android.data.network.AndroidModelDownloadManager
import app.knotwork.android.domain.constants.OnboardingModelCatalog
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.ModelDownloadManager
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.LoadModelUseCase
import app.knotwork.android.domain.usecases.SetUpScenarioUseCase
import app.knotwork.android.presentation.state.TransientMessageRelay
import app.knotwork.design.screens.onboarding.OnboardingLiteRtModel
import app.knotwork.design.screens.onboarding.OnboardingScenario
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
 * Verifies the orchestrated scenario / download / warm flow layered on top of
 * the `hasCompletedOnboarding` persistence.
 *
 * Invariants covered:
 *  1. The gallery CTA stays disabled until a scenario is picked;
 *  2. Picking a scenario pre-selects its model and warms an already-installed one;
 *  3. `setUpScenario` materialises the scenario and advances to the download step;
 *  4. The download CTA gate matches the download state;
 *  5. Downloaded models are persisted and their path fed to `LoadModelUseCase`;
 *  6. Errors surface in `downloadError` instead of crashing the coroutine.
 *
 * The class covers the VM contract, not the catalog rendering (Roborazzi does that).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var localModelRepository: LocalModelRepository
    private lateinit var downloadManager: ModelDownloadManager
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var setUpScenarioUseCase: SetUpScenarioUseCase
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
        setUpScenarioUseCase = mockk(relaxed = true)
        coEvery { setUpScenarioUseCase(any()) } returns kotlin.Result.success(
            SetUpScenarioUseCase.ScenarioSetup(
                scenarioId = OnboardingScenario.StyledTranslation.id,
                pipelineId = "pipe-1",
                nodeTypeNames = listOf("INPUT", "LITE_RT", "OUTPUT"),
                nodeCount = 3,
                edgeCount = 2,
            ),
        )
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
        setUpScenarioUseCase = setUpScenarioUseCase,
        transientMessageRelay = transientMessageRelay,
    )

    @Test
    fun `finishOnboarding sets hasCompletedOnboarding without touching firstLaunch`() = runTest {
        val viewModel = newViewModel()

        viewModel.finishOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
        // The VM must not touch `isFirstLaunch` — that flag is owned by
        // `InitializeAppUseCase` and resetting it would re-trigger seeding.
        coVerify(exactly = 0) { settingsRepository.setFirstLaunch(any()) }
    }

    @Test
    fun `gallery CTA is disabled until a scenario is picked`() = runTest {
        val viewModel = newViewModel()
        viewModel.next() // Welcome → ChooseScenario
        advanceUntilIdle()

        assertEquals(OnboardingStep.ChooseScenario, viewModel.state.value.step)
        assertFalse(viewModel.state.value.isPrimaryCtaEnabled)

        viewModel.pickScenario(OnboardingScenario.StyledTranslation)
        advanceUntilIdle()

        assertEquals(OnboardingScenario.StyledTranslation, viewModel.state.value.selectedScenario)
        assertTrue(viewModel.state.value.isPrimaryCtaEnabled)
    }

    @Test
    fun `pickScenario pre-selects the scenario model and warms an installed one`() = runTest {
        val e4bFileName = OnboardingModelCatalog.entryById(OnboardingLiteRtModel.Gemma4E4B.id)!!.fileName
        coEvery { localModelRepository.findByFileName(e4bFileName) } returns LocalModel(
            id = 3L,
            name = e4bFileName,
            path = "/data/e4b.litertlm",
            size = 0L,
            isActive = false,
        )
        val viewModel = newViewModel()

        // Virtual Companion needs Gemma 4 E4B.
        viewModel.pickScenario(OnboardingScenario.VirtualCompanion)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(OnboardingLiteRtModel.Gemma4E4B, state.liteRtModel)
        assertEquals(OnboardingLiteRtModel.Gemma4E4B.id, state.installedModelId)
        coVerify(atLeast = 1) { loadModelUseCase.invoke("/data/e4b.litertlm") }
    }

    @Test
    fun `setUpScenario materialises the scenario and advances to download`() = runTest {
        val viewModel = newViewModel()
        viewModel.next() // → ChooseScenario
        viewModel.pickScenario(OnboardingScenario.StyledTranslation)
        advanceUntilIdle()

        viewModel.setUpScenario()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(OnboardingStep.Download, state.step)
        assertEquals(listOf("INPUT", "LITE_RT", "OUTPUT"), state.scenarioPreview?.nodes)
        coVerify(exactly = 1) { setUpScenarioUseCase(OnboardingScenario.StyledTranslation.id) }
    }

    @Test
    fun `setUpScenario marks the gallery busy and ignores picks until it settles`() = runTest {
        // Hold the materialisation open so the in-flight window is observable.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { setUpScenarioUseCase(any()) } coAnswers {
            gate.await()
            kotlin.Result.success(
                SetUpScenarioUseCase.ScenarioSetup(
                    scenarioId = OnboardingScenario.StyledTranslation.id,
                    pipelineId = "pipe-1",
                    nodeTypeNames = listOf("INPUT", "LITE_RT", "OUTPUT"),
                    nodeCount = 3,
                    edgeCount = 2,
                ),
            )
        }
        val viewModel = newViewModel()
        viewModel.next() // → ChooseScenario
        viewModel.pickScenario(OnboardingScenario.StyledTranslation)
        advanceUntilIdle()

        viewModel.setUpScenario()
        advanceUntilIdle()

        // Busy: the CTA is disabled and a card tap must not cancel the set-up
        // (cancelling after the preset was persisted would orphan a pipeline
        // and let the next tap create a duplicate).
        assertTrue(viewModel.state.value.isSettingUpScenario)
        assertFalse(viewModel.state.value.isPrimaryCtaEnabled)
        viewModel.pickScenario(OnboardingScenario.VirtualCompanion)
        viewModel.setUpScenario()
        advanceUntilIdle()
        assertEquals(OnboardingScenario.StyledTranslation, viewModel.state.value.selectedScenario)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSettingUpScenario)
        assertEquals(OnboardingStep.Download, viewModel.state.value.step)
        // Exactly one pipeline materialised despite the extra taps.
        coVerify(exactly = 1) { setUpScenarioUseCase(any()) }
    }

    @Test
    fun `setUpScenario re-tap of the same scenario does not materialise twice`() = runTest {
        val viewModel = newViewModel()
        viewModel.next() // → ChooseScenario
        viewModel.pickScenario(OnboardingScenario.StyledTranslation)
        advanceUntilIdle()
        viewModel.setUpScenario()
        advanceUntilIdle()
        // Back to the gallery, then set up the same scenario again.
        viewModel.back()
        viewModel.setUpScenario()
        advanceUntilIdle()

        assertEquals(OnboardingStep.Download, viewModel.state.value.step)
        coVerify(exactly = 1) { setUpScenarioUseCase(OnboardingScenario.StyledTranslation.id) }
    }

    @Test
    fun `retryWarmUp clears the error and re-warms after a failed warm-up`() = runTest {
        val e4bFileName = OnboardingModelCatalog.entryById(OnboardingLiteRtModel.Gemma4E4B.id)!!.fileName
        coEvery { localModelRepository.findByFileName(e4bFileName) } returns LocalModel(
            id = 7L,
            name = e4bFileName,
            path = "/data/model.litertlm",
            size = 0L,
            isActive = true,
        )
        // First warm-up fails; the retry succeeds.
        coEvery { loadModelUseCase.invoke("/data/model.litertlm") } returnsMany listOf(
            Result.Error(error = object : AppError.System {}, message = "warm failed"),
            Result.Success(Unit),
        )
        val viewModel = newViewModel()
        viewModel.pickScenario(OnboardingScenario.StyledTranslation)
        advanceUntilIdle()
        assertEquals("warm failed", viewModel.state.value.downloadError)
        assertFalse(viewModel.state.value.isModelWarmed)

        viewModel.retryWarmUp()
        advanceUntilIdle()

        assertNull(viewModel.state.value.downloadError)
        assertTrue(viewModel.state.value.isModelWarmed)
    }

    @Test
    fun `setUpScenario surfaces a materialisation failure inline`() = runTest {
        coEvery { setUpScenarioUseCase(any()) } returns kotlin.Result.failure(IllegalStateException("boom"))
        val viewModel = newViewModel()
        viewModel.next() // → ChooseScenario
        viewModel.pickScenario(OnboardingScenario.StyledTranslation)
        advanceUntilIdle()

        viewModel.setUpScenario()
        advanceUntilIdle()

        val state = viewModel.state.value
        // Stays on the gallery step and shows the error rather than stranding the user.
        assertEquals(OnboardingStep.ChooseScenario, state.step)
        assertEquals("boom", state.downloadError)
    }

    @Test
    fun `startDownload propagates progress from DownloadManager`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        every { downloadManager.downloadModel(any(), any(), any()) } returns flowOf(
            DownloadState.Pending,
            DownloadState.Downloading(progress = 50),
            DownloadState.Success(fileUri = "/tmp/gemma-4-E4B-it.litertlm"),
        )

        viewModel.startDownload()
        advanceUntilIdle()

        val finalState = viewModel.state.value
        assertNull(finalState.downloadProgress)
        // The flow defaults to E4B — the model every curated scenario targets.
        assertEquals(OnboardingLiteRtModel.Gemma4E4B.id, finalState.installedModelId)
        coVerify(exactly = 1) { localModelRepository.insertModel(any()) }
        coVerify(exactly = 1) { loadModelUseCase.invoke("/tmp/gemma-4-E4B-it.litertlm") }
    }

    @Test
    fun `finishOnboarding persists hasCompletedOnboarding without re-warming`() = runTest {
        val e4bFileName = OnboardingModelCatalog.entryById(OnboardingLiteRtModel.Gemma4E4B.id)!!.fileName
        coEvery { localModelRepository.findByFileName(e4bFileName) } returns LocalModel(
            id = 7L,
            name = e4bFileName,
            path = "/data/model.litertlm",
            size = 0L,
            isActive = true,
        )
        val viewModel = newViewModel()
        // Styled Translation needs E4B; picking it warms the installed handle once.
        viewModel.pickScenario(OnboardingScenario.StyledTranslation)
        advanceUntilIdle()
        coVerify(exactly = 1) { loadModelUseCase.invoke("/data/model.litertlm") }

        viewModel.finishOnboarding()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
        // No second warm-up: the Ready CTA only enables after the first one flips
        // `isModelWarmed`, so `finishOnboarding` never re-loads the model.
        coVerify(exactly = 1) { loadModelUseCase.invoke("/data/model.litertlm") }
    }

    @Test
    fun `download CTA is disabled while a download is in flight`() = runTest {
        every { downloadManager.downloadModel(any(), any(), any()) } returns kotlinx.coroutines.flow.flow {
            emit(DownloadState.Pending)
            kotlinx.coroutines.awaitCancellation()
        }
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.next() // → ChooseScenario
        viewModel.next() // → Download
        viewModel.startDownload()
        advanceUntilIdle()

        val onDownload = viewModel.state.value
        assertEquals(OnboardingStep.Download, onDownload.step)
        assertNull(onDownload.installedModelId)
        assertEquals(0f, onDownload.downloadProgress)
        assertFalse(onDownload.isPrimaryCtaEnabled)
    }

    @Test
    fun `pickLiteRtModel sets installedId when matching file already on disk`() = runTest {
        val e4bFileName = OnboardingModelCatalog.entryById(OnboardingLiteRtModel.Gemma4E4B.id)!!.fileName
        coEvery { localModelRepository.isInstalled(e4bFileName) } returns true
        coEvery { localModelRepository.findByFileName(e4bFileName) } returns LocalModel(
            id = 9L,
            name = e4bFileName,
            path = "/data/e4b.litertlm",
            size = 0L,
            isActive = false,
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.pickLiteRtModel(OnboardingLiteRtModel.Gemma4E4B)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(OnboardingLiteRtModel.Gemma4E4B.id, state.installedModelId)
        coVerify(exactly = 0) { downloadManager.downloadModel(any(), any(), any()) }
        coVerify(atLeast = 1) { loadModelUseCase.invoke("/data/e4b.litertlm") }
    }

    @Test
    fun `pickLiteRtModel warms picked path even when a different model is active`() = runTest {
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

        verify(exactly = 1) { transientMessageRelay.post(OnboardingViewModel.SKIP_SNACKBAR_MESSAGE) }
        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
    }

    @Test
    fun `startFromScratch posts hint and completes onboarding`() = runTest {
        val viewModel = newViewModel()

        viewModel.startFromScratch()
        advanceUntilIdle()

        verify(exactly = 1) { transientMessageRelay.post(OnboardingViewModel.SKIP_SNACKBAR_MESSAGE) }
        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
    }

    @Test
    fun `rapid pick switch does not resurrect stale install-check result`() = runTest {
        val e2bFileName = OnboardingModelCatalog.entryById(OnboardingLiteRtModel.Gemma4E2B.id)!!.fileName
        val e4bFileName = OnboardingModelCatalog.entryById(OnboardingLiteRtModel.Gemma4E4B.id)!!.fileName
        val e2bSuspender = kotlinx.coroutines.CompletableDeferred<LocalModel?>()
        coEvery { localModelRepository.findByFileName(e2bFileName) } coAnswers { e2bSuspender.await() }
        coEvery { localModelRepository.findByFileName(e4bFileName) } returns null
        val viewModel = newViewModel()

        // Pick E2B (slow lookup suspends), then immediately pick E4B (cancels it).
        viewModel.pickLiteRtModel(OnboardingLiteRtModel.Gemma4E2B)
        viewModel.pickLiteRtModel(OnboardingLiteRtModel.Gemma4E4B)
        advanceUntilIdle()
        // Now resolve the slow E2B lookup, simulating a stale resume.
        e2bSuspender.complete(
            LocalModel(id = 5L, name = e2bFileName, path = "/data/e2b.litertlm", size = 0L, isActive = false),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(OnboardingLiteRtModel.Gemma4E4B, state.liteRtModel)
        assertNull(state.installedModelId)
        coVerify(exactly = 0) { loadModelUseCase.invoke("/data/e2b.litertlm") }
    }

    @Test
    fun `download CTA is enabled for a fresh-install preset pick`() = runTest {
        val viewModel = newViewModel()
        viewModel.next() // → ChooseScenario
        viewModel.next() // → Download
        advanceUntilIdle()

        // Fresh install: no install yet, no download in flight, picked row is a
        // preset → CTA must be enabled so the user can start the download.
        assertEquals(OnboardingStep.Download, viewModel.state.value.step)
        assertTrue(viewModel.state.value.isPrimaryCtaEnabled)
    }
}
