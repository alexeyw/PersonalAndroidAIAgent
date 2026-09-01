package app.knotwork.android.presentation.ui.orchestrator.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.knotwork.android.R
import app.knotwork.android.domain.models.PresetCategory
import app.knotwork.design.screens.pipelines.PresetCategoryOptionUi
import app.knotwork.design.screens.pipelines.SaveAsPresetDialogUi
import app.knotwork.design.screens.pipelines.SaveAsPresetDialog as CatalogSaveAsPresetDialog

/**
 * Result payload emitted by [SaveAsPresetDialog] when the user submits.
 *
 * Kept in `:app` rather than reusing the catalog's form result, because
 * [category] is a domain type and the catalog cannot name it. The mapping from
 * the catalog's opaque category id back to [PresetCategory] happens here, in
 * the one place that knows both vocabularies.
 *
 * @property name Display name; trimmed by the dialog.
 * @property description Free-form description.
 * @property category Picker category bucket.
 * @property tags Tags as the user typed them, split and trimmed. The
 *   `SavePipelineAsPresetUseCase` normalises further (dedupe / blank-drop) so
 *   the dialog stays dumb.
 */
data class SaveAsPresetResult(
    val name: String,
    val description: String,
    val category: PresetCategory,
    val tags: List<String>,
)

/**
 * `:app` binding of the catalog's save-as-preset dialog: resolves the copy,
 * turns [PresetCategory] into opaque chip options, and maps the chosen id back.
 *
 * The dialog itself lives in `:catalog` so it can be photographed. It had no
 * baseline while it lived here, and that is exactly how "the selected category
 * is not marked" survived to a manual device run.
 *
 * @param initialName Initial value for the name field (typically the source
 *   pipeline's name).
 * @param onDismiss Invoked when the user taps Cancel or the dialog scrim.
 * @param onConfirm Invoked with the captured [SaveAsPresetResult] on Save.
 */
@Composable
fun SaveAsPresetDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (SaveAsPresetResult) -> Unit) {
    val options = PresetCategory.entries.map { entry ->
        PresetCategoryOptionUi(id = entry.name, label = presetCategoryLabelText(entry))
    }
    CatalogSaveAsPresetDialog(
        ui = SaveAsPresetDialogUi(
            initialName = initialName,
            categories = options,
            initialCategoryId = PresetCategory.OTHER.name,
            title = stringResource(R.string.orchestrator_preset_save_title),
            nameLabel = stringResource(R.string.orchestrator_preset_save_name_label),
            descriptionLabel = stringResource(R.string.orchestrator_preset_save_description_label),
            categoryLabel = stringResource(R.string.orchestrator_preset_save_category_label),
            tagsLabel = stringResource(R.string.orchestrator_preset_save_tags_label),
            saveLabel = stringResource(R.string.common_save),
            cancelLabel = stringResource(R.string.common_cancel),
        ),
        onDismiss = onDismiss,
        onConfirm = { form ->
            onConfirm(
                SaveAsPresetResult(
                    name = form.name,
                    description = form.description,
                    // The id is the enum's own name, so an unknown value can only
                    // mean the two lists have drifted apart; OTHER is the bucket
                    // that says "uncategorised" rather than guessing a wrong one.
                    category = PresetCategory.entries.firstOrNull { it.name == form.categoryId }
                        ?: PresetCategory.OTHER,
                    tags = form.tags,
                ),
            )
        },
    )
}
