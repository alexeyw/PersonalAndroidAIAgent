package app.knotwork.android.presentation.ui.triggers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.R
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.usecases.SyncTriggersUseCase
import app.knotwork.android.presentation.ui.common.UiText
import app.knotwork.design.screens.triggers.TriggerConditionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Triggers screen. Observes the trigger list and the
 * available pipelines (for the binding picker), drives the full-screen editor
 * and the delete dialog, and — critically — re-syncs the background runtime
 * after every mutation so a trigger created / edited / enabled / deleted while
 * the app is running takes effect immediately rather than on the next cold
 * start (see [SyncTriggersUseCase]).
 */
@HiltViewModel
@Suppress("TooManyFunctions") // List + editor combine many discrete callbacks; collapsing hides intent.
class TriggersViewModel @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val pipelineRepository: PipelineRepository,
    private val syncTriggers: SyncTriggersUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TriggersUiState())
    val uiState: StateFlow<TriggersUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                triggerRepository.observeTriggers(),
                pipelineRepository.getAllPipelines(),
            ) { triggers, pipelines -> triggers to pipelines }
                .catch { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.toUiText()) } }
                .collect { (triggers, pipelines) ->
                    _uiState.update {
                        it.copy(isLoading = false, triggers = triggers, pipelines = pipelines, errorMessage = null)
                    }
                }
        }
    }

    /** Re-runs the load and clears the previous error. */
    fun retry() {
        _uiState.update { it.copy(errorMessage = null) }
        observe()
    }

    /** Opens the overflow menu for the row with [id]. */
    fun openRowMenu(id: String) = _uiState.update { it.copy(openMenuTriggerId = id) }

    /** Dismisses any open overflow menu. */
    fun dismissRowMenu() = _uiState.update { it.copy(openMenuTriggerId = null) }

    /** Toggles the enabled flag of the trigger with [id] and re-syncs the runtime. */
    fun toggleEnabled(id: String) {
        val trigger = _uiState.value.triggers.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            try {
                triggerRepository.setEnabled(id, !trigger.enabled)
                syncTriggers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.toUiText()) }
            }
        }
    }

    // ── Editor ──────────────────────────────────────────────────────────────

    /** Opens the editor for a fresh draft. */
    fun openNewTrigger() {
        _uiState.update { it.copy(openMenuTriggerId = null, editor = TriggerEditorDraft.newDraft()) }
    }

    /** Opens the editor for the trigger with [id]. */
    fun openEditTrigger(id: String) {
        val trigger = _uiState.value.triggers.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(openMenuTriggerId = null, editor = TriggerEditorDraft.fromTrigger(trigger)) }
    }

    /** Whole-row tap opens the editor for that trigger. */
    fun onRowClick(id: String) = openEditTrigger(id)

    /** Closes the editor without persisting. */
    fun closeEditor() = _uiState.update { it.copy(editor = null) }

    /** Updates the editor draft's Name. */
    fun onNameChange(value: String) = updateDraft { it.copy(name = value) }

    /** Switches the editor's condition type. */
    fun onTypeChange(type: TriggerConditionType) = updateDraft { it.copy(type = type) }

    /** Selects an interval preset (leaving Custom mode). */
    fun onIntervalPreset(minutes: Long) =
        updateDraft { it.copy(intervalCustom = false, intervalPresetMinutes = minutes) }

    /** Switches the interval control into Custom mode, seeding a default amount. */
    fun onIntervalCustomToggle() = updateDraft { draft ->
        draft.copy(
            intervalCustom = true,
            intervalCustomAmount = draft.intervalCustomAmount.ifBlank { TriggerEditorDraft.CUSTOM_SEED.toString() },
        )
    }

    /** Updates the Custom interval amount, keeping digits only. */
    fun onIntervalCustomAmountChange(value: String) =
        updateDraft { it.copy(intervalCustomAmount = value.filter(Char::isDigit)) }

    /** Switches the Custom interval unit between minutes and hours. */
    fun onIntervalUnitChange(hours: Boolean) = updateDraft { it.copy(intervalCustomUnitHours = hours) }

    /** Sets the Daily condition's time of day. */
    fun onDailyTimeChange(hour: Int, minute: Int) = updateDraft { it.copy(dailyHour = hour, dailyMinute = minute) }

    /** Toggles the Network condition's Wi-Fi-only flag. */
    fun onWifiOnlyToggle() = updateDraft { it.copy(wifiOnly = !it.wifiOnly) }

    /** Binds (or unbinds, when [pipelineId] is `null`) the editor's pipeline. */
    fun onPipelineSelect(pipelineId: String?) = updateDraft { it.copy(pipelineId = pipelineId) }

    /** Updates the editor draft's input prompt. */
    fun onPromptChange(value: String) = updateDraft { it.copy(prompt = value) }

    /** Toggles the editor draft's enabled flag. */
    fun onEnabledToggle() = updateDraft { it.copy(enabled = !it.enabled) }

    /** Persists the editor draft as a trigger, re-syncs the runtime, and closes the editor. */
    fun saveEditor() {
        val draft = _uiState.value.editor ?: return
        if (!draft.canSave) return
        val trigger = Trigger(
            id = draft.id ?: UUID.randomUUID().toString(),
            name = draft.name.trim(),
            condition = draft.resolveCondition(),
            pipelineId = draft.pipelineId,
            prompt = draft.prompt.trim(),
            enabled = draft.enabled,
            armed = draft.armed,
            createdAt = draft.createdAt,
            lastFiredAt = draft.lastFiredAt,
        )
        viewModelScope.launch {
            try {
                triggerRepository.saveTrigger(trigger)
                syncTriggers()
                _uiState.update { it.copy(editor = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.toUiText()) }
            }
        }
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    /** Opens the delete dialog for the trigger with [id]. */
    fun requestDelete(id: String) {
        val trigger = _uiState.value.triggers.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(openMenuTriggerId = null, deleteTarget = TriggerDeleteTarget(id = id, name = trigger.name))
        }
    }

    /** Confirms the pending delete, removes the trigger, and re-syncs the runtime. */
    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        _uiState.update { it.copy(deleteTarget = null) }
        viewModelScope.launch {
            try {
                triggerRepository.deleteTrigger(target.id)
                syncTriggers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.toUiText()) }
            }
        }
    }

    /** Dismisses the delete dialog without deleting. */
    fun cancelDelete() = _uiState.update { it.copy(deleteTarget = null) }

    private inline fun updateDraft(transform: (TriggerEditorDraft) -> TriggerEditorDraft) {
        _uiState.update { state -> state.copy(editor = state.editor?.let(transform)) }
    }

    private fun Throwable.toUiText(): UiText =
        message?.let(UiText::Dynamic) ?: UiText(R.string.errors_generic_unexpected)
}
