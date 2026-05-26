package app.knotwork.design.components.pipelineeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import app.knotwork.design.components.buttons.KnotworkButtonSize
import app.knotwork.design.components.buttons.KnotworkIconButton
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Single-line toolbar height (no subtitle). Mirrors Material `TopAppBar` minimum,
 * trimmed so the canvas owns its own bar — `LargeTopAppBar` would steal the run
 * banner's vertical space.
 */
private val ToolbarHeightSingle = 56.dp

/**
 * Two-line toolbar height (title + subtitle). Tuned so the subtitle (`BodySm`) sits
 * flush against the title baseline without crowding the navigation icon.
 */
private val ToolbarHeightWithSubtitle = 64.dp

/**
 * Pipeline-editor top toolbar — `[← back] [Title + Subtitle stack] [Primary action] [Overflow]`.
 *
 * Visual contract: `compose/components/README.md` §EditorToolbar.
 *
 * The previous Phase-21 layout exposed `Undo / Redo / Delete / Auto-layout` as
 * permanent icon buttons; the Phase-22 designer mockups demoted them to the
 * overflow menu so the toolbar stays uncluttered across every state (Editing /
 * Validating / Running / Done / Overview). The caller owns the overflow
 * `DropdownMenu` — this composable just invokes [onOverflow] when the icon is
 * tapped.
 *
 * **Stateless** — every action surfaces as a lambda. The name field is fully
 * controlled via [name] / [onNameChange]; the subtitle is whatever string the
 * caller computes from `runState` / `validationErrors` / `nodes.size` / etc.
 *
 * @param name current pipeline name shown in the inline editor.
 * @param onNameChange invoked with each keystroke in the name field.
 * @param onNavigateUp invoked when the leading back icon is tapped.
 * @param onPrimaryAction invoked when the primary [primaryAction] button is tapped.
 *   No-op when [primaryAction] is [EditorPrimaryAction.None] (the button is hidden).
 * @param onOverflow invoked when the trailing overflow icon is tapped.
 * @param subtitle optional secondary line below the pipeline name — drives the
 *   bar's vertical sizing (56 dp without, 64 dp with). Typically
 *   `"Editing · N nodes · M edges"` / `"Running · 6 / 11 · 4.2 s"` / etc.
 * @param primaryAction selects which (if any) primary CTA renders on the right.
 *   Hidden during a live run (banner owns Pause / Stop).
 * @param primaryActionEnabled gates the primary action (e.g. `Run` greys out
 *   while validation errors are present).
 * @param modifier optional layout modifier applied to the bar root.
 */
@Composable
@Suppress("LongParameterList") // Stable toolbar API; collapsing into a config object would only obscure the contract.
fun EditorToolbar(
    name: String,
    onNameChange: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    primaryAction: EditorPrimaryAction = EditorPrimaryAction.Run,
    primaryActionEnabled: Boolean = true,
) {
    val barHeight = if (subtitle == null) ToolbarHeightSingle else ToolbarHeightWithSubtitle
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = KnotworkTheme.elevation.el1,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KnotworkTheme.spacing.sp2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            KnotworkIconButton(
                onClick = onNavigateUp,
                contentDescription = stringResource(R.string.knotwork_editor_action_navigate_up),
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
            )
            TitleStack(
                name = name,
                onNameChange = onNameChange,
                subtitle = subtitle,
                modifier = Modifier.weight(1f),
            )
            // Compact primary action: `KnotworkButtonSize.Sm` keeps the
            // button below the toolbar height; the leading icon is dropped on
            // narrow screens so the title + subtitle stack keeps room. The
            // semantics (`contentDescription`) carry the action verbatim so
            // TalkBack still announces "Run" / "Re-run".
            when (primaryAction) {
                EditorPrimaryAction.Run -> KnotworkPrimaryButton(
                    text = stringResource(R.string.knotwork_editor_action_run),
                    onClick = onPrimaryAction,
                    enabled = primaryActionEnabled,
                    size = KnotworkButtonSize.Sm,
                )
                EditorPrimaryAction.Rerun -> KnotworkPrimaryButton(
                    text = stringResource(R.string.knotwork_editor_action_rerun),
                    onClick = onPrimaryAction,
                    enabled = primaryActionEnabled,
                    size = KnotworkButtonSize.Sm,
                )
                EditorPrimaryAction.None -> Unit
            }
            KnotworkIconButton(
                onClick = onOverflow,
                contentDescription = stringResource(R.string.knotwork_editor_action_overflow),
                icon = Icons.Outlined.MoreVert,
            )
        }
    }
}

/**
 * Selects which primary call-to-action renders in the toolbar's trailing slot.
 *
 * - [Run] — initial editing state and after a stop/reset. Gated by validation.
 * - [Rerun] — last completed run is the source of truth; the button replays it.
 * - [None] — hidden. Live run state owns Pause / Stop inside the run banner;
 *   showing a third button on the toolbar would compete for the same action.
 */
sealed interface EditorPrimaryAction {
    data object Run : EditorPrimaryAction
    data object Rerun : EditorPrimaryAction
    data object None : EditorPrimaryAction
}

/**
 * Title + optional subtitle stack rendered between the back icon and the primary
 * action. The pipeline name remains a [BasicTextField] (inline-editable); the
 * subtitle is a read-only [BodySm] beneath it.
 *
 * The field is laid out with a min-height equal to a single line of `TitleLg` so
 * the row collapses gracefully when [subtitle] is `null` and grows to two lines
 * when present without the title visibly jumping.
 */
@Composable
private fun TitleStack(
    name: String,
    onNameChange: (String) -> Unit,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(modifier = Modifier.defaultMinSize(minHeight = 28.dp)) {
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
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
                        if (name.isEmpty()) {
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
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = KnotworkTextStyles.BodySm,
                color = KnotworkTheme.extended.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = KnotworkTheme.spacing.sp2),
            )
        }
    }
}

/** Light-theme preview — editing state, primary `Run`. */
@Preview(name = "EditorToolbar — light editing", showBackground = true, widthDp = 720)
@Composable
private fun EditorToolbarLightEditingPreview() {
    KnotworkTheme(darkTheme = false) {
        EditorToolbar(
            name = "Weekly digest",
            onNameChange = {},
            onNavigateUp = {},
            onPrimaryAction = {},
            onOverflow = {},
            subtitle = "Editing · 4 nodes · 3 edges",
            primaryAction = EditorPrimaryAction.Run,
        )
    }
}

/** Dark-theme preview — Re-run after a successful run. */
@Preview(name = "EditorToolbar — dark rerun", showBackground = true, widthDp = 720)
@Composable
private fun EditorToolbarDarkRerunPreview() {
    KnotworkTheme(darkTheme = true) {
        EditorToolbar(
            name = "research-deepdive",
            onNameChange = {},
            onNavigateUp = {},
            onPrimaryAction = {},
            onOverflow = {},
            subtitle = "Last run · 12.8 s · 2 408 tok",
            primaryAction = EditorPrimaryAction.Rerun,
        )
    }
}

/** Preview — invalid state: subtitle reflects issues, primary action greyed. */
@Preview(name = "EditorToolbar — issues", showBackground = true, widthDp = 720)
@Composable
private fun EditorToolbarIssuesPreview() {
    KnotworkTheme(darkTheme = false) {
        EditorToolbar(
            name = "research-deepdive",
            onNameChange = {},
            onNavigateUp = {},
            onPrimaryAction = {},
            onOverflow = {},
            subtitle = "2 issues · can't run",
            primaryAction = EditorPrimaryAction.Run,
            primaryActionEnabled = false,
        )
    }
}

/** Preview — running state: primary action hidden, banner owns Pause/Stop. */
@Preview(name = "EditorToolbar — running", showBackground = true, widthDp = 720)
@Composable
private fun EditorToolbarRunningPreview() {
    KnotworkTheme(darkTheme = false) {
        EditorToolbar(
            name = "research-deepdive",
            onNameChange = {},
            onNavigateUp = {},
            onPrimaryAction = {},
            onOverflow = {},
            subtitle = "Running · 6 / 11 · 4.2 s",
            primaryAction = EditorPrimaryAction.None,
        )
    }
}
