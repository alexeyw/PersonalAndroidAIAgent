@file:Suppress("MatchingDeclarationName") // File hosts dialog + its SavePromptAsPresetResult payload.

package ai.agent.android.presentation.ui.orchestrator.components

import ai.agent.android.R
import ai.agent.android.domain.constants.PromptPresetConstants
import ai.agent.android.domain.models.NodeType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import app.knotwork.design.components.controls.KnotworkField
import app.knotwork.design.components.controls.KnotworkTextField
import app.knotwork.design.theme.KnotworkTheme

/**
 * Result payload emitted by [SavePromptAsPresetDialog] when the user submits.
 *
 * Tag normalisation (trim / dedupe / blank-drop) is performed by
 * `SavePromptAsPresetUseCase`, so the dialog stays dumb.
 *
 * @property name Display name; trimmed by the dialog before emission.
 * @property description Free-form description; trimmed.
 * @property tags Comma-separated tags as the user typed them.
 */
data class SavePromptAsPresetResult(val name: String, val description: String, val tags: List<String>)

/**
 * Modal dialog used by the pipeline editor's `NodeConfigSheet` 💾 button to
 * capture the metadata needed to persist the currently-edited system prompt
 * as a user prompt preset.
 *
 * The dialog owns its own internal form state. Submission gates on a non-blank
 * name within [PromptPresetConstants.MAX_NAME_LENGTH] characters and is
 * additionally disabled (with an inline message) when [systemPromptPreview]
 * is blank — saving an empty prompt would fail the use-case validator
 * anyway. The target node type is implicit from the field the user clicked
 * 💾 on, so there is no category picker here.
 *
 * @param nodeType target [NodeType] (rendered as the dialog subtitle).
 * @param systemPromptPreview the current text of the field; rendered in a
 *   compact read-only label so the user can confirm what they're saving.
 * @param onConfirm invoked with the captured [SavePromptAsPresetResult] when
 *   the user taps Save.
 * @param onDismiss invoked when the user taps Cancel or the scrim.
 */
@Composable
@Suppress("LongMethod")
fun SavePromptAsPresetDialog(
    nodeType: NodeType,
    systemPromptPreview: String,
    onConfirm: (SavePromptAsPresetResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tagsRaw by remember { mutableStateOf("") }

    val trimmedName = name.trim()
    val nameTooLong = trimmedName.length > PromptPresetConstants.MAX_NAME_LENGTH
    val promptBlank = systemPromptPreview.isBlank()
    val canSubmit = canSavePromptPreset(name, systemPromptPreview)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(tag = SAVE_PROMPT_AS_PRESET_DIALOG_TEST_TAG),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.prompt_preset_save_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(
                        R.string.prompt_preset_save_dialog_subtitle,
                        nodeType.name,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            ) {
                if (promptBlank) {
                    Text(
                        text = stringResource(R.string.prompt_preset_save_error_blank_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    // Compact read-only preview so the user can verify which
                    // draft they're persisting — the dialog opens from a
                    // single 💾 button so the visual association is otherwise
                    // implicit.
                    Text(
                        text = systemPromptPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = PREVIEW_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                KnotworkField(label = stringResource(R.string.prompt_preset_save_field_name)) {
                    KnotworkTextField(value = name, onValueChange = { name = it })
                }
                if (nameTooLong) {
                    Text(
                        text = stringResource(
                            R.string.prompt_preset_save_error_name_too_long,
                            PromptPresetConstants.MAX_NAME_LENGTH,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                KnotworkField(label = stringResource(R.string.prompt_preset_save_field_description)) {
                    KnotworkTextField(value = description, onValueChange = { description = it })
                }
                KnotworkField(label = stringResource(R.string.prompt_preset_save_field_tags)) {
                    KnotworkTextField(value = tagsRaw, onValueChange = { tagsRaw = it })
                }
                Text(
                    text = stringResource(R.string.prompt_preset_save_field_tags_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    onConfirm(
                        SavePromptAsPresetResult(
                            name = trimmedName,
                            description = description.trim(),
                            tags = parsePromptPresetTags(tagsRaw),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.prompt_preset_save_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.prompt_preset_save_action_cancel))
            }
        },
    )
}

/**
 * Splits a comma-separated tag string into a clean list — trims each token and
 * drops blanks. Final de-dup is left to `SavePromptAsPresetUseCase` so the
 * dialog stays dumb. Pure function so the parser can be unit-tested without
 * spinning up Compose.
 */
internal fun parsePromptPresetTags(raw: String): List<String> =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Returns `true` when the dialog's Save button should be enabled — name is
 * non-blank, within [PromptPresetConstants.MAX_NAME_LENGTH] characters, and
 * the source prompt is non-blank. Mirrors the live-state gate inside the
 * Composable so the rule can be unit-tested.
 */
internal fun canSavePromptPreset(name: String, systemPrompt: String): Boolean {
    val trimmedName = name.trim()
    return trimmedName.isNotEmpty() &&
        trimmedName.length <= PromptPresetConstants.MAX_NAME_LENGTH &&
        systemPrompt.isNotBlank()
}

/** Test-tag applied to the Save-as-prompt-preset dialog root for Compose tests. */
internal const val SAVE_PROMPT_AS_PRESET_DIALOG_TEST_TAG = "save_prompt_as_preset_dialog"

/** Cap for the read-only systemPrompt preview rendered above the form. */
private const val PREVIEW_MAX_LINES: Int = 4
