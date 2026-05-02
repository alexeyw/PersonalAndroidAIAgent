package ai.agent.android.presentation.ui.components

import ai.agent.android.domain.prompt.PromptSegment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet displaying a prompt with `$VARIABLES` already substituted.
 *
 * Resolved variables are tinted with [MaterialTheme.colorScheme.tertiaryContainer] so the
 * user can clearly see what the engine put in place of each placeholder. Unknown
 * variables are highlighted in [MaterialTheme.colorScheme.errorContainer] and wrapped in
 * a [PlainTooltip] saying "Variable not found", since unresolved tokens almost always
 * indicate a typo in the template.
 *
 * The component is a thin presentation shell: the caller is responsible for producing the
 * [segments] list (typically via `PromptTemplateEngine.renderSegments`).
 *
 * @param segments ordered list of segments produced by the prompt engine.
 * @param onDismiss invoked when the sheet is dragged away or its scrim is tapped.
 * @param modifier optional [Modifier] applied to the [ModalBottomSheet] root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptPreviewBottomSheet(
    segments: List<PromptSegment>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Prompt preview",
                style = MaterialTheme.typography.titleMedium,
            )
            // Spacer between title and content kept implicit via paragraph styling below.
            PreviewBody(segments = segments, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/**
 * Renders the body of [PromptPreviewBottomSheet]. Extracted so the unknown-segment tooltip
 * can wrap each individual `Unknown` segment without creating a dedicated composable per
 * call site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewBody(
    segments: List<PromptSegment>,
    modifier: Modifier = Modifier,
) {
    val resolvedBg = MaterialTheme.colorScheme.tertiaryContainer
    val resolvedFg = MaterialTheme.colorScheme.onTertiaryContainer
    val unknownBg = MaterialTheme.colorScheme.errorContainer
    val unknownFg = MaterialTheme.colorScheme.onErrorContainer

    if (segments.none { it is PromptSegment.Unknown }) {
        // Fast path: no tooltips needed, render the whole thing as one annotated Text.
        Text(
            text = buildPreviewAnnotatedString(
                segments = segments,
                resolvedBg = resolvedBg,
                resolvedFg = resolvedFg,
                unknownBg = unknownBg,
                unknownFg = unknownFg,
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        return
    }

    // Slow path: split rendering into runs so each Unknown can host its own tooltip.
    Column(modifier = modifier) {
        val runs = mutableListOf<List<PromptSegment>>()
        var current = mutableListOf<PromptSegment>()
        for (segment in segments) {
            if (segment is PromptSegment.Unknown) {
                if (current.isNotEmpty()) {
                    runs.add(current)
                    current = mutableListOf()
                }
                runs.add(listOf(segment))
            } else {
                current.add(segment)
            }
        }
        if (current.isNotEmpty()) runs.add(current)

        for (run in runs) {
            val onlyUnknown = run.singleOrNull() as? PromptSegment.Unknown
            if (onlyUnknown != null) {
                val tooltipState = rememberTooltipState()
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above,
                    ),
                    tooltip = { PlainTooltip { Text("Variable not found") } },
                    state = tooltipState,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = unknownFg, background = unknownBg)) {
                                append('$').append(onlyUnknown.key)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Text(
                    text = buildPreviewAnnotatedString(
                        segments = run,
                        resolvedBg = resolvedBg,
                        resolvedFg = resolvedFg,
                        unknownBg = unknownBg,
                        unknownFg = unknownFg,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Builds an [AnnotatedString] that styles each segment according to its kind.
 *
 * Kept separate so the no-unknowns fast path can render the whole prompt as a single
 * `Text`, preserving line wrapping across segment boundaries — a per-segment `Text`
 * would force every segment onto its own line.
 */
private fun buildPreviewAnnotatedString(
    segments: List<PromptSegment>,
    resolvedBg: androidx.compose.ui.graphics.Color,
    resolvedFg: androidx.compose.ui.graphics.Color,
    unknownBg: androidx.compose.ui.graphics.Color,
    unknownFg: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    for (segment in segments) {
        when (segment) {
            is PromptSegment.Literal -> append(segment.text)
            is PromptSegment.Resolved -> withStyle(
                SpanStyle(color = resolvedFg, background = resolvedBg),
            ) {
                append(segment.value)
            }
            is PromptSegment.Unknown -> withStyle(
                SpanStyle(color = unknownFg, background = unknownBg),
            ) {
                append('$').append(segment.key)
            }
        }
    }
}
