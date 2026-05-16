package app.knotwork.design.components.pipelineeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.ChipStyle
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.theme.KnotworkTheme

/**
 * Modal bottom sheet that hosts the per-type configuration form for one
 * pipeline node.
 *
 * Visual contract: `compose/components/README.md` §NodeConfigSheet. The
 * sheet's chrome (drag handle / type pill / scrollable body / sticky
 * Cancel-Save row) is fixed; the body switches on `when (config)` via
 * [NodeConfigForms.Body] and dispatches to the per-type form. Save is
 * gated on [NodeConfigValidation.validate] — if any rule fails the
 * inline error surfaces under the offending field and the primary CTA
 * renders disabled.
 *
 * **Stateless** — the caller owns the working [config] value and the
 * dirty/clean transitions. The sheet calls [onChange] for every keystroke
 * / chip toggle, [onCancel] when the user dismisses without saving, and
 * [onSave] with the validated payload when Save is pressed.
 *
 * @param config the working configuration being edited.
 * @param peerTitles set of sibling node titles in the same pipeline,
 * excluding the one currently being edited; drives the uniqueness rule.
 * @param onChange invoked with the next [NodeConfig] every time the user
 * edits a field.
 * @param onCancel invoked when the user dismisses the sheet.
 * @param onSave invoked with [config] when the user taps Save and
 * validation passes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeConfigSheet(
    config: NodeConfig,
    peerTitles: Set<String>,
    onChange: (NodeConfig) -> Unit,
    onCancel: () -> Unit,
    onSave: (NodeConfig) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val errors = NodeConfigValidation.validate(config = config, peerTitles = peerTitles)
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        NodeConfigSheetBody(
            config = config,
            errors = errors,
            onChange = onChange,
            onCancel = onCancel,
            onSave = onSave,
        )
    }
}

/**
 * Renders the sheet body without the `ModalBottomSheet` wrapper. Pulled
 * out so the catalog can render the same body inline (without an Activity
 * window context) and so snapshot tests render deterministically without
 * the modal scrim.
 *
 * @param config the working configuration being edited.
 * @param errors validator output for [config].
 * @param onChange invoked with the next [NodeConfig] on every edit.
 * @param onCancel invoked on the Cancel action.
 * @param onSave invoked on the Save action when [errors] is empty.
 */
@Composable
fun NodeConfigSheetBody(
    config: NodeConfig,
    errors: Map<FieldId, ValidationFailure>,
    onChange: (NodeConfig) -> Unit,
    onCancel: () -> Unit,
    onSave: (NodeConfig) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp3),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        SheetHeader(type = config.type)
        NodeConfigForms.Body(config = config, errors = errors, onChange = onChange)
        Spacer(modifier = Modifier.size(KnotworkTheme.spacing.sp2))
        SheetActionRow(saveEnabled = errors.isEmpty(), onCancel = onCancel, onSave = { onSave(config) })
    }
}

/** Type pill rendered above the form body. */
@Composable
private fun SheetHeader(type: NodeType) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KnotworkChip(label = type.displayLabel(), style = ChipStyle.Tonal)
    }
}

/** Bottom sticky row — Cancel + Save (Save gated on validation). */
@Composable
private fun SheetActionRow(saveEnabled: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = KnotworkTheme.spacing.sp2,
            alignment = Alignment.End,
        ),
    ) {
        KnotworkTextButton(
            text = stringResource(R.string.knotwork_node_config_action_cancel),
            onClick = onCancel,
        )
        KnotworkPrimaryButton(
            text = stringResource(R.string.knotwork_node_config_action_save),
            onClick = onSave,
            enabled = saveEnabled,
        )
    }
}
