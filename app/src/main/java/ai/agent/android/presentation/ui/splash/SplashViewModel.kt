package ai.agent.android.presentation.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.agent.android.domain.models.InitProgress
import ai.agent.android.domain.models.InitStage
import ai.agent.android.domain.usecases.AppInitializationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [SplashScreen]. Subscribes to [AppInitializationUseCase] on
 * construction, maps each [InitProgress] into [SplashUiState], and exposes a
 * [retry] entry-point that re-runs the entire initialization pipeline after
 * a fatal failure.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val appInitializationUseCase: AppInitializationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    private var initJob: Job? = null

    init {
        startInitialization()
    }

    /**
     * Starts (or restarts) the initialization sequence. Cancels any in-flight
     * job so [retry] cannot stack overlapping runs if the user taps the
     * button while a previous attempt is still emitting.
     */
    private fun startInitialization() {
        initJob?.cancel()
        _uiState.update { SplashUiState() }
        initJob = viewModelScope.launch {
            appInitializationUseCase().collect { progress ->
                _uiState.update { current -> current.merge(progress) }
            }
        }
    }

    /**
     * Re-runs initialization after a [InitStage.Failed] terminal state. No-op
     * while the splash is mid-flight or already done — the UI hides the
     * retry button in those cases anyway.
     */
    fun retry() {
        if (_uiState.value.errorMessage == null && !_uiState.value.isDone) return
        startInitialization()
    }
}

/**
 * Folds an [InitProgress] emission into a new [SplashUiState], translating
 * `InitStage` to UI-shaped flags. Pulled out as an extension so the
 * `SplashViewModel.startInitialization` body stays focused on lifecycle.
 */
private fun SplashUiState.merge(progress: InitProgress): SplashUiState {
    val fraction = if (progress.totalSteps > 0) {
        progress.completedSteps.toFloat() / progress.totalSteps.toFloat()
    } else {
        0f
    }
    return when (val stage = progress.stage) {
        is InitStage.Done -> SplashUiState(
            message = progress.message,
            progressFraction = 1f,
            isDone = true,
            errorMessage = null,
        )
        is InitStage.Failed -> SplashUiState(
            message = progress.message,
            progressFraction = fraction,
            isDone = false,
            errorMessage = stage.cause,
        )
        else -> SplashUiState(
            message = progress.message,
            progressFraction = fraction,
            isDone = false,
            errorMessage = null,
        )
    }
}
