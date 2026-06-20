package app.knotwork.android.presentation.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.domain.usecases.SearchDiscoverableModelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the model-discovery list screen. Owns the search text and the
 * fetch lifecycle (initial load, search, pull-to-refresh, retry) against
 * [SearchDiscoverableModelsUseCase]. A network call is issued only in response
 * to a user action (the initial open included).
 *
 * @property searchModels use case listing/searching compatible models.
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(private val searchModels: SearchDiscoverableModelsUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())

    /** Current UI state of the Discover list screen. */
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    /** In-flight fetch job; cancelled before a new fetch supersedes it. */
    private var loadJob: Job? = null

    init {
        load(refresh = false)
    }

    /** Updates the search text without issuing a request. */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** Runs a search against the current [DiscoverUiState.query]. */
    fun onSubmitSearch() {
        load(refresh = false)
    }

    /** Re-fetches the current query keeping the list visible (pull-to-refresh). */
    fun onRefresh() {
        load(refresh = true)
    }

    /** Re-fetches after an error. */
    fun onRetry() {
        load(refresh = false)
    }

    /**
     * Fetches the current query. A non-refresh load swaps in the loading
     * skeletons; a refresh keeps the existing list and shows the refresh
     * indicator instead.
     */
    private fun load(refresh: Boolean) {
        loadJob?.cancel()
        val query = _uiState.value.query
        _uiState.update {
            if (refresh) {
                it.copy(refreshing = true)
            } else {
                it.copy(status = DiscoverStatus.Loading)
            }
        }
        loadJob = viewModelScope.launch {
            val result = searchModels(query = query)
            result.fold(
                onSuccess = { models ->
                    _uiState.update {
                        it.copy(
                            models = models,
                            status = if (models.isEmpty()) DiscoverStatus.Empty else DiscoverStatus.Populated,
                            refreshing = false,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(status = DiscoverStatus.Error, refreshing = false) }
                },
            )
        }
    }
}
