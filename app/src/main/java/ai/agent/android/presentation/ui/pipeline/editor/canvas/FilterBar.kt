package ai.agent.android.presentation.ui.pipeline.editor.canvas

import ai.agent.android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Overlay search bar pinned to the top of the canvas while
 * `EditorState.searchOpen` is `true`. Lets the user filter nodes by label /
 * type as they type; pressing Enter (`ImeAction.Search`) jumps to the first
 * match.
 *
 * Auto-focuses on first composition so the keyboard appears immediately when
 * the overflow toggle opens the bar.
 *
 * @param query current query string.
 * @param matchCount number of nodes matching [query] in the live graph.
 * @param onQueryChange invoked on each keystroke.
 * @param onSubmit invoked on `Enter` — caller centres the canvas on the first
 *   matching node.
 * @param onClose invoked when the `×` button is tapped.
 * @param modifier optional layout modifier (typically `.align(TopStart)`).
 */
@Composable
internal fun FilterBar(
    query: String,
    matchCount: Int,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        color = KnotworkTheme.extended.surface1,
        tonalElevation = KnotworkTheme.elevation.el2,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = KnotworkTheme.extended.onSurfaceMuted,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = {
                    Text(text = stringResource(R.string.pipeline_editor_search_placeholder))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            if (query.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        id = R.plurals.pipeline_editor_search_count,
                        count = matchCount,
                        matchCount,
                    ),
                    style = KnotworkTextStyles.LabelSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.pipeline_editor_search_close),
                )
            }
        }
    }
}
