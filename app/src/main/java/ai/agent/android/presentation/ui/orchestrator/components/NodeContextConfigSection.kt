package ai.agent.android.presentation.ui.orchestrator.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Renders the "Input Data" section of the node configuration dialog: a header,
 * five labelled checkboxes corresponding to the flags of
 * `NodeContextConfig`, and a hint banner advising the user to keep the input
 * minimal.
 *
 * The first checkbox ("Previous node output") is rendered locked-on because
 * `OrchestratorViewModel.updateNodeContextConfig` always forces
 * `nodeInput = true` — disabling it would silently break the chain. The
 * stateful caller is therefore not allowed to flip this flag from the UI; we
 * accept no callback for it.
 *
 * The composable is stateless: every visible flag is a parameter and every
 * change is reported through a dedicated lambda. State must be hoisted into
 * the parent dialog so that the user can review the changes before pressing
 * Save.
 *
 * @param originalTask Whether the "Original user task" checkbox is checked.
 * @param chatHistory Whether the "Chat history" checkbox is checked.
 * @param longTermMemory Whether the "Long-term memory" checkbox is checked.
 * @param toolResults Whether the "Tool results" checkbox is checked.
 * @param onOriginalTaskChange Invoked when the user toggles the "Original
 * user task" checkbox; receives the new value.
 * @param onChatHistoryChange Invoked when the user toggles the "Chat history"
 * checkbox; receives the new value.
 * @param onLongTermMemoryChange Invoked when the user toggles the "Long-term
 * memory" checkbox; receives the new value.
 * @param onToolResultsChange Invoked when the user toggles the "Tool results"
 * checkbox; receives the new value.
 * @param modifier Modifier applied to the root [Column] of the section.
 */
@Composable
fun NodeContextConfigSection(
    originalTask: Boolean,
    chatHistory: Boolean,
    longTermMemory: Boolean,
    toolResults: Boolean,
    onOriginalTaskChange: (Boolean) -> Unit,
    onChatHistoryChange: (Boolean) -> Unit,
    onLongTermMemoryChange: (Boolean) -> Unit,
    onToolResultsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Input Data",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )

        ContextFlagRow(
            checked = true,
            enabled = false,
            label = "Previous node output",
            hint = "Result of the previous step (always enabled)",
            onCheckedChange = null,
        )
        ContextFlagRow(
            checked = originalTask,
            enabled = true,
            label = "Original user task",
            hint = "The chat message that started this run",
            onCheckedChange = onOriginalTaskChange,
        )
        ContextFlagRow(
            checked = chatHistory,
            enabled = true,
            label = "Chat history",
            hint = "All earlier messages in the current session",
            onCheckedChange = onChatHistoryChange,
        )
        ContextFlagRow(
            checked = longTermMemory,
            enabled = true,
            label = "Long-term memory",
            hint = "Relevant entries from the agent's long-term memory",
            onCheckedChange = onLongTermMemoryChange,
        )
        ContextFlagRow(
            checked = toolResults,
            enabled = true,
            label = "Tool results",
            hint = "Data returned by tools in the current step",
            onCheckedChange = onToolResultsChange,
        )

        Surface(
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                text = "Fewer inputs make the node faster and cheaper. " +
                    "Enable only what's needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/**
 * Single checkbox row used inside [NodeContextConfigSection]. Renders a
 * Material 3 `Checkbox` next to a two-line label/hint column.
 *
 * The whole [Row] is the click target — `Modifier.toggleable` with
 * `Role.Checkbox` flips the flag and announces the row as a single
 * checkbox to TalkBack, so users do not need to aim at the small icon.
 * Passing `null` for [onCheckedChange] disables the toggle and removes
 * the click target entirely; combined with `enabled = false` on the
 * `Checkbox`, this is how the locked-on "Previous node output" row is
 * rendered.
 *
 * @param checked Current value of the flag.
 * @param enabled Whether the user can toggle the flag.
 * @param label Bold primary label shown next to the checkbox.
 * @param hint Subtitle explaining what the flag controls.
 * @param onCheckedChange Invoked with the new value on toggle. Pass `null`
 * for read-only rows; the click target then becomes inert.
 */
@Composable
private fun ContextFlagRow(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    hint: String,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    val rowModifier = if (onCheckedChange != null && enabled) {
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 2.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier,
    ) {
        // Click handling is hoisted onto the Row, so the checkbox itself
        // does not need its own listener. Passing `null` here also lets
        // TalkBack treat the row as a single semantics node.
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
