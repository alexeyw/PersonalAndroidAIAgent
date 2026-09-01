package app.knotwork.android.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.knotwork.android.R
import app.knotwork.android.domain.prompt.PromptSegment
import app.knotwork.design.components.prompt.PromptPreviewSegmentUi
import app.knotwork.design.components.prompt.PromptPreviewSheet
import app.knotwork.design.components.prompt.PromptPreviewSheetUi

/**
 * `:app` binding of the catalog's prompt-preview sheet: resolves the copy and
 * translates the domain's [PromptSegment] into the catalog's own segment
 * vocabulary.
 *
 * The sheet lives in `:catalog` so it can be photographed — including the two
 * states that matter most and were previously invisible: a prompt whose
 * placeholder resolved, and one whose placeholder did not.
 *
 * @param segments Ordered segments produced by the prompt engine (typically
 *   `PromptTemplateEngine.renderSegments`), or `null` while resolution is still
 *   in flight.
 * @param onDismiss Invoked when the sheet is dragged away or its scrim tapped.
 * @param modifier Optional [Modifier] applied to the sheet root.
 */
@Composable
fun PromptPreviewBottomSheet(segments: List<PromptSegment>?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PromptPreviewSheet(
        segments = segments?.map { it.toUi() },
        ui = PromptPreviewSheetUi(
            title = stringResource(R.string.prompt_preview_title),
            variableNotFoundTooltip = stringResource(R.string.prompt_preview_variable_not_found),
        ),
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/**
 * Translates one domain segment into the catalog's vocabulary.
 *
 * Exhaustive rather than defaulting: a new segment kind must be given a
 * rendering here, not silently fall through to plain text — which is exactly
 * how an unresolved placeholder would stop looking like an error.
 *
 * @return The catalog-side segment.
 */
private fun PromptSegment.toUi(): PromptPreviewSegmentUi = when (this) {
    is PromptSegment.Literal -> PromptPreviewSegmentUi.Literal(text)
    is PromptSegment.Resolved -> PromptPreviewSegmentUi.Resolved(value)
    is PromptSegment.Unknown -> PromptPreviewSegmentUi.Unknown(key)
}
