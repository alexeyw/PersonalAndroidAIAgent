package app.knotwork.android.presentation.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import app.knotwork.design.screens.onboarding.OnboardingDefaultPipelinePreview
import app.knotwork.design.screens.onboarding.OnboardingLiteRtModel
import app.knotwork.design.screens.onboarding.OnboardingScenario
import app.knotwork.design.screens.onboarding.OnboardingStep
import app.knotwork.design.screens.onboarding.OnboardingViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the scenario onboarding flow.
 *
 * Drives the 4-step pager:
 *  - Step 1 / Welcome — pure presentation.
 *  - Step 2 / Choose a scenario — the value gallery. Picking a scenario
 *    ([pickScenario]) pre-selects the model it needs and re-checks whether that
 *    model is already installed. Tapping "Set up {scenario}" ([setUpScenario])
 *    materialises the scenario's pipeline as the default, binds its entry
 *    surface, and advances to the download step. "Start from scratch"
 *    ([startFromScratch]) leaves the seeded default in place and completes.
 *  - Step 3 / Download — the motivated download. The scenario pre-selects the
 *    model; the user may switch to an alternative under "Or choose another".
 *    The CTA either starts a download ([startDownload]) or advances when the
 *    picked model is already installed.
 *  - Step 4 / Ready — recap of what was materialised (scenario pipeline preview,
 *    active model, and — for Share Handler — the bound surface / HITL safety
 *    rows). Committing flips `hasCompletedOnboarding` and lets the host navigate.
 *
 * Warm-up. As soon as a model becomes installed (either re-detected on entry or
 * after a successful download), [scheduleWarmUp] kicks off [LoadModelUseCase] so
 * the inference handle is ready by the time the user reaches step 4 — preventing
 * the "LiteRT handle released by system" failure on the first `sendMessage`.
 *
 * @property settingsRepository persists the `hasCompletedOnboarding` flag.
 * @property localModelRepository detects previously-installed models and persists
 * freshly-downloaded ones.
 * @property downloadManager streams `DownloadState` updates folded into
 * `OnboardingViewState.downloadProgress` / `downloadError`.
 * @property loadModelUseCase warms the LiteRT inference handle.
 * @property setUpScenarioUseCase materialises a picked scenario (default pipeline
 * + entry-surface binding) and returns its recap projection.
 * @property transientMessageRelay process-wide one-shot snackbar bus; the
 * skip/escape hint outlives the popped onboarding back-stack entry.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val localModelRepository: LocalModelRepository,
    private val downloadManager: ModelDownloadManager,
    private val loadModelUseCase: LoadModelUseCase,
    private val setUpScenarioUseCase: SetUpScenarioUseCase,
    private val transientMessageRelay: TransientMessageRelay,
) : ViewModel() {

    private val _state: MutableStateFlow<OnboardingViewState> = MutableStateFlow(OnboardingViewState())

    /** Externally-observable view state passed to `OnboardingContent`. */
    val state: StateFlow<OnboardingViewState> = _state.asStateFlow()

    /** Active download collection — cancelled when a new pick re-arms the flow. */
    private var downloadJob: Job? = null

    /** Active warm-up job — cancelled if the user re-installs a different model. */
    private var warmUpJob: Job? = null

    /**
     * Active install-check job — cancelled by the next [pickLiteRtModel] /
     * [pickScenario] so a slow `findByFileName` from a stale pick can never
     * overwrite a newer selection's state.
     */
    private var installCheckJob: Job? = null

    /**
     * Resolved absolute path of the currently installed model. Kept outside the
     * catalog view-state because the design-system layer has no business with
     * on-disk paths; the VM passes this into [LoadModelUseCase] on warm-up.
     */
    private var installedModelPath: String? = null

    /** Active scenario set-up job — guards against a double-tap materialising twice. */
    private var setUpJob: Job? = null

    /**
     * Id of the scenario whose pipeline has already been materialised in this
     * session. Lets a re-tap of the *same* scenario re-advance to the download
     * step without spawning a second pipeline (and overwriting the default);
     * a different pick re-materialises deliberately.
     */
    private var materializedScenarioId: String? = null

    /** Advances to the next step. Idempotent at the final step. */
    fun next() {
        _state.update { current ->
            val nextStep = OnboardingStep.entries.getOrNull(current.step.pageIndex + 1) ?: current.step
            current.copy(step = nextStep)
        }
    }

    /** Steps back; idempotent on step 1. */
    fun back() {
        _state.update { current ->
            val previousStep = OnboardingStep.entries.getOrNull(current.step.pageIndex - 1) ?: current.step
            current.copy(step = previousStep)
        }
    }

    /**
     * Records the user's scenario pick on the gallery step and pre-selects the
     * model that scenario needs, re-checking whether that model is already
     * installed. Cancels any in-flight download / warm-up / install-check that
     * belonged to a previous pick so a rapid A → B → A switch cannot resurrect
     * the previous selection's state.
     */
    fun pickScenario(scenario: OnboardingScenario) {
        // Never interrupt an in-flight set-up: cancelling it after the preset
        // has already been persisted would orphan that pipeline and leave
        // `materializedScenarioId` unset, so the next "Set up" tap would
        // persist a duplicate. The gallery is non-interactive while busy; this
        // guard is the authoritative one.
        if (setUpJob?.isActive == true) return
        installCheckJob?.cancel()
        downloadJob?.cancel()
        warmUpJob?.cancel()
        _state.update {
            it.copy(
                selectedScenario = scenario,
                liteRtModel = scenario.requiredModel,
                downloadProgress = null,
                downloadError = null,
                installedModelId = null,
                isModelWarmed = false,
            )
        }
        installedModelPath = null
        checkIfPickedModelAlreadyInstalled(scenario.requiredModel)
    }

    /**
     * Materialises the currently-picked scenario (default pipeline + entry
     * surface) and advances to the download step. Stores the recap projection
     * for the "Ready" step. No-op when no scenario is picked; surfaces a failure
     * inline when materialisation fails so the user is not stranded.
     *
     * Guards against materialising more than once for the same intent: a
     * double-tap while a set-up is in flight is ignored, and a re-tap of a
     * scenario already materialised this session just re-advances to the
     * download step instead of persisting a second pipeline and overwriting the
     * default. Picking a *different* scenario ([pickScenario] cancels the job)
     * re-materialises deliberately.
     */
    fun setUpScenario() {
        val scenario = _state.value.selectedScenario ?: return
        if (setUpJob?.isActive == true) return
        if (materializedScenarioId == scenario.id && _state.value.scenarioPreview != null) {
            _state.update { it.copy(step = OnboardingStep.Download) }
            return
        }
        setUpJob = viewModelScope.launch {
            // Surfacing the wait is what stops the user from tapping again:
            // materialising reads the preset asset, parses it and writes to
            // Room, which is not instant on a cold first launch.
            _state.update { it.copy(isSettingUpScenario = true) }
            try {
                setUpScenarioUseCase(scenario.id)
                    .onSuccess { setup ->
                        materializedScenarioId = scenario.id
                        _state.update {
                            it.copy(scenarioPreview = setup.toPreview(), step = OnboardingStep.Download)
                        }
                    }
                    .onFailure { error ->
                        _state.update { it.copy(downloadError = error.message ?: GENERIC_SETUP_ERROR) }
                    }
            } finally {
                // Runs on the cancellation path too, so a cancelled set-up can
                // never strand the gallery in a permanently busy state.
                _state.update { it.copy(isSettingUpScenario = false) }
            }
        }
    }

    /**
     * Retries the model warm-up after a failure surfaced on the "Ready" step.
     * Clears the error and re-runs [LoadModelUseCase] against the same installed
     * model path, so a transient warm failure never dead-ends onboarding behind
     * a disabled "Preparing…" CTA.
     */
    fun retryWarmUp() {
        _state.update { it.copy(downloadError = null) }
        scheduleWarmUp()
    }

    /**
     * Escape hatch behind the "Start from scratch" card. Completes onboarding
     * without materialising a scenario (the first-launch seed remains the
     * default pipeline) and publishes the model hint through the
     * [TransientMessageRelay] so the user knows where to install a model later.
     */
    fun startFromScratch() {
        transientMessageRelay.post(SKIP_SNACKBAR_MESSAGE)
        viewModelScope.launch { settingsRepository.setHasCompletedOnboarding(true) }
    }

    /**
     * Records the user's LiteRT model override on the download step ("Or choose
     * another") and synchronously re-checks whether that file is installed.
     */
    fun pickLiteRtModel(model: OnboardingLiteRtModel) {
        installCheckJob?.cancel()
        downloadJob?.cancel()
        warmUpJob?.cancel()
        _state.update {
            it.copy(
                liteRtModel = model,
                downloadProgress = null,
                downloadError = null,
                installedModelId = null,
                isModelWarmed = false,
            )
        }
        installedModelPath = null
        checkIfPickedModelAlreadyInstalled(model)
    }

    /** Updates the bound custom-URL field; clears any prior download error. */
    fun onCustomDownloadUrlChanged(url: String) {
        _state.update { it.copy(customDownloadUrl = url, downloadError = null) }
    }

    /**
     * Starts a download for the currently-picked model and folds every
     * `DownloadState` into [OnboardingViewState]. No-op when a download is
     * already in flight.
     */
    fun startDownload() {
        if (_state.value.downloadProgress != null) return

        val picked = _state.value.liteRtModel
        val resolved = resolveDownloadTarget(picked) ?: return

        _state.update { it.copy(downloadProgress = 0f, downloadError = null) }

        downloadJob = downloadManager.downloadModel(resolved.url, resolved.fileName)
            .onEach { downloadState -> handleDownloadEmission(downloadState, picked, resolved) }
            .catch { e ->
                _state.update {
                    it.copy(downloadProgress = null, downloadError = e.message ?: GENERIC_DOWNLOAD_ERROR)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Final-step action — persists the `hasCompletedOnboarding` flag. Warm-up is
     * intentionally NOT re-triggered here: the "Ready" CTA only enables once
     * `isModelWarmed == true`, so the handle is already loaded by the time the
     * user can tap it.
     */
    fun finishOnboarding() {
        viewModelScope.launch { settingsRepository.setHasCompletedOnboarding(true) }
    }

    /**
     * Skip button (visible on steps 1-3). Persists `hasCompletedOnboarding` and
     * publishes a snackbar hint through the [TransientMessageRelay].
     */
    fun skipOnboarding() {
        transientMessageRelay.post(SKIP_SNACKBAR_MESSAGE)
        viewModelScope.launch { settingsRepository.setHasCompletedOnboarding(true) }
    }

    private fun checkIfPickedModelAlreadyInstalled(model: OnboardingLiteRtModel) {
        val entry = OnboardingModelCatalog.entryById(model.id) ?: return
        installCheckJob = viewModelScope.launch {
            val installed = localModelRepository.findByFileName(entry.fileName) ?: return@launch
            // Re-verify the pick is still current (cooperative cancellation).
            if (_state.value.liteRtModel != model) return@launch
            installedModelPath = installed.path
            _state.update { it.copy(installedModelId = model.id) }
            scheduleWarmUp()
        }
    }

    private fun handleDownloadEmission(
        downloadState: DownloadState,
        picked: OnboardingLiteRtModel,
        resolved: ResolvedDownloadTarget,
    ) {
        when (downloadState) {
            is DownloadState.Pending -> _state.update { it.copy(downloadProgress = 0f) }
            is DownloadState.Downloading -> _state.update {
                it.copy(downloadProgress = downloadState.progress / PERCENT_DIVISOR)
            }
            is DownloadState.Success -> onDownloadSucceeded(downloadState, picked, resolved)
            is DownloadState.Error -> _state.update {
                it.copy(
                    downloadProgress = null,
                    downloadError = extractErrorMessage(downloadState.error),
                )
            }
        }
    }

    private fun extractErrorMessage(error: AppError): String {
        val message = (error as? AndroidModelDownloadManager.DownloadError)?.message
        return message?.takeIf { it.isNotBlank() } ?: GENERIC_DOWNLOAD_ERROR
    }

    private fun onDownloadSucceeded(
        downloadState: DownloadState.Success,
        picked: OnboardingLiteRtModel,
        resolved: ResolvedDownloadTarget,
    ) {
        installedModelPath = downloadState.fileUri
        _state.update { it.copy(downloadProgress = null, installedModelId = picked.id) }
        viewModelScope.launch {
            val insertedId = localModelRepository.insertModel(
                LocalModel(
                    name = resolved.fileName,
                    path = downloadState.fileUri,
                    size = 0L,
                    isActive = false,
                ),
            )
            localModelRepository.setActiveModel(insertedId)
            scheduleWarmUp()
        }
    }

    private fun scheduleWarmUp() {
        warmUpJob?.cancel()
        val path = installedModelPath ?: return
        warmUpJob = viewModelScope.launch {
            when (val result = loadModelUseCase(path)) {
                is Result.Success -> _state.update { it.copy(isModelWarmed = true) }
                is Result.Error -> _state.update {
                    it.copy(downloadError = result.message ?: GENERIC_LOAD_ERROR)
                }
            }
        }
    }

    private fun resolveDownloadTarget(model: OnboardingLiteRtModel): ResolvedDownloadTarget? {
        val preset = OnboardingModelCatalog.entryById(model.id)
        if (preset != null) {
            return ResolvedDownloadTarget(url = preset.downloadUrl, fileName = preset.fileName)
        }
        val customUrl = _state.value.customDownloadUrl.trim()
        if (customUrl.isEmpty()) {
            _state.update { it.copy(downloadError = CUSTOM_URL_REQUIRED_MESSAGE) }
            return null
        }
        return ResolvedDownloadTarget(
            url = customUrl,
            fileName = OnboardingModelCatalog.fileNameForCustomUrl(customUrl),
        )
    }

    /**
     * Projects a [SetUpScenarioUseCase.ScenarioSetup] into the catalog preview
     * shape, giving a routing node ([NodeType.INTENT_ROUTER] / `IF`) the green
     * accent so the recap reads like the editor.
     */
    private fun SetUpScenarioUseCase.ScenarioSetup.toPreview(): OnboardingDefaultPipelinePreview {
        val accent = nodeTypeNames.firstOrNull { it == "INTENT_ROUTER" }
            ?: nodeTypeNames.firstOrNull { it == "IF" }
        return OnboardingDefaultPipelinePreview(
            nodes = nodeTypeNames,
            nodeCount = nodeCount,
            edgeCount = edgeCount,
            accentNodeName = accent,
        )
    }

    /** Compact tuple bundling the inputs the data-layer download manager needs. */
    private data class ResolvedDownloadTarget(val url: String, val fileName: String)

    companion object {
        /** Skip / escape snackbar copy — referenced by tests and `OnboardingScreen`. */
        const val SKIP_SNACKBAR_MESSAGE: String = "You can install a model from Settings → Models"

        /** Fallback copy when an unknown exception breaks the download stream. */
        private const val GENERIC_DOWNLOAD_ERROR: String = "Download failed."

        /** Fallback copy when `LoadModelUseCase` returns a result without a message. */
        private const val GENERIC_LOAD_ERROR: String = "Failed to load the model."

        /** Fallback copy when scenario set-up fails without a message. */
        private const val GENERIC_SETUP_ERROR: String = "Couldn't set up this scenario."

        /** Surfaced when the user taps the CTA on the Custom URL row with an empty input. */
        private const val CUSTOM_URL_REQUIRED_MESSAGE: String = "Enter a model URL to download."

        /** Divisor turning a 0..100 Int progress into a 0f..1f float for `OnboardingViewState`. */
        private const val PERCENT_DIVISOR: Float = 100f
    }
}
