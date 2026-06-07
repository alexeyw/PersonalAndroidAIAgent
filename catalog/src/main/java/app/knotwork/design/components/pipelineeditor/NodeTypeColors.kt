@file:Suppress("MagicNumber") // Luminance bands ARE the data — naming the breakpoints would not clarify them.

package app.knotwork.design.components.pipelineeditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.knotwork.design.theme.KnotworkTheme

/**
 * Reads the header-strip tint for [this] node type from the
 * [KnotworkTheme.extended] palette. The 12 hues are pinned identically in
 * light and dark themes by design (see `tokens/ExtendedColors.kt`) so a
 * pipeline reads the same on either palette.
 *
 * @return the [Color] from `extended.node*` matching [this] enum entry.
 */
@Composable
@ReadOnlyComposable
fun NodeType.headerTint(): Color {
    val extended = KnotworkTheme.extended
    return when (this) {
        NodeType.INPUT -> extended.nodeInput
        NodeType.OUTPUT -> extended.nodeOutput
        NodeType.LITE_RT -> extended.nodeLiteRt
        NodeType.CLOUD -> extended.nodeCloud
        NodeType.INTENT_ROUTER -> extended.nodeIntentRouter
        NodeType.IF_CONDITION -> extended.nodeIfCondition
        NodeType.CLARIFICATION -> extended.nodeClarification
        NodeType.TOOL -> extended.nodeTool
        NodeType.DECOMPOSITION -> extended.nodeDecomposition
        NodeType.QUEUE_PROCESSOR -> extended.nodeQueueProcessor
        NodeType.EVALUATION -> extended.nodeEvaluation
        NodeType.SUMMARY -> extended.nodeSummary
    }
}

/**
 * Picks an on-tone foreground colour (glyph + label) that reads on top of
 * the given header [strip] tint. Implements the L-band rule from
 * NodeCard tints:
 *
 *  - `L < 0.5` → `Color.White` (hue is dark enough that white reads cleanly).
 *  - `L ≥ 0.7` → `MaterialTheme.colorScheme.onSurface` (hue is light enough
 *    that a near-black tone gives AA contrast).
 *  - Middle band (`0.5..0.7`) → `MaterialTheme.colorScheme.onPrimary`, the
 *    canonical Knotwork inverse foreground.
 *
 * Returning a single foreground avoids the otherwise tempting per-hue lookup
 * table — every node hue is run through the same band check so adding a
 * future 13th hue does not require a token edit.
 *
 * @param strip the header-strip background colour returned by [headerTint].
 * @return the foreground tone to use for the glyph + uppercase type label.
 */
@Composable
@ReadOnlyComposable
fun headerOnColor(strip: Color): Color {
    val l = strip.luminance()
    return when {
        l < 0.5f -> Color.White
        l >= 0.7f -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onPrimary
    }
}

/**
 * Uppercase display label for a node type rendered in the header strip
 * (`LabelSm` tracking 0.08 em per spec). Kept here next to [headerTint]
 * so a contributor adding a node type only edits one file group.
 *
 * @return human-readable two-or-three-word label.
 */
fun NodeType.displayLabel(): String = when (this) {
    NodeType.INPUT -> "INPUT"
    NodeType.OUTPUT -> "OUTPUT"
    NodeType.LITE_RT -> "LITE-RT"
    NodeType.CLOUD -> "CLOUD"
    NodeType.INTENT_ROUTER -> "INTENT ROUTER"
    NodeType.IF_CONDITION -> "IF"
    NodeType.CLARIFICATION -> "CLARIFY"
    NodeType.TOOL -> "TOOL"
    NodeType.DECOMPOSITION -> "DECOMPOSE"
    NodeType.QUEUE_PROCESSOR -> "QUEUE"
    NodeType.EVALUATION -> "EVAL"
    NodeType.SUMMARY -> "SUMMARY"
}
