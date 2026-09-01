@file:Suppress("MatchingDeclarationName") // File hosts SavePromptAsPresetDialog + its SavePromptAsPresetResult payload.

package app.knotwork.android.presentation.ui.orchestrator.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.knotwork.android.R
import app.knotwork.android.domain.constants.PromptPresetConstants
import app.knotwork.android.domain.models.NodeType
import app.knotwork.design.screens.prompts.SavePromptAsPresetDialogUi
import app.knotwork.design.screens.prompts.SavePromptAsPresetDialog as CatalogSavePromptAsPresetDialog

/**
 * Result payload emitted by [SavePromptAsPresetDialog] when the user submits.
 *
 * Tag normalisation (trim / dedupe / blank-drop) is performed by
 * `SavePromptAsPresetUseCase`, so the dialog stays dumb.
 *
 * @property name Display name; trimmed by the dialog before emission.
 * @property description Free-form description; trimmed.
 * @property tags Tags as the user typed them, split and trimmed.
 */
data class SavePromptAsPresetResult(val name: String, val description: String, val tags: List<String>)

/**
 * `:app` binding of the catalog's save-prompt-as-preset dialog: resolves the
 * copy and supplies the name-length limit the domain owns.
 *
 * The dialog itself lives in `:catalog` so its three states — the ordinary
 * form, the blank-prompt refusal and the over-length name — can be
 * photographed. None of them could be while it lived here.
 *
 * @param nodeType Target [NodeType], rendered as the dialog subtitle.
 * @param systemPromptPreview The current text of the field, shown read-only so
 *   the user can confirm what they are saving.
 * @param onConfirm Invoked with the captured [SavePromptAsPresetResult] on Save.
 * @param onDismiss Invoked when the user taps Cancel or the scrim.
 */
@Composable
fun SavePromptAsPresetDialog(
    nodeType: NodeType,
    systemPromptPreview: String,
    onConfirm: (SavePromptAsPresetResult) -> Unit,
    onDismiss: () -> Unit,
) {
    CatalogSavePromptAsPresetDialog(
        ui = SavePromptAsPresetDialogUi(
            title = stringResource(R.string.prompt_preset_save_dialog_title),
            subtitle = stringResource(R.string.prompt_preset_save_dialog_subtitle, nodeType.name),
            promptPreview = systemPromptPreview,
            nameLabel = stringResource(R.string.prompt_preset_save_field_name),
            descriptionLabel = stringResource(R.string.prompt_preset_save_field_description),
            tagsLabel = stringResource(R.string.prompt_preset_save_field_tags),
            tagsHint = stringResource(R.string.prompt_preset_save_field_tags_hint),
            blankPromptError = stringResource(R.string.prompt_preset_save_error_blank_prompt),
            nameTooLongError = stringResource(
                R.string.prompt_preset_save_error_name_too_long,
                PromptPresetConstants.MAX_NAME_LENGTH,
            ),
            saveLabel = stringResource(R.string.prompt_preset_save_action_save),
            cancelLabel = stringResource(R.string.prompt_preset_save_action_cancel),
            maxNameLength = PromptPresetConstants.MAX_NAME_LENGTH,
        ),
        onConfirm = { form ->
            onConfirm(
                SavePromptAsPresetResult(
                    name = form.name,
                    description = form.description,
                    tags = form.tags,
                ),
            )
        },
        onDismiss = onDismiss,
    )
}
