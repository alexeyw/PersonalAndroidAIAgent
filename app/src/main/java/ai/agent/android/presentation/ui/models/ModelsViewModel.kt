package ai.agent.android.presentation.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.domain.models.DownloadState
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * ViewModel responsible for managing the state and business logic of the Models UI.
 * It interacts with the repository to fetch downloaded models and the download manager
 * to handle new model downloads.
 *
 * @property localModelRepository The repository for accessing locally stored models.
 * @property downloadManager The manager for downloading new models from the network.
 */
@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    init {
        observeDownloadedModels()
    }

    private fun observeDownloadedModels() {
        localModelRepository.getAllModels()
            .onEach { models ->
                val active = models.find { it.isActive }
                _uiState.update { state ->
                    state.copy(
                        downloadedModels = models,
                        activeModel = active
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Updates the custom URL input text in the UI state.
     *
     * @param url The new URL string entered by the user.
     */
    fun onCustomUrlChanged(url: String) {
        _uiState.update { it.copy(customUrlInput = url, downloadError = null) }
    }

    /**
     * Updates the authorization token input text in the UI state.
     *
     * @param token The new token string entered by the user.
     */
    fun onAuthTokenChanged(token: String) {
        _uiState.update { it.copy(authTokenInput = token) }
    }

    /**
     * Initiates a download for the given URL and desired file name.
     *
     * @param url The direct URL to download the model from.
     * @param fileName The name to save the file as on the local device.
     */
    fun startDownload(url: String, fileName: String) {
        if (_uiState.value.isDownloading) return

        val authToken = _uiState.value.authTokenInput.takeIf { it.isNotBlank() }

        _uiState.update {
            it.copy(
                isDownloading = true,
                downloadProgress = 0,
                downloadError = null
            )
        }

        downloadManager.downloadModel(url, fileName, authToken)
            .onEach { state ->
                when (state) {
                    is DownloadState.Pending -> {
                        _uiState.update { it.copy(downloadProgress = 0) }
                    }
                    is DownloadState.Downloading -> {
                        _uiState.update { it.copy(downloadProgress = state.progress) }
                    }
                    is DownloadState.Success -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadProgress = null
                            )
                        }
                    }
                    is DownloadState.Error -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadProgress = null,
                                downloadError = state.error
                            )
                        }
                    }
                }
            }
            .catch { e ->
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        downloadError = ai.agent.android.data.network.AndroidModelDownloadManager.DownloadError(e.message ?: "Unknown error occurred")
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Sets the specified model as the active one for the application.
     *
     * @param modelId The unique identifier of the model to activate.
     */
    fun setActiveModel(modelId: Long) {
        viewModelScope.launch {
            localModelRepository.setActiveModel(modelId)
        }
    }
}
