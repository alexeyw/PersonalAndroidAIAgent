@file:Suppress("MatchingDeclarationName") // File hosts SaveAsPresetDialog + its SaveAsPresetResult payload.

package ai.agent.android.presentation.ui.orchestrator.presets

import ai.agent.android.R
import ai.agent.android.domain.models.PresetCategory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.knotwork.design.components.controls.KnotworkField
import app.knotwork.design.components.controls.KnotworkTextField
import app.knotwork.design.theme.KnotworkTheme

/**
 * Result payload emitted by [SaveAsPresetDialog] when the user submits.
 *
 * @property name Display name; trimmed by the caller.
 * @property description Free-form description.
 * @property category Picker category bucket.
 * @property tags Comma-separated tags as the user typed them. The
 *   [SavePipelineAsPresetUseCase] normalises (trim / dedupe / blank-drop)
 *   so the dialog stays dumb.
 */
data class SaveAsPresetResult(
    val name: String,
    val description: String,
    val category: PresetCategory,
    val tags: List<String>,
)

/**
 * Modal dialog used by both the pipeline-library row overflow ("Save as
 * preset") and the editor overflow. Captures name, description, category
 * and tags before delegating to the matching `OrchestratorViewModel`
 * method.
 *
 * The dialog owns its own internal form state. Submission gates on a
 * non-blank name to mirror [SavePipelineAsPresetUseCase].
 *
 * @param initialName Initial value for the name field (typically the
 *   source pipeline's name).
 * @param onDismiss Invoked when the user taps Cancel or the dialog scrim.
 * @param onConfirm Invoked with the captured [SaveAsPresetResult] when the
 *   user taps Save.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SaveAsPresetDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (SaveAsPresetResult) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf("") }
    var tagsRaw by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PresetCategory.OTHER) }

    val canSubmit = name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(tag = SAVE_AS_PRESET_DIALOG_TEST_TAG),
        title = { Text(stringResource(R.string.orchestrator_preset_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3)) {
                KnotworkField(label = stringResource(R.string.orchestrator_preset_save_name_label)) {
                    KnotworkTextField(value = name, onValueChange = { name = it })
                }
                KnotworkField(label = stringResource(R.string.orchestrator_preset_save_description_label)) {
                    KnotworkTextField(value = description, onValueChange = { description = it })
                }
                KnotworkField(label = stringResource(R.string.orchestrator_preset_save_category_label)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                    ) {
                        PresetCategory.entries.forEach { entry ->
                            FilterChip(
                                selected = category == entry,
                                onClick = { category = entry },
                                label = { Text(presetCategoryLabel(entry)) },
                                colors = FilterChipDefaults.filterChipColors(),
                            )
                        }
                    }
                }
                KnotworkField(label = stringResource(R.string.orchestrator_preset_save_tags_label)) {
                    KnotworkTextField(value = tagsRaw, onValueChange = { tagsRaw = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    val tags = tagsRaw
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onConfirm(
                        SaveAsPresetResult(
                            name = name.trim(),
                            description = description.trim(),
                            category = category,
                            tags = tags,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * Maps a [PresetCategory] to a localised display label. Kept outside the
 * dialog body so manager / picker surfaces can reuse the same wording.
 */
@Composable
internal fun presetCategoryLabel(category: PresetCategory): String = when (category) {
    PresetCategory.LOCAL -> stringResource(R.string.orchestrator_preset_category_local)
    PresetCategory.CLOUD -> stringResource(R.string.orchestrator_preset_category_cloud)
    PresetCategory.HYBRID -> stringResource(R.string.orchestrator_preset_category_hybrid)
    PresetCategory.TOOL -> stringResource(R.string.orchestrator_preset_category_tool)
    PresetCategory.RESEARCH -> stringResource(R.string.orchestrator_preset_category_research)
    PresetCategory.OTHER -> stringResource(R.string.orchestrator_preset_category_other)
}

/** Test-tag applied to the Save-as-preset dialog root for Compose tests. */
internal const val SAVE_AS_PRESET_DIALOG_TEST_TAG = "save_as_preset_dialog"
