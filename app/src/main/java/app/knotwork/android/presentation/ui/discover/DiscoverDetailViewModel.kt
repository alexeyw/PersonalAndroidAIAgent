package app.knotwork.android.presentation.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.data.network.AndroidModelDownloadManager.DownloadError
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.GetDiscoverableModelDetailUseCase
import app.knotwork.android.domain.usecases.InstallDiscoveredModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the model-discovery detail screen. Loads a repository's files
 * and drives the per-file install flow through [InstallDiscoveredModelUseCase],
 * gating each download behind a license confirmation. The shared Hugging Face
 * token (used for access-gated repos) is read/written through
 * [SettingsRepository], matching the Models screen.
 *
 * @property getDetail use case fetching the repository detail.
 * @property installModel use case streaming a file download + local registration.
 * @property settingsRepository source/sink of the Hugging Face token.
 */
@HiltViewModel
class DiscoverDetailViewModel @Inject constructor(
    private val getDetail: GetDiscoverableModelDetailUseCase,
    private val installModel: InstallDiscoveredModelUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverDetailUiState())

    /** Current UI state of the detail screen. */
    val uiState: StateFlow<DiscoverDetailUiState> = _uiState.asStateFlow()

    private val _installEvents = MutableSharedFlow<DiscoverInstallEvent>(extraBufferCapacity = 1)

    /** One-shot install outcomes for the screen's snackbar host. */
    val installEvents: SharedFlow<DiscoverInstallEvent> = _installEvents.asSharedFlow()

    /** Per-file in-flight install jobs, keyed by on-disk file name. */
    private val installJobs = mutableMapOf<String, Job>()

    /** In-flight detail-fetch job; cancelled before a new fetch supersedes it. */
    private var loadJob: Job? = null

    private var repoId: String? = null
    private var tokenObserved = false

    /**
     * Binds the screen to [repoId]: starts observing the stored token (once)
     * and loads the repository detail. Idempotent — safe to call from
     * `LaunchedEffect(repoId)` on every composition.
     */
    fun bind(repoId: String) {
        if (this.repoId == repoId) return
        this.repoId = repoId
        observeTokenOnce()
        load()
    }

    private fun observeTokenOnce() {
        if (tokenObserved) return
        tokenObserved = true
        settingsRepository.huggingFaceAuthToken
            .onEach { token -> _uiState.update { it.copy(tokenInput = token.orEmpty()) } }
            .launchIn(viewModelScope)
    }

    /** Re-fetches the repository detail. */
    fun onRetry() = load()

    private fun load() {
        val id = repoId ?: return
        loadJob?.cancel()
        _uiState.update { it.copy(status = DiscoverDetailStatus.Loading) }
        loadJob = viewModelScope.launch {
            getDetail(id).fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(
                            status = DiscoverDetailStatus.Loaded,
                            detail = detail,
                            installed = detail.files.filter { f -> f.isInstalled }.map { f -> f.fileName }.toSet(),
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(status = DiscoverDetailStatus.Error) }
                },
            )
        }
    }

    /** Opens the license-confirmation dialog for [fileName]. */
    fun onInstallClick(fileName: String) {
        _uiState.update { it.copy(pendingLicenseFileName = fileName) }
    }

    /** Dismisses the license-confirmation dialog. */
    fun onLicenseDismiss() {
        _uiState.update { it.copy(pendingLicenseFileName = null) }
    }

    /** User accepted the license — starts the download for [fileName]. */
    fun onLicenseConfirm(fileName: String) {
        _uiState.update { it.copy(pendingLicenseFileName = null) }
        val file = _uiState.value.detail?.files?.firstOrNull { it.fileName == fileName } ?: return
        if (installJobs[fileName]?.isActive == true) return
        installJobs[fileName] = installModel(file)
            .onEach { state -> handleDownloadState(fileName, state) }
            .launchIn(viewModelScope)
    }

    private fun handleDownloadState(fileName: String, state: DownloadState) {
        when (state) {
            is DownloadState.Pending -> setProgress(fileName, 0)
            is DownloadState.Downloading -> setProgress(fileName, state.progress)
            is DownloadState.Success -> {
                clearProgress(fileName)
                installJobs.remove(fileName)
                _uiState.update { it.copy(installed = it.installed + fileName) }
                _installEvents.tryEmit(DiscoverInstallEvent.Success(fileName))
            }
            is DownloadState.Error -> {
                clearProgress(fileName)
                installJobs.remove(fileName)
                _installEvents.tryEmit(DiscoverInstallEvent.Failed(gated = state.isGatedRefusal()))
            }
        }
    }

    /** Cancels an in-flight install for [fileName]. */
    fun onCancelInstall(fileName: String) {
        installJobs.remove(fileName)?.cancel()
        clearProgress(fileName)
    }

    /** Updates and persists the Hugging Face token. */
    fun onTokenChange(token: String) {
        _uiState.update { it.copy(tokenInput = token) }
        viewModelScope.launch {
            settingsRepository.setHuggingFaceAuthToken(token.takeIf { it.isNotBlank() })
        }
    }

    /** Toggles between masked and clear-text token display. */
    fun onToggleTokenReveal() {
        _uiState.update { it.copy(tokenRevealed = !it.tokenRevealed) }
    }

    private fun setProgress(fileName: String, percent: Int) {
        _uiState.update { it.copy(progress = it.progress + (fileName to percent)) }
    }

    private fun clearProgress(fileName: String) {
        _uiState.update { it.copy(progress = it.progress - fileName) }
    }

    /**
     * Treats a 401/403 download refusal as an access-gated failure so the
     * screen can nudge the user toward accepting the licence and supplying a
     * token. Reads the typed HTTP status carried on [DownloadError.code]
     * rather than parsing the free-text message (which also covers arbitrary
     * transport-error strings).
     */
    private fun DownloadState.Error.isGatedRefusal(): Boolean {
        val code = (error as? DownloadError)?.code
        return code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN
    }

    private companion object {
        /** HTTP 401 — missing/invalid credentials for a gated repository. */
        const val HTTP_UNAUTHORIZED = 401

        /** HTTP 403 — licence not accepted for a gated repository. */
        const val HTTP_FORBIDDEN = 403
    }
}
