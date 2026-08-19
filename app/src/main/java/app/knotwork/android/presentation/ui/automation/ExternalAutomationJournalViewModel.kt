package app.knotwork.android.presentation.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.domain.repositories.ExternalAutomationJournalRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel of the external-automation request journal.
 *
 * Read-only by design: the screen shows history and posture, and every control
 * that could change the posture lives one level up on the Background settings
 * category. That keeps the app's most security-sensitive toggle a settings row
 * that search can find and deep-link into, and keeps this surface unable to
 * widen the attack surface it exists to report on.
 *
 * @param settingsRepository Source of the master switch and the surface binding.
 * @param pipelineRepository Resolves the bound pipeline id to its display name.
 * @param journal The external-request journal store.
 */
@HiltViewModel
class ExternalAutomationJournalViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    pipelineRepository: PipelineRepository,
    journal: ExternalAutomationJournalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExternalAutomationJournalUiState())

    /** Live journal + posture snapshot consumed by the screen. */
    val uiState: StateFlow<ExternalAutomationJournalUiState> = _uiState.asStateFlow()

    init {
        settingsRepository.externalAutomationEnabled
            .onEach { enabled -> _uiState.update { it.copy(contractEnabled = enabled) } }
            .launchIn(viewModelScope)

        // The name is resolved rather than stored, so a binding whose pipeline was
        // deleted reads as unbound here exactly as the authorizer already treats
        // it — the screen never claims a pipeline the app could not actually run.
        combine(
            settingsRepository.externalAutomationPipelineId,
            pipelineRepository.observePipelineNames(),
        ) { boundId, names -> boundId?.let(names::get) }
            .onEach { name -> _uiState.update { it.copy(boundPipelineName = name) } }
            .launchIn(viewModelScope)

        journal.observeAll()
            .onEach { entries -> _uiState.update { it.copy(entries = entries) } }
            .launchIn(viewModelScope)
    }
}
