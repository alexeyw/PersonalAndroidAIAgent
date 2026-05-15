package app.knotwork.design.icons

import androidx.compose.ui.graphics.vector.ImageVector
import app.knotwork.design.icons.imagevector.knotworkAutoLayoutIcon
import app.knotwork.design.icons.imagevector.knotworkBrainIcon
import app.knotwork.design.icons.imagevector.knotworkFlowIcon
import app.knotwork.design.icons.imagevector.knotworkMarkIcon
import app.knotwork.design.icons.imagevector.knotworkNodeBranchIcon
import app.knotwork.design.icons.imagevector.knotworkNodeClarifyIcon
import app.knotwork.design.icons.imagevector.knotworkNodeCloudIcon
import app.knotwork.design.icons.imagevector.knotworkNodeDecomposeIcon
import app.knotwork.design.icons.imagevector.knotworkNodeEvalIcon
import app.knotwork.design.icons.imagevector.knotworkNodeInputIcon
import app.knotwork.design.icons.imagevector.knotworkNodeIntentRouterIcon
import app.knotwork.design.icons.imagevector.knotworkNodeLiteIcon
import app.knotwork.design.icons.imagevector.knotworkNodeOutputIcon
import app.knotwork.design.icons.imagevector.knotworkNodeQueueIcon
import app.knotwork.design.icons.imagevector.knotworkNodeSummaryIcon
import app.knotwork.design.icons.imagevector.knotworkNodeToolIcon
import app.knotwork.design.icons.imagevector.knotworkWordmarkIcon

/**
 * Knotwork icon facade.
 *
 * Strategy (decisions.md §5): use Material Icons Extended where the built-in
 * glyph fits; declare custom [ImageVector]s only for the Knotwork-specific
 * symbols (pipeline node types, brand mark, Pipelines tab, auto-layout).
 *
 * See the icon-mapping reference in `project_docs/design/compose/icons/` for
 * the complete usage table and authoring guidance. Material icons are NOT
 * re-exported here — import them directly from
 * `androidx.compose.material.icons.outlined.*` etc. This object only holds
 * custom vectors.
 *
 * Implementations live in [imagevector] (one file per icon). Each file
 * exposes a top-level `internal val knotwork<Name>Icon: ImageVector`
 * lazily built from SVG path data, and the facade delegates to it.
 */
object AppIcons {
    /** Knotwork wordmark glyph (used in compact contexts; the literal "Knotwork" text wordmark is rendered with Inter). */
    val Wordmark: ImageVector get() = knotworkWordmarkIcon

    /** Knotwork brand mark (the in-app 24×24 simplification of the launcher icon). */
    val Mark: ImageVector get() = knotworkMarkIcon

    /** "Pipelines" bottom-nav tab — three connected nodes. */
    val Flow: ImageVector get() = knotworkFlowIcon

    /** Pipeline `INPUT` node glyph. */
    val NodeInput: ImageVector get() = knotworkNodeInputIcon

    /** Pipeline `INTENT_ROUTER` node glyph. */
    val NodeIntentRouter: ImageVector get() = knotworkNodeIntentRouterIcon

    /** Pipeline `IF_CONDITION` node glyph. */
    val NodeBranch: ImageVector get() = knotworkNodeBranchIcon

    /** Pipeline `CLARIFICATION` node glyph. */
    val NodeClarify: ImageVector get() = knotworkNodeClarifyIcon

    /** Pipeline `LITE_RT` node glyph (local on-device LLM). */
    val NodeLite: ImageVector get() = knotworkNodeLiteIcon

    /** Pipeline `CLOUD` node glyph. */
    val NodeCloud: ImageVector get() = knotworkNodeCloudIcon

    /** Pipeline `TOOL` node glyph. */
    val NodeTool: ImageVector get() = knotworkNodeToolIcon

    /** Pipeline `DECOMPOSITION` node glyph. */
    val NodeDecompose: ImageVector get() = knotworkNodeDecomposeIcon

    /** Pipeline `QUEUE_PROCESSOR` node glyph. */
    val NodeQueue: ImageVector get() = knotworkNodeQueueIcon

    /** Pipeline `EVALUATION` node glyph. */
    val NodeEval: ImageVector get() = knotworkNodeEvalIcon

    /** Pipeline `SUMMARY` node glyph. */
    val NodeSummary: ImageVector get() = knotworkNodeSummaryIcon

    /** Pipeline `OUTPUT` node glyph. */
    val NodeOutput: ImageVector get() = knotworkNodeOutputIcon

    /** Editor toolbar — auto-layout action. */
    val AutoLayout: ImageVector get() = knotworkAutoLayoutIcon

    /** Memory entry / "Memory" navigation glyph. */
    val Brain: ImageVector get() = knotworkBrainIcon
}
