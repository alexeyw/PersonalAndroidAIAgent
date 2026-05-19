package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.BuildConfig
import ai.agent.android.R
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Triple-tap state picker rendered as a `DropdownMenu` anchored to the
 * `ChatHomeScreen` TopAppBar title. Visible only in debug builds — the
 * `BuildConfig.DEBUG` guard short-circuits the menu before it mounts so
 * the production binary carries no developer surface.
 *
 * Selecting an item calls [onPick] with the corresponding stable id
 * (`DebugStateIds.*`), which the screen forwards to the VM via
 * `forceState(debugStateForId(id)!!)`.
 *
 * @param expanded `true` to render the dropdown; `false` keeps it hidden.
 * @param onDismiss invoked when the user taps outside the dropdown.
 * @param onPick invoked with the picked state id.
 */
@Composable
fun ChatHomeDebugStatePicker(expanded: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    if (!BuildConfig.DEBUG) return
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = stringResource(app.knotwork.design.R.string.knotwork_chat_home_debug_picker_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        DebugStateRows.entries.forEach { row ->
            DropdownMenuItem(
                text = { Text(stringResource(row.labelRes)) },
                onClick = {
                    onDismiss()
                    onPick(row.id)
                },
            )
        }
    }
}

/**
 * Static catalogue of every entry exposed by [ChatHomeDebugStatePicker].
 *
 * Listed in the order they appear in the dropdown. Localised labels live
 * in `:app/src/main/res/values/strings.xml`.
 */
internal enum class DebugStateRows(val id: String, val labelRes: Int) {
    Empty(id = DebugStateIds.EMPTY, labelRes = R.string.chat_home_debug_state_empty),
    Idle(id = DebugStateIds.IDLE, labelRes = R.string.chat_home_debug_state_idle),
    Generating(id = DebugStateIds.GENERATING, labelRes = R.string.chat_home_debug_state_generating),
    HitlReadonly(id = DebugStateIds.HITL_READONLY, labelRes = R.string.chat_home_debug_state_hitl_readonly),
    HitlSensitive(id = DebugStateIds.HITL_SENSITIVE, labelRes = R.string.chat_home_debug_state_hitl_sensitive),
    HitlDestructive(id = DebugStateIds.HITL_DESTRUCTIVE, labelRes = R.string.chat_home_debug_state_hitl_destructive),
    Clarification(id = DebugStateIds.CLARIFICATION, labelRes = R.string.chat_home_debug_state_clarification),
    Error(id = DebugStateIds.ERROR, labelRes = R.string.chat_home_debug_state_error),
    DrawerOpen(id = DebugStateIds.DRAWER_OPEN, labelRes = R.string.chat_home_debug_state_drawer_open),
    ConsolePartial(id = DebugStateIds.CONSOLE_PARTIAL, labelRes = R.string.chat_home_debug_state_console_partial),
    ConsoleFull(id = DebugStateIds.CONSOLE_FULL, labelRes = R.string.chat_home_debug_state_console_full),
}
