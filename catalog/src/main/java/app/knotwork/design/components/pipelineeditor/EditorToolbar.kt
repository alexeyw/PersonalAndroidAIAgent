package app.knotwork.design.components.pipelineeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkIconButton
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Bar height (Material `LargeTopAppBar` is overkill — the editor needs a thin bar). */
private val ToolbarHeight = 56.dp

/**
 * Pipeline-editor top toolbar — inline-editable pipeline name on the
 * left, undo / redo / delete / auto-layout cluster in the center, run +
 * overflow on the right.
 *
 * Visual contract: `compose/components/README.md` §EditorToolbar. The
 * toolbar is laid out by hand (not `TopAppBar`) so the canvas owns its
 * status-bar inset handling on the editor screen; this composable just
 * paints the chrome.
 *
 * **Stateless** — every action surfaces as a lambda; the name field is
 * fully controlled via [name] / [onNameChange]. Disabled flags let the
 * caller gate Undo / Redo / Delete without re-rendering the bar.
 *
 * @param name current pipeline name shown in the inline editor.
 * @param onNameChange invoked with each keystroke in the name field.
 * @param onUndo invoked when Undo is pressed.
 * @param onRedo invoked when Redo is pressed.
 * @param onDelete invoked when Delete is pressed.
 * @param onAutoLayout invoked when Auto-layout is pressed.
 * @param onRun invoked when Run is pressed.
 * @param onOverflow invoked when the overflow icon is pressed.
 * @param undoEnabled gates the Undo icon button.
 * @param redoEnabled gates the Redo icon button.
 * @param deleteEnabled gates the Delete icon button.
 * @param runEnabled gates the primary Run button.
 * @param modifier optional layout modifier applied to the bar root.
 */
@Composable
@Suppress("LongParameterList") // Stable toolbar API; collapsing into a config object would only obscure the contract.
fun EditorToolbar(
    name: String,
    onNameChange: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    onAutoLayout: () -> Unit,
    onRun: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
    undoEnabled: Boolean = true,
    redoEnabled: Boolean = true,
    deleteEnabled: Boolean = true,
    runEnabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ToolbarHeight),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = KnotworkTheme.elevation.el1,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KnotworkTheme.spacing.sp3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            PipelineNameField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.weight(1f),
            )
            KnotworkIconButton(
                onClick = onUndo,
                contentDescription = stringResource(R.string.knotwork_editor_action_undo),
                icon = Icons.AutoMirrored.Outlined.Undo,
                enabled = undoEnabled,
            )
            KnotworkIconButton(
                onClick = onRedo,
                contentDescription = stringResource(R.string.knotwork_editor_action_redo),
                icon = Icons.AutoMirrored.Outlined.Redo,
                enabled = redoEnabled,
            )
            KnotworkIconButton(
                onClick = onDelete,
                contentDescription = stringResource(R.string.knotwork_editor_action_delete),
                icon = Icons.Outlined.Delete,
                enabled = deleteEnabled,
            )
            KnotworkIconButton(
                onClick = onAutoLayout,
                contentDescription = stringResource(R.string.knotwork_editor_action_auto_layout),
                icon = AppIcons.AutoLayout,
            )
            KnotworkPrimaryButton(
                text = stringResource(R.string.knotwork_editor_action_run),
                onClick = onRun,
                enabled = runEnabled,
                leadingIcon = Icons.Filled.PlayArrow,
            )
            KnotworkIconButton(
                onClick = onOverflow,
                contentDescription = stringResource(R.string.knotwork_editor_action_overflow),
                icon = Icons.Outlined.MoreVert,
            )
        }
    }
}

/**
 * Inline-editable pipeline name. Rendered as a [BasicTextField] styled to
 * match `TitleLg` so the field reads as flowing text on the bar. Empty
 * input is allowed transiently — final validation happens at save time
 * via [NodeConfigValidation.validateTitle] (Task 8/9 hook the same rule
 * into the pipeline-level title).
 */
@Composable
private fun PipelineNameField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = KnotworkTextStyles.TitleLg.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { /* canvas commits on submit */ }),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .background(color = KnotworkTheme.extended.surface1, shape = KnotworkTheme.shapes.sm)
                        .padding(
                            horizontal = KnotworkTheme.spacing.sp2,
                            vertical = KnotworkTheme.spacing.sp1,
                        ),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.knotwork_editor_name_placeholder),
                            style = KnotworkTextStyles.TitleLg,
                            color = KnotworkTheme.extended.onSurfaceDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** Light-theme preview. */
@Preview(name = "EditorToolbar — light", showBackground = true, widthDp = 720)
@Composable
private fun EditorToolbarLightPreview() {
    KnotworkTheme(darkTheme = false) {
        EditorToolbar(
            name = "Weekly digest",
            onNameChange = {},
            onUndo = {},
            onRedo = {},
            onDelete = {},
            onAutoLayout = {},
            onRun = {},
            onOverflow = {},
        )
    }
}

/** Dark-theme preview. */
@Preview(name = "EditorToolbar — dark", showBackground = true, widthDp = 720)
@Composable
private fun EditorToolbarDarkPreview() {
    KnotworkTheme(darkTheme = true) {
        EditorToolbar(
            name = "Weekly digest",
            onNameChange = {},
            onUndo = {},
            onRedo = {},
            onDelete = {},
            onAutoLayout = {},
            onRun = {},
            onOverflow = {},
            undoEnabled = false,
        )
    }
}
