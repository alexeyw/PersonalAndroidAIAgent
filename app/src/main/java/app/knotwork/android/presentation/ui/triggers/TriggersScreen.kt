package app.knotwork.android.presentation.ui.triggers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.android.R
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.presentation.ui.common.asString
import app.knotwork.design.screens.triggers.TriggerConditionType
import app.knotwork.design.screens.triggers.TriggerDeleteDialogContent
import app.knotwork.design.screens.triggers.TriggerDeleteStrings
import app.knotwork.design.screens.triggers.TriggerDeleteUi
import app.knotwork.design.screens.triggers.TriggerEditorCallbacks
import app.knotwork.design.screens.triggers.TriggerEditorContent
import app.knotwork.design.screens.triggers.TriggerEditorStrings
import app.knotwork.design.screens.triggers.TriggerEditorUi
import app.knotwork.design.screens.triggers.TriggerPipelineOptionUi
import app.knotwork.design.screens.triggers.TriggerRowUi
import app.knotwork.design.screens.triggers.TriggersCallbacks
import app.knotwork.design.screens.triggers.TriggersContent
import app.knotwork.design.screens.triggers.TriggersStrings
import app.knotwork.design.screens.triggers.TriggersViewState
import app.knotwork.design.screens.triggers.TriggersVisualState

/**
 * App-side Triggers mapper. Subscribes to [TriggersViewModel.uiState], folds it
 * into the catalog [TriggersViewState], and routes between the list, the
 * full-screen editor (`TriggerEditorContent`) and the delete dialog
 * (`TriggerDeleteDialogContent`).
 *
 * The editor replaces the list while open (the catalog editor is a full-screen
 * surface), so it is rendered instead of the list rather than over it.
 *
 * @param modifier optional layout modifier.
 * @param viewModel the screen ViewModel.
 * @param onBack back-navigation callback.
 */
@Composable
fun TriggersScreen(
    modifier: Modifier = Modifier,
    viewModel: TriggersViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface a one-shot mutation error (save / toggle / delete) as a snackbar
    // over whichever surface is showing, then clear it.
    val transientMessage = uiState.transientError?.asString()
    LaunchedEffect(uiState.transientError) {
        val message = transientMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearTransientError()
    }

    Box(modifier = modifier.fillMaxSize()) {
        val editor = uiState.editor
        if (editor != null) {
            TriggerEditorContent(
                state = uiState.toEditorUi(editor),
                strings = triggerEditorStrings(),
                callbacks = TriggerEditorCallbacks(
                    onClose = viewModel::closeEditor,
                    onSave = viewModel::saveEditor,
                    onNameChange = viewModel::onNameChange,
                    onTypeChange = viewModel::onTypeChange,
                    onIntervalPreset = viewModel::onIntervalPreset,
                    onIntervalCustomToggle = viewModel::onIntervalCustomToggle,
                    onIntervalCustomAmountChange = viewModel::onIntervalCustomAmountChange,
                    onIntervalUnitChange = viewModel::onIntervalUnitChange,
                    onDailyTimeChange = viewModel::onDailyTimeChange,
                    onWifiOnlyToggle = viewModel::onWifiOnlyToggle,
                    onPipelineSelect = viewModel::onPipelineSelect,
                    onPromptChange = viewModel::onPromptChange,
                    onEnabledToggle = viewModel::onEnabledToggle,
                    onDelete = { editor.id?.let(viewModel::requestDelete) },
                ),
            )
        } else {
            TriggersList(uiState = uiState, viewModel = viewModel, onBack = onBack)
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

/** The list surface + its delete dialog, shown when the editor is closed. */
@Composable
private fun TriggersList(uiState: TriggersUiState, viewModel: TriggersViewModel, onBack: () -> Unit) {
    val resolvedError = uiState.loadError?.asString()
    val fallbackError = stringResource(R.string.triggers_error_body)
    TriggersContent(
        state = uiState.toListViewState(
            subtitle = pluralStringResource(
                R.plurals.triggers_subtitle,
                uiState.triggers.size,
                uiState.triggers.size,
                uiState.activeCount,
            ),
            conditionLabels = uiState.conditionLabels(),
            resolvedError = resolvedError,
            fallbackError = fallbackError,
        ),
        strings = triggersStrings(),
        callbacks = TriggersCallbacks(
            onBack = onBack,
            onNewTrigger = viewModel::openNewTrigger,
            onRowClick = viewModel::onRowClick,
            onRowToggle = viewModel::toggleEnabled,
            onRowMenuOpen = viewModel::openRowMenu,
            onRowMenuDismiss = viewModel::dismissRowMenu,
            onEditTrigger = viewModel::openEditTrigger,
            onDeleteTrigger = viewModel::requestDelete,
            onRetry = viewModel::retry,
        ),
    )

    val deleteTarget = uiState.deleteTarget
    if (deleteTarget != null) {
        Dialog(onDismissRequest = viewModel::cancelDelete) {
            TriggerDeleteDialogContent(
                state = TriggerDeleteUi(triggerName = deleteTarget.name),
                strings = triggerDeleteStrings(),
                onConfirm = viewModel::confirmDelete,
                onCancel = viewModel::cancelDelete,
            )
        }
    }
}

/**
 * Pre-resolved human-readable condition lines keyed by trigger id. The pure
 * structured-label computation is memoised on the trigger list so only string
 * resolution (cheap resource lookups) runs on an unrelated recomposition.
 */
@Composable
private fun TriggersUiState.conditionLabels(): Map<String, String> {
    val structured = remember(triggers) {
        triggers.map { it.id to TriggerConditionFormatter.toLabel(it.condition) }
    }
    return structured.associate { (id, label) -> id to conditionText(label) }
}

/** Resolves a structured [TriggerConditionLabel] to its localized display string. */
@Composable
private fun conditionText(label: TriggerConditionLabel): String = when (label) {
    is TriggerConditionLabel.IntervalMinutes ->
        pluralStringResource(R.plurals.triggers_interval_minutes, label.minutes, label.minutes)
    is TriggerConditionLabel.IntervalHours ->
        pluralStringResource(R.plurals.triggers_interval_hours, label.hours, label.hours)
    is TriggerConditionLabel.Daily ->
        stringResource(R.string.triggers_condition_daily, "%02d:%02d".format(label.hour, label.minute))
    TriggerConditionLabel.Charging -> stringResource(R.string.triggers_condition_charging)
    TriggerConditionLabel.NetworkAny -> stringResource(R.string.triggers_condition_network_any)
    TriggerConditionLabel.NetworkWifi -> stringResource(R.string.triggers_condition_network_wifi)
}

/** Maps the app state onto the catalog list view state. */
private fun TriggersUiState.toListViewState(
    subtitle: String,
    conditionLabels: Map<String, String>,
    resolvedError: String?,
    fallbackError: String,
): TriggersViewState {
    val pipelineNames = pipelines.associate { it.id to it.name }
    val rows = triggers.map { trigger ->
        TriggerRowUi(
            id = trigger.id,
            name = trigger.name,
            conditionLabel = conditionLabels[trigger.id].orEmpty(),
            conditionType = trigger.condition.toConditionType(),
            pipelineName = trigger.pipelineId?.let { pipelineNames[it] },
            enabled = trigger.enabled,
        )
    }
    val visualState = when {
        loadError != null && triggers.isEmpty() -> TriggersVisualState.Error
        isLoading -> TriggersVisualState.Loading
        triggers.isEmpty() -> TriggersVisualState.Empty
        else -> TriggersVisualState.Default
    }
    val errorText = if (visualState == TriggersVisualState.Error) {
        resolvedError?.takeIf { it.isNotBlank() } ?: fallbackError
    } else {
        null
    }
    return TriggersViewState(
        visualState = visualState,
        rows = rows,
        openMenuRowId = openMenuTriggerId,
        subtitle = if (visualState == TriggersVisualState.Default) subtitle else "",
        errorMessage = errorText,
    )
}

/**
 * Maps the editor draft onto the catalog editor view state. The pipeline option
 * list is memoised on [TriggersUiState.pipelines] so editing the draft (each
 * keystroke) does not reallocate it.
 */
@Composable
private fun TriggersUiState.toEditorUi(draft: TriggerEditorDraft): TriggerEditorUi {
    val options = remember(pipelines) { pipelines.map { TriggerPipelineOptionUi(id = it.id, name = it.name) } }
    val pipelineName = draft.pipelineId?.let { id -> pipelines.firstOrNull { it.id == id }?.name }
    return TriggerEditorUi(
        id = draft.id,
        name = draft.name,
        type = draft.type,
        intervalCustom = draft.intervalCustom,
        intervalMinutes = draft.intervalPresetMinutes,
        intervalCustomAmount = draft.intervalCustomAmount,
        intervalCustomUnitHours = draft.intervalCustomUnitHours,
        intervalError = draft.intervalError,
        dailyHour = draft.dailyHour,
        dailyMinute = draft.dailyMinute,
        wifiOnly = draft.wifiOnly,
        pipelines = options,
        pipelineName = pipelineName,
        prompt = draft.prompt,
        enabled = draft.enabled,
        // Show the field-level error only when editing an existing trigger whose
        // name the user has cleared — a fresh draft starts blank and shouldn't nag.
        nameError = draft.id != null && draft.name.isBlank(),
        canSave = draft.canSave,
    )
}

/** Maps a domain condition onto the catalog condition-type enum (for the row glyph). */
private fun TriggerCondition.toConditionType(): TriggerConditionType = when (this) {
    is TriggerCondition.IntervalSchedule -> TriggerConditionType.Interval
    is TriggerCondition.DailySchedule -> TriggerConditionType.Daily
    TriggerCondition.Charging -> TriggerConditionType.Charging
    is TriggerCondition.NetworkConnected -> TriggerConditionType.Network
}

@Composable
private fun triggersStrings(): TriggersStrings = TriggersStrings(
    title = stringResource(R.string.triggers_screen_title),
    backCd = stringResource(R.string.triggers_back_cd),
    moreCd = stringResource(R.string.triggers_more_cd),
    fab = stringResource(R.string.triggers_fab),
    unboundHint = stringResource(R.string.triggers_unbound_hint),
    menuEdit = stringResource(R.string.triggers_menu_edit),
    menuDelete = stringResource(R.string.triggers_menu_delete),
    emptyTitle = stringResource(R.string.triggers_empty_title),
    emptyBody = stringResource(R.string.triggers_empty_body),
    emptyCta = stringResource(R.string.triggers_empty_cta),
    emptyStepTrigger = stringResource(R.string.triggers_empty_step_trigger),
    emptyStepRun = stringResource(R.string.triggers_empty_step_run),
    emptyStepResult = stringResource(R.string.triggers_empty_step_result),
    errorTitle = stringResource(R.string.triggers_error_title),
    errorRetry = stringResource(R.string.common_retry),
)

@Composable
private fun triggerEditorStrings(): TriggerEditorStrings = TriggerEditorStrings(
    titleNew = stringResource(R.string.triggers_editor_title_new),
    titleEdit = stringResource(R.string.triggers_editor_title_edit),
    subtitleNew = stringResource(R.string.triggers_editor_subtitle_new),
    subtitleEdit = stringResource(R.string.triggers_editor_subtitle_edit),
    closeCd = stringResource(R.string.triggers_editor_close_cd),
    save = stringResource(R.string.common_save),
    required = stringResource(R.string.triggers_editor_required),
    kicker = stringResource(R.string.triggers_editor_kicker),
    kickerBody = stringResource(R.string.triggers_editor_kicker_body),
    nameLabel = stringResource(R.string.triggers_editor_name_label),
    namePlaceholder = stringResource(R.string.triggers_editor_name_placeholder),
    nameErrorText = stringResource(R.string.triggers_editor_name_error),
    conditionLabel = stringResource(R.string.triggers_editor_condition_label),
    typeInterval = stringResource(R.string.triggers_type_interval),
    typeIntervalSub = stringResource(R.string.triggers_type_interval_sub),
    typeDaily = stringResource(R.string.triggers_type_daily),
    typeDailySub = stringResource(R.string.triggers_type_daily_sub),
    typeCharging = stringResource(R.string.triggers_type_charging),
    typeChargingSub = stringResource(R.string.triggers_type_charging_sub),
    typeNetwork = stringResource(R.string.triggers_type_network),
    typeNetworkSub = stringResource(R.string.triggers_type_network_sub),
    intervalCustom = stringResource(R.string.triggers_interval_custom),
    intervalErrorText = stringResource(R.string.triggers_interval_error),
    intervalFloorNote = stringResource(R.string.triggers_interval_floor_note),
    unitMinutes = stringResource(R.string.triggers_unit_minutes),
    unitHours = stringResource(R.string.triggers_unit_hours),
    dailyFieldLabel = stringResource(R.string.triggers_daily_field_label),
    chargingTitle = stringResource(R.string.triggers_charging_title),
    chargingBody = stringResource(R.string.triggers_charging_body),
    wifiOnlyLabel = stringResource(R.string.triggers_wifi_only_label),
    wifiOnlyDesc = stringResource(R.string.triggers_wifi_only_desc),
    pipelineLabel = stringResource(R.string.triggers_pipeline_label),
    pipelineNone = stringResource(R.string.triggers_pipeline_none),
    pipelineNoneSub = stringResource(R.string.triggers_pipeline_none_sub),
    promptLabel = stringResource(R.string.triggers_prompt_label),
    promptPlaceholder = stringResource(R.string.triggers_prompt_placeholder),
    enabledLabel = stringResource(R.string.triggers_enabled_label),
    enabledDesc = stringResource(R.string.triggers_enabled_desc),
    timeOk = stringResource(R.string.common_ok),
    cancel = stringResource(R.string.common_cancel),
    deleteTrigger = stringResource(R.string.triggers_editor_delete),
)

@Composable
private fun triggerDeleteStrings(): TriggerDeleteStrings = TriggerDeleteStrings(
    title = stringResource(R.string.triggers_delete_title),
    bodyFormat = stringResource(R.string.triggers_delete_body_format),
    cancel = stringResource(R.string.common_cancel),
    delete = stringResource(R.string.triggers_delete_confirm),
)
