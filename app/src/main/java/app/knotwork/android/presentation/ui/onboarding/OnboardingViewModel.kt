package app.knotwork.android.presentation.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.data.network.AndroidModelDownloadManager
import app.knotwork.android.domain.constants.OnboardingModelCatalog
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.ApiKeyRepository
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.ModelDownloadManager
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.LoadModelUseCase
import app.knotwork.android.presentation.state.TransientMessageRelay
import app.knotwork.design.screens.onboarding.OnboardingCloudProvider
import app.knotwork.design.screens.onboarding.OnboardingDefaultPipelinePreview
import app.knotwork.design.screens.onboarding.OnboardingLiteRtModel
import app.knotwork.design.screens.onboarding.OnboardingStep
import app.knotwork.design.screens.onboarding.OnboardingViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the redesigned onboarding flow.
 *
 * Drives the 4-step pager:
 *  - Step 1 / Welcome — pure presentation.
 *  - Step 2 / LiteRT model — the user picks a model id; the screen-level
 *    CTA either starts a download via [startDownload] or advances when
 *    the picked model is already installed. The CTA stays disabled until
 *    one of those conditions becomes true so the user can never skip
 *    past this step without an active model.
 *  - Step 3 / Cloud keys — list of providers; the real key-entry sheet
 *    lives in Settings, so this surface only records which providers the
 *    user opened.
 *  - Step 4 / Ready — recap with the default pipeline preview and the
 *    active-model name. Committing flips `hasCompletedOnboarding` and
 *    lets the host navigate to Chat.
 *
 * Warm-up. As soon as a model becomes installed (either re-detected on
 * entry or after a successful download), [warmUpInstalledModel] kicks
 * off `LoadModelUseCase` so the inference handle is ready by the time
 * the user reaches step 4 — preventing the "LiteRT handle released by
 * system" failure on the first `sendMessage` after onboarding.
 *
 * @property settingsRepository persists the `hasCompletedOnboarding` flag.
 * @property localModelRepository used to detect previously-installed
 * models and to persist freshly-downloaded ones.
 * @property downloadManager streams `DownloadState` updates folded into
 * `OnboardingViewState.downloadProgress` / `downloadError`.
 * @property loadModelUseCase warms the LiteRT inference handle as soon
 * as a model is available so chat works on the first send.
 * @property transientMessageRelay process-wide one-shot snackbar bus.
 * The skip-flow message has to outlive the `OnboardingScreen`'s
 * back-stack entry (which is popped the same frame `onCompleted` fires),
 * so we publish through this singleton; the activity-level host renders
 * the snackbar after navigation settles on Chat.
 * @property apiKeyRepository observed reactively to compute
 * `configuredCloudProviders` from the actual saved credentials — the
 * "Configured" pill on step 3 follows whichever providers have a
 * non-empty key (or, for Ollama, a non-empty base URL) instead of just
 * tracking which rows the user tapped.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val localModelRepository: LocalModelRepository,
    private val downloadManager: ModelDownloadManager,
    private val loadModelUseCase: LoadModelUseCase,
    private val transientMessageRelay: TransientMessageRelay,
    private val apiKeyRepository: ApiKeyRepository,
) : ViewModel() {

    private val _state: MutableStateFlow<OnboardingViewState> = MutableStateFlow(
        OnboardingViewState(defaultPipelinePreview = DEFAULT_PIPELINE_PREVIEW),
    )

    /** Externally-observable view state passed to `OnboardingContent`. */
    val state: StateFlow<OnboardingViewState> = _state.asStateFlow()

    /** Active download collection — cancelled when a new pick re-arms the flow. */
    private var downloadJob: Job? = null

    /** Active warm-up job — cancelled if the user re-installs a different model. */
    private var warmUpJob: Job? = null

    /**
     * Active install-check job — cancelled by the next [pickLiteRtModel]
     * so a slow `findByFileName` from a stale pick can never overwrite
     * a newer selection's state. Without this guard, picking A → B
     * quickly could resume A's check after B is set and incorrectly
     * mark A as installed (bypassing the download gate).
     */
    private var installCheckJob: Job? = null

    /**
     * Resolved absolute path of the currently installed model. Kept
     * outside the catalog view-state because the design-system layer
     * has no business with on-disk paths; the VM passes this into
     * [LoadModelUseCase] in [finishOnboarding].
     */
    private var installedModelPath: String? = null

    /** Initial pick on step 2 — detect re-installs immediately. */
    init {
        checkIfPickedModelAlreadyInstalled(_state.value.liteRtModel)
        observeConfiguredCloudProviders()
    }

    /**
     * Streams the actual saved-key state for every cloud provider and
     * folds it into [OnboardingViewState.configuredCloudProviders]. The
     * "Configured" pill on step 3 is then a *truthful* signal — it
     * reflects what's persisted in `EncryptedSharedPreferences`, not
     * which row the user happened to tap.
     *
     * Per provider:
     *  - OpenAI / Anthropic / Google / DeepSeek — "configured" when the
     *    API key flow is non-blank.
     *  - Ollama — "configured" when the base URL is non-blank (Ollama
     *    does not use an API key in the same sense).
     */
    private fun observeConfiguredCloudProviders() {
        combine(
            apiKeyRepository.getOpenAIKey(),
            apiKeyRepository.getAnthropicKey(),
            apiKeyRepository.getGoogleKey(),
            apiKeyRepository.getDeepSeekKey(),
            apiKeyRepository.getOllamaBaseUrl(),
        ) { openAi, anthropic, google, deepSeek, ollamaBaseUrl ->
            buildSet {
                if (!openAi.isNullOrBlank()) add(OnboardingCloudProvider.OpenAI.id)
                if (!anthropic.isNullOrBlank()) add(OnboardingCloudProvider.Anthropic.id)
                if (!google.isNullOrBlank()) add(OnboardingCloudProvider.Google.id)
                if (!deepSeek.isNullOrBlank()) add(OnboardingCloudProvider.DeepSeek.id)
                if (!ollamaBaseUrl.isNullOrBlank()) add(OnboardingCloudProvider.Ollama.id)
            }
        }
            .onEach { configured -> _state.update { it.copy(configuredCloudProviders = configured) } }
            .launchIn(viewModelScope)
    }

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
     * Records the user's LiteRT model pick on step 2 and synchronously
     * re-checks whether the corresponding file is already installed.
     *
     * Cancels any in-flight install-check / download / warm-up that
     * belonged to the previous pick so a race between a slow database
     * lookup and a rapid A → B → A user switch can never resurrect the
     * previous selection's state on top of the current one.
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
     * `DownloadState` into [OnboardingViewState]. No-op when a download
     * is already in flight (the catalog disables the CTA in that case,
     * but the guard belongs here too).
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
     * Final-step action — persists the `hasCompletedOnboarding` flag.
     *
     * Warm-up is intentionally NOT re-triggered here. The catalog
     * `isPrimaryCtaEnabled` only fires for step `Ready` when
     * `isModelWarmed == true`, and the only path that flips that flag
     * is a successful `scheduleWarmUp` (run on install / download
     * completion). So by the time the user can tap "Open chat", the
     * inference handle is already loaded with the picked model — a
     * second `loadModelUseCase` call here would be a redundant no-op
     * (the engine early-returns when `currentModelPath` matches).
     */
    fun finishOnboarding() {
        viewModelScope.launch {
            settingsRepository.setHasCompletedOnboarding(true)
        }
    }

    /**
     * Skip button (visible on steps 1-3). Persists `hasCompletedOnboarding
     * = true` and publishes a snackbar hint through the
     * [TransientMessageRelay] — the message is rendered by the
     * activity-level host *after* navigation pops onboarding off the
     * back-stack, so the user sees the hint on top of the Chat surface
     * they just landed on.
     */
    fun skipOnboarding() {
        // Publish the hint *before* the flag flip so the relay tryEmit
        // happens regardless of how fast the host pops onboarding.
        transientMessageRelay.post(SKIP_SNACKBAR_MESSAGE)
        viewModelScope.launch {
            settingsRepository.setHasCompletedOnboarding(true)
        }
    }

    /**
     * Legacy helper preserved for binary compatibility with older
     * call sites. The production tap path drives navigation via
     * `OnboardingScreen.onConfigureProvider` and the
     * `configuredCloudProviders` projection is computed reactively
     * from [ApiKeyRepository] (see [observeConfiguredCloudProviders])
     * — so this method intentionally NO LONGER mutates state. We keep
     * the symbol so any stale-build call site that still references it
     * compiles, but the state-flip is gone: the "Configured" pill on
     * step 3 can only flip when a key is actually persisted.
     */
    fun markCloudProviderConfigured(provider: OnboardingCloudProvider) {
        Timber.w(
            "OnboardingViewModel.markCloudProviderConfigured(${provider.id}) called " +
                "— this is a no-op. The configured pill " +
                "is driven by ApiKeyRepository observation, not by tap.",
        )
    }

    /** Legacy alias preserved so existing call sites keep compiling. */
    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setHasCompletedOnboarding(true) }
    }

    private fun checkIfPickedModelAlreadyInstalled(model: OnboardingLiteRtModel) {
        val entry = OnboardingModelCatalog.entryById(model.id) ?: return
        installCheckJob = viewModelScope.launch {
            // Look up the LocalModel row by *filename* (not active-flag).
            // The user's pick is orthogonal to whichever row currently has
            // `isActive = 1`: picking model A while model B is active must
            // still warm A's handle, not B's.
            val installed = localModelRepository.findByFileName(entry.fileName) ?: return@launch
            // Re-verify the pick is still current. `pickLiteRtModel`
            // cancels this job on every change, but cancellation is
            // cooperative — a coroutine that just resumed from a
            // suspending call hasn't yet hit its next cancellation point
            // and could land here with stale `model` data. The
            // `liteRtModel == model` guard short-circuits that case
            // without depending on the cancellation timing.
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

    /**
     * Pulls a user-facing message out of an [AppError] instance. The
     * downloader emits `AndroidModelDownloadManager.DownloadError`,
     * which carries a `message` field; for any other shape (e.g. a
     * future provider) we fall back to a generic copy.
     */
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
        _state.update {
            it.copy(
                downloadProgress = null,
                installedModelId = picked.id,
            )
        }
        viewModelScope.launch {
            val insertedId = localModelRepository.insertModel(
                LocalModel(
                    name = resolved.fileName,
                    path = downloadState.fileUri,
                    size = 0L,
                    isActive = false,
                ),
            )
            // Activate so `getActiveModel()` later picks this row and
            // `LoadModelUseCase` can resolve the path on its own.
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

    /**
     * Resolves the picked model into a (URL, filename) pair. Returns
     * `null` (and surfaces an inline error) when the custom URL row is
     * picked without a usable URL — the catalog CTA stays disabled in
     * that case via the empty-string default, but the guard is
     * defensive.
     */
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
     * Compact tuple bundling the inputs the data-layer download manager
     * needs. Local to the VM — every call site reads both fields.
     */
    private data class ResolvedDownloadTarget(val url: String, val fileName: String)

    companion object {
        /** Skip-flow snackbar copy — referenced by tests and `OnboardingScreen`. */
        const val SKIP_SNACKBAR_MESSAGE: String = "You can install a model from Settings → Models"

        /** Fallback copy when an unknown exception breaks the download stream. */
        private const val GENERIC_DOWNLOAD_ERROR: String = "Download failed."

        /** Fallback copy when `LoadModelUseCase` returns a result without a message. */
        private const val GENERIC_LOAD_ERROR: String = "Failed to load the model."

        /** Surfaced when the user taps the CTA on the Custom URL row with an empty input. */
        private const val CUSTOM_URL_REQUIRED_MESSAGE: String = "Enter a model URL to download."

        /** Divisor turning a 0..100 Int progress into a 0f..1f float for `OnboardingViewState`. */
        private const val PERCENT_DIVISOR: Float = 100f

        /**
         * Hardcoded default-pipeline recap rendered on step 4. The catalog
         * draws the chips from this projection; the actual default
         * pipeline lives in `DefaultPipelineFactory`. Keep the labels in
         * sync — six nodes, seven edges, IF picks up the green accent.
         */
        private val DEFAULT_PIPELINE_PREVIEW = OnboardingDefaultPipelinePreview(
            nodes = listOf("INPUT", "LITE_RT", "IF", "TOOL", "LITE_RT", "OUTPUT"),
            nodeCount = 6,
            edgeCount = 7,
            accentNodeName = "IF",
        )
    }
}
