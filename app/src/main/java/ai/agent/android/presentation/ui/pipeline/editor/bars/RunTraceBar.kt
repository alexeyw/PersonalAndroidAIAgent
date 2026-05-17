package ai.agent.android.presentation.ui.pipeline.editor.bars

import ai.agent.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

private val BAR_MIN_HEIGHT = 44.dp

/**
 * Bottom run-trace strip — replaces [ValidationBar] while a pipeline run is active.
 *
 * Shows the active node's display label and a small accent indicator so the user can
 * confirm execution is progressing. The traveling-dot animation on the edges (see
 * [ai.agent.android.presentation.ui.pipeline.editor.canvas.EditorEdges]) carries the
 * heavier "movement" signal; this bar surfaces the per-step text only.
 *
 * @param activeNodeLabel display label of the currently-running node, or `null` when no
 * node is active yet.
 * @param modifier optional layout modifier applied to the bar root.
 */
@Composable
internal fun RunTraceBar(activeNodeLabel: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BAR_MIN_HEIGHT)
            .background(KnotworkTheme.extended.surface1)
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Indicator()
        val text = if (activeNodeLabel != null) {
            stringResource(R.string.pipeline_editor_run_trace_active, activeNodeLabel)
        } else {
            stringResource(R.string.pipeline_editor_run_trace_idle)
        }
        Text(
            text = text,
            style = KnotworkTextStyles.LabelMd,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = KnotworkTheme.spacing.sp3),
        )
    }
}

@Composable
private fun Indicator() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(KnotworkTheme.extended.outlineStrong),
    )
}
