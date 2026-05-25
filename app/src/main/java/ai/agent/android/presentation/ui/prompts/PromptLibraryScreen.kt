package ai.agent.android.presentation.ui.prompts

import ai.agent.android.R
import ai.agent.android.domain.models.NodeType
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
    val viewState = remember(uiState, strings.subtitleFormat, strings.usedByFormat) {
        uiState.toViewState(
            subtitleFormat = strings.subtitleFormat,
            usedByFormat = strings.usedByFormat,
        )
    }
    val callbacks = PromptLibraryCallbacks(
        onBack = onBack,
        onSearch = {},
        onCategorySelected = viewModel::selectCategory,
        onNewPrompt = { viewModel.openEditor(promptId = null) },
        onEditPrompt = { viewModel.openEditor(promptId = it) },
        onDeletePrompt = viewModel::deletePrompt,
        onDuplicatePrompt = viewModel::duplicatePrompt,
        onEditorNameChange = viewModel::onEditorNameChange,
        onEditorCategoryChange = viewModel::onEditorCategoryChange,
        onEditorBodyChange = viewModel::onEditorBodyChange,
        onEditorVariableInsert = viewModel::onEditorVariableInsert,
        onEditorSave = viewModel::saveEditor,
        onEditorCancel = viewModel::closeEditor,
        onRetry = {},
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
}

/**
 * Maps the app-side [PromptLibraryUiState] onto the catalog
 * [PromptLibraryViewState] consumed by `PromptLibraryContent`.
 *
 * @param subtitleFormat localised `"%1$d categories · %2$d prompts"` template.
 * @param usedByFormat localised `"used by %1$d pipelines"` template
 * (currently unused — the catalog applies its own formatting from
 * `usedByCount`).
 */
@Suppress("UNUSED_PARAMETER") // usedByFormat reserved for future per-row count formatting.
internal fun PromptLibraryUiState.toViewState(subtitleFormat: String, usedByFormat: String): PromptLibraryViewState {
    val categories = if (promptTemplates.isEmpty()) {
        NodeType.entries.map { it.name }
    } else {
        promptTemplates.map { it.category }.distinct().sorted()
    }
    val selected = selectedCategory ?: categories.firstOrNull().orEmpty()
    val rows = promptTemplates
        .filter { it.category == selected }
        .map { template ->
            PromptRow(
                id = template.id,
                category = template.category,
                name = template.name,
                body = template.text,
                usedByCount = 0,
            )
        }
    val subtitle = subtitleFormat.format(categories.size, promptTemplates.size)
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
        isLoading -> PromptLibraryVisualState.Loading
        promptTemplates.isEmpty() -> PromptLibraryVisualState.Empty
        errorMessage != null -> PromptLibraryVisualState.Error
        else -> PromptLibraryVisualState.Default
    }
    val errorText = if (visualState == PromptLibraryVisualState.Error) "" else null
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
private data class LocalisedPromptLibraryStrings(
    val content: PromptLibraryStrings,
    val subtitleFormat: String,
    val usedByFormat: String,
)

@Composable
private fun promptLibraryStrings(): LocalisedPromptLibraryStrings {
    val usedByFormat = stringResource(R.string.prompts_used_by_format)
    return LocalisedPromptLibraryStrings(
        content = PromptLibraryStrings(
            title = stringResource(R.string.prompts_screen_title),
            backCd = stringResource(R.string.prompts_back_cd),
            searchCd = stringResource(R.string.prompts_search_cd),
            fabCd = stringResource(R.string.prompts_fab_cd),
            editCd = stringResource(R.string.prompts_edit_cd),
            deleteCd = stringResource(R.string.prompts_delete_cd),
            duplicate = stringResource(R.string.prompts_duplicate),
            usedByFormat = usedByFormat,
            emptyTitle = stringResource(R.string.prompts_empty_title),
            emptySubtitle = stringResource(R.string.prompts_empty_subtitle),
            errorTitle = stringResource(R.string.prompts_error_title),
            errorRetry = stringResource(R.string.common_retry),
        ),
        subtitleFormat = stringResource(R.string.prompts_subtitle_format),
        usedByFormat = usedByFormat,
    )
}

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
