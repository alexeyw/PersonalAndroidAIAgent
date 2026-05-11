package ai.agent.android.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontal scrollable row of [AssistChip]s exposing the prompt variables that can be
 * inserted into a system prompt editor (e.g. `$DATE`, `$TIME`, `$TOOLS`, `$MODEL`,
 * `$MEMORY_SUMMARY`).
 *
 * The component is purely stateless: clicking a chip invokes [onChipClick] with the full
 * variable token (including the leading `$`) so the caller can splice it at the current
 * cursor position. An empty [variables] list collapses to an empty row.
 *
 * @param variables tokens to display as chips, each prefixed with `$`. Order is preserved.
 * @param onChipClick callback invoked with the full token when the user taps a chip.
 * @param modifier optional [Modifier] applied to the underlying [LazyRow].
 */
@Composable
fun VariableChipsRow(variables: List<String>, onChipClick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(items = variables, key = { it }) { token ->
            AssistChip(
                onClick = { onChipClick(token) },
                label = { Text(token) },
            )
        }
    }
}
