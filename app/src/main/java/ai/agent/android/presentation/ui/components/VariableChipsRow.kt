package ai.agent.android.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.knotwork.design.components.chips.KnotworkVariableChip

/**
 * Horizontal scrollable row of [KnotworkVariableChip]s exposing the prompt
 * variables that can be inserted into a system-prompt editor (e.g. `$DATE`,
 * `$TIME`, `$TOOLS`, `$MODEL`, `$MEMORY_SUMMARY`).
 *
 * The component is purely stateless: clicking a chip invokes [onChipClick]
 * with the full variable token (including the leading `$`) so the caller
 * can splice it at the current cursor position. An empty [variables] list
 * collapses to an empty row.
 *
 * Migrated from the M3 `AssistChip` baseline to [KnotworkVariableChip]
 * (`inputs-and-chips.md` §6.4) so prompt-variable affordances render in the
 * mono/accent/dashed-border family the spec calls "template, not value".
 *
 * @param variables Tokens to display as chips. Items may include or omit the
 *  leading `$` — [KnotworkVariableChip] normalises the prefix.
 * @param onChipClick Callback invoked with the full token (with leading
 *  `$`) when the user taps a chip.
 * @param modifier Optional [Modifier] applied to the underlying [LazyRow].
 */
@Composable
fun VariableChipsRow(variables: List<String>, onChipClick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(items = variables, key = { it }) { token ->
            val bare = token.trimStart('$')
            KnotworkVariableChip(
                name = bare,
                onInsert = { onChipClick("$" + bare) },
            )
        }
    }
}
