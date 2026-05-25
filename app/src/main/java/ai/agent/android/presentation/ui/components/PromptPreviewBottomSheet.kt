package ai.agent.android.presentation.ui.components

import ai.agent.android.R
import ai.agent.android.domain.prompt.PromptSegment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme

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
 * [segments] list (typically via `PromptTemplateEngine.renderSegments`). Pass `null` to
 * surface the loading state — a centred [CircularProgressIndicator] is shown until the
 * engine finishes rendering, which matters for slow providers (e.g. `$MEMORY_SUMMARY`
 * hits the database).
 *
 * @param segments ordered list of segments produced by the prompt engine, or `null`
 * while resolution is still in flight.
 * @param onDismiss invoked when the sheet is dragged away or its scrim is tapped.
 * @param modifier optional [Modifier] applied to the [ModalBottomSheet] root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptPreviewBottomSheet(segments: List<PromptSegment>?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KnotworkTheme.spacing.sp6, vertical = KnotworkTheme.spacing.sp4)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.prompt_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (segments == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LoadingPlaceholderHeight)
                        .padding(top = KnotworkTheme.spacing.sp3),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                PreviewBody(segments = segments, modifier = Modifier.padding(top = KnotworkTheme.spacing.sp3))
            }
        }
    }
}

/**
 * Renders [segments] as a single flowing [Text] so the preview matches the layout of the
 * final prompt string (line breaks come from the prompt itself, not from segment
 * boundaries). Unknown placeholders are embedded as `inlineContent` slots so each one
 * can host its own tooltip without breaking the inline flow — line wrapping treats them
 * as ordinary glyph runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewBody(segments: List<PromptSegment>, modifier: Modifier = Modifier) {
    val resolvedBg = MaterialTheme.colorScheme.tertiaryContainer
    val resolvedFg = MaterialTheme.colorScheme.onTertiaryContainer
    val unknownBg = MaterialTheme.colorScheme.errorContainer
    val unknownFg = MaterialTheme.colorScheme.onErrorContainer
    val textStyle = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Pre-compute the inline-placeholder dimensions for every unknown segment so the
    // slot inside `Text` reserves exactly the space the literal `$KEY` would occupy.
    // Theme colors participate in the cache key — without them the inline content keeps
    // serving stale colors after a light/dark mode switch.
    val inlineContent = remember(segments, textStyle, density, unknownBg, unknownFg) {
        buildInlineContent(
            segments = segments,
            textStyle = textStyle,
            measurer = measurer,
            density = density,
            unknownBg = unknownBg,
            unknownFg = unknownFg,
        )
    }

    Text(
        text = buildPreviewAnnotatedString(
            segments = segments,
            resolvedBg = resolvedBg,
            resolvedFg = resolvedFg,
        ),
        style = textStyle,
        inlineContent = inlineContent,
        modifier = modifier,
    )
}

/**
 * Builds the [AnnotatedString] consumed by the preview [Text]. Unknown segments are
 * emitted as inline-content placeholders keyed by [unknownInlineId] so the
 * `inlineContent` map can substitute a tooltip-wrapped composable at render time.
 */
private fun buildPreviewAnnotatedString(
    segments: List<PromptSegment>,
    resolvedBg: androidx.compose.ui.graphics.Color,
    resolvedFg: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    var unknownIndex = 0
    for (segment in segments) {
        when (segment) {
            is PromptSegment.Literal -> append(segment.text)
            is PromptSegment.Resolved -> withStyle(
                SpanStyle(color = resolvedFg, background = resolvedBg),
            ) {
                append(segment.value)
            }
            is PromptSegment.Unknown -> {
                appendInlineContent(
                    id = unknownInlineId(unknownIndex),
                    alternateText = "$" + segment.key,
                )
                unknownIndex++
            }
        }
    }
}

/**
 * For every unknown segment, produces an [InlineTextContent] entry whose [Placeholder]
 * dimensions match the rendered width and height of `$KEY` under [textStyle], so the
 * inline slot does not disturb the surrounding line layout. The slot composable wraps a
 * [TooltipBox] that surfaces "Variable not found" on long-press.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun buildInlineContent(
    segments: List<PromptSegment>,
    textStyle: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    unknownBg: androidx.compose.ui.graphics.Color,
    unknownFg: androidx.compose.ui.graphics.Color,
): Map<String, InlineTextContent> {
    val map = mutableMapOf<String, InlineTextContent>()
    var unknownIndex = 0
    for (segment in segments) {
        if (segment !is PromptSegment.Unknown) continue
        val token = "$" + segment.key
        val measured = measurer.measure(AnnotatedString(token), style = textStyle)
        val width = with(density) { measured.size.width.toSp() }
        val height = with(density) { measured.size.height.toSp() }
        val id = unknownInlineId(unknownIndex)
        map[id] = InlineTextContent(
            placeholder = Placeholder(
                width = width,
                height = height,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
            children = { _ ->
                val tooltipState = rememberTooltipState()
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above,
                    ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.prompt_preview_variable_not_found)) } },
                    state = tooltipState,
                ) {
                    Box(
                        modifier = Modifier.background(unknownBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = token,
                            style = textStyle.copy(color = unknownFg),
                        )
                    }
                }
            },
        )
        unknownIndex++
    }
    return map
}

/**
 * Stable id used to bind an inline placeholder in the [AnnotatedString] to its
 * `InlineTextContent` slot. The index disambiguates multiple unknown placeholders in the
 * same prompt; the actual key is intentionally NOT part of the id because two unknowns
 * with the same key still need separate slots.
 */
private fun unknownInlineId(index: Int): String = "prompt_unknown_$index"

// Height of the loading-spinner container before segments resolve. Off the
// `KnotworkSpacing` 4 dp scale on purpose — sized to host a centred
// `CircularProgressIndicator` with a vertical-rhythm match against the
// surrounding `bodyMedium` line height.
private val LoadingPlaceholderHeight = 120.dp
