package ai.agent.android.presentation.ui.pipeline.editor.canvas

import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.presentation.ui.pipeline.editor.config.NodeTypeMapper
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
import app.knotwork.design.components.pipelineeditor.NodePorts
import app.knotwork.design.components.pipelineeditor.OutboundPort
import app.knotwork.design.theme.KnotworkTheme

/**
 * Canvas-space coordinates of one port anchor (inbound or one of N outbound ports).
 *
 * Anchors are computed from the node's top-left corner plus the canonical NodeCard
 * geometry (`168 dp` wide, ports protruding `6 dp` past the edge). The caller passes
 * the dp ⇆ px [Density] so the math stays unit-agnostic.
 */
internal data class PortAnchor(val xCanvas: Float, val yCanvas: Float)

private const val NODE_WIDTH_DP = 168f
private const val NODE_BASE_HEIGHT_DP = 64f
private const val DOT_TRAVEL_SPEED_DP_PER_SEC = 40f

/**
 * Per-port horizontal spacing in dp. Picked to match the catalog NodeCard's outbound
 * port row visually (`Arrangement.spacedBy(sp2)` + label widths around 30–40 dp). Used
 * BOTH by [outboundPortAnchor] for edge rendering AND by EditorNode to position the
 * per-port hit targets, so the two stay in lockstep.
 */
internal const val PORT_SPACING_DP = 48f

/** Inner card width available for the outbound port row (NodeCard width − 2 × sp3 padding). */
private const val NODE_INNER_WIDTH_DP = NODE_WIDTH_DP - 24f

/**
 * Returns the canvas-space horizontal offset (relative to the node's centre) of outbound
 * port [index] given [count] total ports. Symmetric around centre; clamped so an
 * IntentRouter with 6 classes still fits inside the card.
 */
internal fun outboundPortOffsetDp(index: Int, count: Int): Float {
    if (count <= 1) return 0f
    val maxSpacing = NODE_INNER_WIDTH_DP / count
    val spacing = minOf(PORT_SPACING_DP, maxSpacing)
    return (index - (count - 1) / 2f) * spacing
}

/** Inbound port anchor — single dot centred on the top edge. */
internal fun inboundPortAnchor(node: NodeModel, density: Density): PortAnchor {
    val widthPx = with(density) { NODE_WIDTH_DP.dp.toPx() }
    return PortAnchor(xCanvas = node.x + widthPx / 2f, yCanvas = node.y)
}

/**
 * Outbound port anchor for the port with [portLabel] on [node] (using [ports] to enumerate).
 * For nodes with a single unlabelled outbound port the label is empty; for IF / Queue / Eval
 * / IntentRouter the label distinguishes which port the edge originates from.
 *
 * If [portLabel] doesn't match any declared port the anchor falls back to index 0 — that
 * way an imported connection with a stale label still renders next to a real port instead
 * of disappearing.
 */
internal fun outboundPortAnchor(node: NodeModel, ports: NodePorts, portLabel: String?, density: Density): PortAnchor {
    val widthPx = with(density) { NODE_WIDTH_DP.dp.toPx() }
    val heightPx = with(density) { NODE_BASE_HEIGHT_DP.dp.toPx() }
    val centreX = node.x + widthPx / 2f
    val outY = node.y + heightPx
    val outbound = ports.outbound
    if (outbound.isEmpty()) return PortAnchor(centreX, outY)
    val matched = outbound.indexOfFirst { matchesPort(it, portLabel) }
    val index = if (matched >= 0) matched else 0
    val offsetPx = with(density) { outboundPortOffsetDp(index, outbound.size).dp.toPx() }
    return PortAnchor(xCanvas = centreX + offsetPx, yCanvas = outY)
}

/**
 * Single source of truth for matching a connection's [ConnectionModel.label] against an
 * [OutboundPort]: equal labels, or the connection's `null` / blank label matched against
 * a `Default` port.
 */
private fun matchesPort(port: OutboundPort, label: String?): Boolean = when {
    label.isNullOrBlank() -> port is OutboundPort.Default
    else -> port.label == label
}

/** Convenience: builds the catalog `NodePorts` for a domain node (no per-type overrides). */
internal fun portsFor(node: NodeModel): NodePorts = NodePorts.forType(type = NodeTypeMapper.toCatalog(node.type))

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
    selectedEdgeId: String?,
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
            // Per-port anchors: edges originate at the dot matching the connection label
            // (e.g. Item / Done on QUEUE, True / False on IF) rather than the node centre.
            val srcAnchor = outboundPortAnchor(source, portsFor(source), c.label, density)
            val tgtAnchor = inboundPortAnchor(target, density)
            val sx = transform.canvasToScreenX(srcAnchor.xCanvas)
            val sy = transform.canvasToScreenY(srcAnchor.yCanvas)
            val tx = transform.canvasToScreenX(tgtAnchor.xCanvas)
            val ty = transform.canvasToScreenY(tgtAnchor.yCanvas)
            val (c0, c1) = BezierEdge.controlPoints(sx, sy, tx, ty)
            val isRunning = c.id in runningEdgeIds
            val isSelected = c.id == selectedEdgeId
            val path = Path().apply {
                moveTo(sx, sy)
                cubicTo(c0.first, c0.second, c1.first, c1.second, tx, ty)
            }
            val highlight = isRunning || isSelected
            drawPath(
                path = path,
                color = if (highlight) accentColor else edgeColor,
                style = Stroke(width = if (highlight) strokeWidth * EDGE_STROKE_RUNNING_FACTOR else strokeWidth),
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

/**
 * Returns the id of the connection nearest to a canvas-space tap, or `null` when no
 * edge is within the screen-space [toleranceDp] of the point.
 *
 * Used by the canvas tap handler to implement "tap an edge → select it" — the tolerance
 * is converted to canvas-space by dividing by the current transform scale, so the user
 * always gets the same visual hit area regardless of zoom.
 */
internal fun hitTestEdge(
    pointerCanvasX: Float,
    pointerCanvasY: Float,
    connections: List<ConnectionModel>,
    nodesById: Map<String, NodeModel>,
    transform: CanvasTransform,
    density: Density,
    toleranceDp: Float = EDGE_HIT_TOLERANCE_DP,
): String? {
    val toleranceScreenPx = with(density) { toleranceDp.dp.toPx() }
    val toleranceCanvas = toleranceScreenPx / transform.scale
    var bestId: String? = null
    var bestDist = Float.MAX_VALUE
    connections.forEach { c ->
        val src = nodesById[c.sourceNodeId] ?: return@forEach
        val tgt = nodesById[c.targetNodeId] ?: return@forEach
        val srcAnchor = outboundPortAnchor(src, portsFor(src), c.label, density)
        val tgtAnchor = inboundPortAnchor(tgt, density)
        val (cp0, cp1) = BezierEdge.controlPoints(
            srcAnchor.xCanvas,
            srcAnchor.yCanvas,
            tgtAnchor.xCanvas,
            tgtAnchor.yCanvas,
        )
        val d = BezierEdge.distanceToPoint(
            px = pointerCanvasX,
            py = pointerCanvasY,
            x0 = srcAnchor.xCanvas,
            y0 = srcAnchor.yCanvas,
            c0x = cp0.first,
            c0y = cp0.second,
            c1x = cp1.first,
            c1y = cp1.second,
            x1 = tgtAnchor.xCanvas,
            y1 = tgtAnchor.yCanvas,
        )
        if (d < toleranceCanvas && d < bestDist) {
            bestId = c.id
            bestDist = d
        }
    }
    return bestId
}

private const val EDGE_HIT_TOLERANCE_DP = 12f

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
