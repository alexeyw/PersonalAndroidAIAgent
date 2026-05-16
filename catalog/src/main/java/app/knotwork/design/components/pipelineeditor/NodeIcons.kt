package app.knotwork.design.components.pipelineeditor

import androidx.compose.ui.graphics.vector.ImageVector
import app.knotwork.design.icons.AppIcons

/**
 * Header-strip glyph for [this] node type, sourced from the bundled
 * [AppIcons] set. Kept in its own file so a designer adding a 13th node
 * hue updates `NodeType.kt` + `NodeTypeColors.kt` + this mapping side-by-
 * side without spelunking through the card composable.
 *
 * @return the [ImageVector] rendered at 16 dp in the header strip.
 */
fun NodeType.glyph(): ImageVector = when (this) {
    NodeType.INPUT -> AppIcons.NodeInput
    NodeType.OUTPUT -> AppIcons.NodeOutput
    NodeType.LITE_RT -> AppIcons.NodeLite
    NodeType.CLOUD -> AppIcons.NodeCloud
    NodeType.INTENT_ROUTER -> AppIcons.NodeIntentRouter
    NodeType.IF_CONDITION -> AppIcons.NodeBranch
    NodeType.CLARIFICATION -> AppIcons.NodeClarify
    NodeType.TOOL -> AppIcons.NodeTool
    NodeType.DECOMPOSITION -> AppIcons.NodeDecompose
    NodeType.QUEUE_PROCESSOR -> AppIcons.NodeQueue
    NodeType.EVALUATION -> AppIcons.NodeEval
    NodeType.SUMMARY -> AppIcons.NodeSummary
}
