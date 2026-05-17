package ai.agent.android.presentation.ui.pipeline.editor.canvas

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.presentation.ui.pipeline.editor.core.BezierEdge
import ai.agent.android.presentation.ui.pipeline.editor.core.CanvasTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme

/**
 * Canvas-space coordinates of a node's inbound and outbound port anchor points.
 *
 * Anchors are computed from the node's top-left corner plus the canonical NodeCard
 * geometry (`168 dp` wide, ports protruding `6 dp` past the edge). The caller passes
 * the dp ⇆ px [Density] so the math stays unit-agnostic.
 */
internal data class NodeAnchors(val inX: Float, val inY: Float, val outX: Float, val outY: Float)

private const val NODE_WIDTH_DP = 168f
private const val NODE_BASE_HEIGHT_DP = 64f
private const val DOT_TRAVEL_SPEED_DP_PER_SEC = 40f

/**
 * Computes a node's inbound (top-centre) and outbound (bottom-centre) port anchor in
 * canvas-space px.
 *
 * Single anchor pair is sufficient because every node type the editor surfaces either
 * has one outbound port (default) or stacks multiple labelled ports along the same
 * bottom edge; per-port offsets are applied at draw time, not here.
 */
internal fun nodeAnchors(node: NodeModel, density: Density): NodeAnchors {
    val widthPx = with(density) { NODE_WIDTH_DP.dp.toPx() }
    val heightPx = with(density) { NODE_BASE_HEIGHT_DP.dp.toPx() }
    val centreX = node.x + widthPx / 2f
    return NodeAnchors(
        inX = centreX,
        inY = node.y,
        outX = centreX,
        outY = node.y + heightPx,
    )
}

/**
 * Draws every edge in [connections] as a cubic Bezier between the source's outbound
 * anchor and the target's inbound anchor. Highlights the edge identified by
 * [highlightedConnectionId] (the run-trace cursor / hover) and applies a traveling
 * accent dot whose duration is derived from the edge's arc length so motion stays
 * visually constant at the spec's 40 dp/s.
 *
 * @param connections graph edges, in stable iteration order (typically the persisted list).
 * @param nodesById map of `id → NodeModel` used to look up anchor positions.
 * @param transform pan / zoom applied uniformly to all paths.
 * @param connectionDraft optional in-flight connection (drag from a port to the pointer).
 * If present, an extra preview edge is drawn between the source port and the live pointer
 * in dashed style.
 * @param runningEdgeIds set of edge ids that should render the traveling-dot animation
 * (run-trace mode). Pass empty when the pipeline is not running.
 * @param reducedMotion when `true`, the traveling dot is rendered as a static cursor at
 * the midpoint instead of an animated transit — `decisions.md §14`.
 */
@Composable
@Suppress("LongParameterList") // The canvas layer needs every input as one frame.
internal fun EditorEdges(
    connections: List<ConnectionModel>,
    nodesById: Map<String, NodeModel>,
    transform: CanvasTransform,
    connectionDraft: ConnectionDraftDrawData?,
    runningEdgeIds: Set<String>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val edgeColor = KnotworkTheme.extended.divider
    val accentColor = KnotworkTheme.extended.outlineStrong
    val dotColor = KnotworkTheme.extended.outlineStrong
    val strokeWidth = with(density) { 2.dp.toPx() }
    val dotRadius = with(density) { 4.dp.toPx() }
    val dotTravelSpeedPx = with(density) { DOT_TRAVEL_SPEED_DP_PER_SEC.dp.toPx() }

    // Continuous elapsed-time tick (in milliseconds since first frame) drives every running
    // edge's traveling dot. Storing a monotonically increasing scalar — rather than the prior
    // shared `0..1` infinite transition — lets each edge compute its own phase as
    // `(timeMs / 1000f / cycleSec) % 1f` regardless of how long the edge is: a 4-second
    // cycle no longer races a 2-second reset window and teleports back to the source.
    //
    // `withFrameNanos` runs only when the host has running edges (this composable is only in
    // the tree while the editor is mounted and edges are drawn); when none are running we
    // still tick, but the per-edge branch below short-circuits before computing positions.
    val animateRunning = runningEdgeIds.isNotEmpty() && !reducedMotion
    var timeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animateRunning) {
        if (!animateRunning) return@LaunchedEffect
        val startNanos = withFrameNanosCompat { it }
        while (true) {
            withFrameNanosCompat { now -> timeMs = (now - startNanos) / NS_PER_MS }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        connections.forEach { c ->
            val source = nodesById[c.sourceNodeId] ?: return@forEach
            val target = nodesById[c.targetNodeId] ?: return@forEach
            val srcAnchors = nodeAnchors(source, density)
            val tgtAnchors = nodeAnchors(target, density)
            val sx = transform.canvasToScreenX(srcAnchors.outX)
            val sy = transform.canvasToScreenY(srcAnchors.outY)
            val tx = transform.canvasToScreenX(tgtAnchors.inX)
            val ty = transform.canvasToScreenY(tgtAnchors.inY)
            val (c0, c1) = BezierEdge.controlPoints(sx, sy, tx, ty)
            val isRunning = c.id in runningEdgeIds
            val path = Path().apply {
                moveTo(sx, sy)
                cubicTo(c0.first, c0.second, c1.first, c1.second, tx, ty)
            }
            drawPath(
                path = path,
                color = if (isRunning) accentColor else edgeColor,
                style = Stroke(width = if (isRunning) strokeWidth * EDGE_STROKE_RUNNING_FACTOR else strokeWidth),
            )
            if (isRunning) {
                val arcLength = BezierEdge.approximateArcLength(
                    sx,
                    sy,
                    c0.first,
                    c0.second,
                    c1.first,
                    c1.second,
                    tx,
                    ty,
                )
                val phase = if (reducedMotion) {
                    MIDPOINT_T
                } else {
                    // Each edge cycles independently in (arcLength / dotTravelSpeedPx) seconds,
                    // floored at MIN_CYCLE_SECONDS so very short edges don't spin so fast they
                    // strobe. `timeMs` is the continuous elapsed-time tick — no shared base
                    // duration that could clip long edges.
                    val cycleSec = (arcLength / dotTravelSpeedPx).coerceAtLeast(MIN_CYCLE_SECONDS)
                    val timeSec = timeMs / MS_PER_SEC.toFloat()
                    (timeSec / cycleSec) % 1f
                }
                val (dx, dy) = BezierEdge.pointAt(
                    phase,
                    sx,
                    sy,
                    c0.first,
                    c0.second,
                    c1.first,
                    c1.second,
                    tx,
                    ty,
                )
                drawCircle(color = dotColor, radius = dotRadius, center = Offset(dx, dy))
            }
        }
        if (connectionDraft != null) {
            drawPreviewEdge(
                src = connectionDraft.sourceScreen,
                pointer = connectionDraft.pointerScreen,
                accentColor = accentColor,
                strokeWidth = strokeWidth,
            )
        }
    }
}

/**
 * Snapshot of an in-flight connection draft in screen space — pre-projected so the
 * draw layer doesn't need access to [CanvasTransform].
 *
 * @property sourceScreen screen-space outbound anchor of the source node.
 * @property pointerScreen live pointer position.
 */
internal data class ConnectionDraftDrawData(val sourceScreen: Offset, val pointerScreen: Offset)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewEdge(
    src: Offset,
    pointer: Offset,
    accentColor: Color,
    strokeWidth: Float,
) {
    val (c0, c1) = BezierEdge.controlPoints(src.x, src.y, pointer.x, pointer.y)
    val path = Path().apply {
        moveTo(src.x, src.y)
        cubicTo(c0.first, c0.second, c1.first, c1.second, pointer.x, pointer.y)
    }
    drawPath(
        path = path,
        color = accentColor,
        style = Stroke(width = strokeWidth * EDGE_PREVIEW_STROKE_FACTOR),
    )
}

private const val MS_PER_SEC = 1_000
private const val NS_PER_MS = 1_000_000L
private const val MIN_CYCLE_SECONDS = 0.5f
private const val MIDPOINT_T = 0.5f
private const val EDGE_STROKE_RUNNING_FACTOR = 1.5f
private const val EDGE_PREVIEW_STROKE_FACTOR = 1.5f

/**
 * Thin wrapper over [androidx.compose.runtime.withFrameNanos] so the public
 * surface signature stays one line and the import block does not gain a noisy
 * `kotlin.coroutines` re-export. Exists purely to keep [EditorEdges] readable.
 */
private suspend inline fun <R> withFrameNanosCompat(crossinline block: (Long) -> R): R =
    androidx.compose.runtime.withFrameNanos { nanos -> block(nanos) }
