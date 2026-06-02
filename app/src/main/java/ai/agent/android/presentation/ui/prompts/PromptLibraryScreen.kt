package ai.agent.android.presentation.ui.prompts

import ai.agent.android.R
import ai.agent.android.domain.constants.PromptPresetConstants
import ai.agent.android.domain.models.PromptPreset
import ai.agent.android.presentation.ui.common.asString
import ai.agent.android.presentation.ui.components.PromptPreviewBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.design.screens.prompts.PromptEditorSheetBody
import app.knotwork.design.screens.prompts.PromptEditorState
import app.knotwork.design.screens.prompts.PromptEditorStrings
import app.knotwork.design.screens.prompts.PromptLibraryCallbacks
import app.knotwork.design.screens.prompts.PromptLibraryContent
import app.knotwork.design.screens.prompts.PromptLibraryStrings
import app.knotwork.design.screens.prompts.PromptLibraryViewState
import app.knotwork.design.screens.prompts.PromptLibraryVisualState
import app.knotwork.design.screens.prompts.PromptRow

/**
 * Slim app-side Prompt Library mapper. Subscribes to
 * [PromptLibraryViewModel.uiState], folds the projection into the catalog
 * [PromptLibraryViewState], and hosts the editor `ModalBottomSheet`.
 *
 * Phase 24 / Task 5 swaps the data source from legacy `PromptTemplate`
 * to [PromptPreset] (bundled + user). The catalog DTOs stay the same; the
 * mapper folds each preset into a `PromptRow` keyed by the preset's
 * String id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: PromptLibraryViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = promptLibraryStrings()
    val editorStrings = promptEditorStrings()
    val resolvedErrorMessage = uiState.errorMessage?.asString()
    val genericErrorMessage = stringResource(R.string.errors_generic_unexpected)
    val viewState = remember(uiState, strings.subtitleFormat, resolvedErrorMessage) {
        uiState.toViewState(
            subtitleFormat = strings.subtitleFormat,
            resolvedErrorMessage = resolvedErrorMessage,
            fallbackErrorMessage = genericErrorMessage,
        )
    }
    val callbacks = PromptLibraryCallbacks(
        onBack = onBack,
        // The library's text search was retired in Phase 24 / Task 5 (the
        // catalog `TopAppBar` actions slot is intentionally empty); the
        // catalogue is now browsed by category tab via `onCategorySelected`.
        // `onSearch` survives on the catalog callbacks bag as a vestigial
        // parameter that is never invoked, so this no-op is never reached.
        onSearch = {},
        onCategorySelected = viewModel::selectCategory,
        onNewPrompt = { viewModel.openEditor(promptId = null) },
        onEditPrompt = { viewModel.openEditor(promptId = it) },
        onDeletePrompt = viewModel::deletePrompt,
        onDuplicatePrompt = viewModel::duplicatePrompt,
        onPreviewPrompt = { presetId ->
            val preset = (uiState.bundledPresets + uiState.userPresets)
                .firstOrNull { it.id == presetId }
                ?: return@PromptLibraryCallbacks
            viewModel.requestPromptPreview(preset.systemPrompt)
        },
        onEditorNameChange = viewModel::onEditorNameChange,
        onEditorCategoryChange = viewModel::onEditorCategoryChange,
        onEditorBodyChange = viewModel::onEditorBodyChange,
        onEditorVariableInsert = viewModel::onEditorVariableInsert,
        onEditorSave = viewModel::saveEditor,
        onEditorCancel = viewModel::closeEditor,
        onRetry = viewModel::retry,
    )

    PromptLibraryContent(
        state = viewState,
        modifier = modifier,
        strings = strings.content,
        callbacks = callbacks,
    )

    val editor = viewState.editor
    if (editor != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeEditor,
            sheetState = sheetState,
        ) {
            PromptEditorSheetBody(
                state = editor,
                availableVariables = viewState.availableVariables,
                strings = editorStrings,
                callbacks = callbacks,
            )
        }
    }

    // Prompt preview bottom sheet — driven by `previewState`. The sheet body
    // (`PromptPreviewBottomSheet`) renders `null` segments as a centred
    // spinner so the user gets feedback while resolution runs on IO.
    val previewState = uiState.previewState
    if (previewState !is PromptPreviewState.Hidden) {
        PromptPreviewBottomSheet(
            segments = (previewState as? PromptPreviewState.Ready)?.segments,
            onDismiss = viewModel::dismissPromptPreview,
        )
    }
}

/**
 * Maps the app-side [PromptLibraryUiState] onto the catalog
 * [PromptLibraryViewState] consumed by `PromptLibraryContent`.
 *
 * Categories are sourced from [PromptPresetConstants.LLM_DRIVEN_NODE_TYPES]
 * — the set of node types that can host a system-prompt preset — so the
 * tab row is stable across loads (even when one category has zero presets).
 * Rows for the active tab come from `bundledPresets + userPresets`.
 *
 * Branch order: a non-null [errorMessage] always wins over the
 * "empty prompts" branch — otherwise a failed initial load with no
 * cached prompts would hide behind a misleading empty state and the
 * Retry CTA would never be reachable.
 *
 * @param subtitleFormat localised `"%1$d categories · %2$d prompts"` template.
 * @param resolvedErrorMessage pre-resolved `errorMessage.asString()` value
 *   (mappers cannot call `@Composable` resolvers themselves), or `null`
 *   when no error is in flight.
 * @param fallbackErrorMessage generic localised error string used as the
 *   rendered subtitle when [errorMessage] is non-null but resolves to an
 *   empty payload.
 */
internal fun PromptLibraryUiState.toViewState(
    subtitleFormat: String,
    resolvedErrorMessage: String?,
    fallbackErrorMessage: String,
): PromptLibraryViewState {
    val categories = PromptPresetConstants.LLM_DRIVEN_NODE_TYPES
        .map { it.name }
        .sorted()
    val selected = selectedCategory?.takeIf { it in categories }
        ?: categories.firstOrNull().orEmpty()
    val allPresets = bundledPresets + userPresets
    val totalPresets = allPresets.size
    val rows = allPresets
        .filter { it.nodeType.name == selected }
        .map { preset ->
            PromptRow(
                id = preset.id,
                category = preset.nodeType.name,
                name = preset.name,
                body = preset.systemPrompt,
                // No "used by N pipelines" counter for presets — that would
                // require scanning every pipeline graph on every load. Wire
                // the field to 0 so the catalog footer reads as a placeholder
                // until a dedicated counter use case lands.
                usedByCount = 0,
                // Bundled presets are read-only — the catalog hides Edit /
                // Delete affordances under this flag.
                isReadOnly = preset.isBundled,
            )
        }
    val subtitle = subtitleFormat.format(categories.size, totalPresets)
    val editor = editorDraft?.let { draft ->
        PromptEditorState(
            id = draft.id,
            name = draft.name,
            category = draft.category,
            body = draft.body,
            usedByCount = 0,
        )
    }
    val visualState = when {
        // Errors take precedence over both Loading and Empty — without
        // this order, a failed initial load with no cached prompts is
        // rendered as Empty, hiding the real failure and the Retry CTA.
        errorMessage != null -> PromptLibraryVisualState.Error
        isLoading -> PromptLibraryVisualState.Loading
        allPresets.isEmpty() -> PromptLibraryVisualState.Empty
        else -> PromptLibraryVisualState.Default
    }
    val errorText = if (visualState == PromptLibraryVisualState.Error) {
        resolvedErrorMessage?.takeIf { it.isNotBlank() } ?: fallbackErrorMessage
    } else {
        null
    }
    return PromptLibraryViewState(
        visualState = visualState,
        categories = categories,
        selectedCategory = selected,
        prompts = rows,
        availableVariables = availableVariables,
        editor = editor,
        subtitle = subtitle,
        errorMessage = errorText,
    )
}

/** Bundle of localised display strings threaded into [PromptLibraryContent]. */
private data class LocalisedPromptLibraryStrings(val content: PromptLibraryStrings, val subtitleFormat: String)

@Composable
private fun promptLibraryStrings(): LocalisedPromptLibraryStrings = LocalisedPromptLibraryStrings(
    content = PromptLibraryStrings(
        title = stringResource(R.string.prompts_screen_title),
        backCd = stringResource(R.string.prompts_back_cd),
        fabCd = stringResource(R.string.prompts_fab_cd),
        editCd = stringResource(R.string.prompts_edit_cd),
        deleteCd = stringResource(R.string.prompts_delete_cd),
        previewCd = stringResource(R.string.prompts_preview_cd),
        duplicate = stringResource(R.string.prompts_duplicate),
        usedByFormat = stringResource(R.string.prompts_used_by_format),
        emptyTitle = stringResource(R.string.prompts_empty_title),
        emptySubtitle = stringResource(R.string.prompts_empty_subtitle),
        errorTitle = stringResource(R.string.prompts_error_title),
        errorRetry = stringResource(R.string.common_retry),
    ),
    subtitleFormat = stringResource(R.string.prompts_subtitle_format),
)

@Composable
private fun promptEditorStrings(): PromptEditorStrings = PromptEditorStrings(
    titleNew = stringResource(R.string.prompts_editor_title_new),
    titleEdit = stringResource(R.string.prompts_editor_title_edit),
    nameLabel = stringResource(R.string.prompts_editor_name_label),
    namePlaceholder = stringResource(R.string.prompts_editor_name_placeholder),
    categoryLabel = stringResource(R.string.prompts_editor_category_label),
    categoryPlaceholder = stringResource(R.string.prompts_editor_category_placeholder),
    bodyLabel = stringResource(R.string.prompts_editor_body_label),
    bodyPlaceholder = stringResource(R.string.prompts_editor_body_placeholder),
    insertLabel = stringResource(R.string.prompts_editor_insert_label),
    footerFormat = stringResource(R.string.prompts_editor_footer_format),
    cancel = stringResource(R.string.common_cancel),
    save = stringResource(R.string.common_save),
    closeCd = stringResource(R.string.prompts_editor_close_cd),
)
