package ai.agent.android.presentation.ui.pipeline.editor.bars

import ai.agent.android.R
import ai.agent.android.domain.models.PipelineValidationError
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

private val BAR_MAX_HEIGHT = 184.dp
private val BAR_MIN_HEIGHT = 36.dp

/**
 * Bottom bar listing every [PipelineValidationError] for the active pipeline. Tapping a
 * row dispatches [onFocusNode] with the offending node id so the canvas can pan / select
 * onto it (`node-specs.md` §validation bar).
 *
 * Rendered as a `LazyColumn` capped at [BAR_MAX_HEIGHT] so the canvas keeps the lion's
 * share of the screen even on long error lists.
 *
 * @param errors validation errors from `PipelineGraph.validate()` via the ViewModel.
 * @param errorLabels human-readable copies for each error (resolved by the caller using
 * the existing `OrchestratorViewModel.validationErrorAsUiText` so wording stays single-sourced).
 * @param nodeLookup maps `nodeId → display label` for the NodeEmptyContext error row.
 * @param onFocusNode invoked when the user taps a row.
 * @param modifier optional modifier applied to the bar root.
 */
@Composable
internal fun ValidationBar(
    errors: List<PipelineValidationError>,
    errorLabels: List<String>,
    nodeLookup: (String) -> String?,
    onFocusNode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (errors.isEmpty()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = BAR_MIN_HEIGHT)
                .background(KnotworkTheme.extended.surface1)
                .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.pipeline_editor_validation_clean),
                style = KnotworkTextStyles.LabelMd,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = BAR_MAX_HEIGHT)
            .background(KnotworkTheme.extended.surface1),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(
                    id = R.plurals.pipeline_editor_validation_count,
                    count = errors.size,
                    errors.size,
                ),
                style = KnotworkTextStyles.LabelMd,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.pipeline_editor_validation_focus),
                style = KnotworkTextStyles.LabelSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = errors.zip(errorLabels)) { (error, label) ->
                ValidationRow(
                    label = label,
                    nodeId = (error as? PipelineValidationError.NodeEmptyContext)?.nodeId,
                    nodeLookup = nodeLookup,
                    onFocusNode = onFocusNode,
                )
            }
        }
    }
}

@Composable
private fun ValidationRow(
    label: String,
    nodeId: String?,
    nodeLookup: (String) -> String?,
    onFocusNode: (String) -> Unit,
) {
    val targetNodeId = nodeId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = targetNodeId != null) {
                if (targetNodeId != null) onFocusNode(targetNodeId)
            }
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KnotworkTextStyles.BodySm,
            color = MaterialTheme.colorScheme.error,
        )
        val nodeLabel = targetNodeId?.let(nodeLookup)
        if (nodeLabel != null) {
            Text(
                text = "  ·  $nodeLabel",
                style = KnotworkTextStyles.LabelSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
                modifier = Modifier.padding(start = KnotworkTheme.spacing.sp1),
            )
        }
    }
}
