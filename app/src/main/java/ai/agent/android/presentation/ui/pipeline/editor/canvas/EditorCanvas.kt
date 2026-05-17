package ai.agent.android.presentation.ui.pipeline.editor.canvas

import ai.agent.android.R
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.presentation.ui.pipeline.editor.core.CanvasTransform
import ai.agent.android.presentation.ui.pipeline.editor.core.ConnectionDraft
import ai.agent.android.presentation.ui.pipeline.editor.core.EditorState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.knotwork.design.components.pipelineeditor.NodeError
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Top-level editor canvas. Hosts pan / pinch zoom, all node renderers, the edge layer,
 * and the radial quick-add menu.
 *
 * Responsibilities (kept here so the screen file stays a thin orchestrator):
 *  - Owns the `Box` modifier chain wiring pinch-to-zoom (`transformable`), one-finger
 *    canvas pan (`detectDragGestures`), and tap / long-press (`detectTapGestures`).
 *  - Renders the [EditorEdges] layer first (so it draws under nodes).
 *  - Renders one [EditorNode] per `PipelineGraph.nodes`.
 *  - Renders the [QuickAddRadialMenu] on top when an anchor is set.
 *
 * Node positions are persisted in canvas-space px; the [EditorState] transform projects
 * them to screen space at render time. Drag-to-move accumulates a screen-space delta
 * into a per-node session state, and commits the un-projected canvas delta to the
 * ViewModel via [onMoveNode] once the gesture settles.
 *
 * @param graph the current pipeline graph (from the ViewModel).
 * @param editor screen-local state (transform, selection, drafts).
 * @param activeRunningEdgeIds set of edge ids to animate in run-trace mode.
 * @param errorsByNodeId map of node-id → optional inline error for the catalog NodeCard.
 * @param reducedMotion reduced-motion flag — gates animations longer than `motionSm`.
 * @param onMoveNode invoked on drag-end with the committed canvas-space delta.
 * @param onAddNode invoked when a quick-add tile is picked.
 * @param onAddConnection invoked when a connection draft drops on a valid target node.
 * @param modifier optional layout modifier applied to the canvas root.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun EditorCanvas(
    graph: PipelineGraph,
    editor: EditorState,
    activeRunningEdgeIds: Set<String>,
    errorsByNodeId: Map<String, NodeError?>,
    reducedMotion: Boolean,
    onMoveNode: (nodeId: String, dxCanvas: Float, dyCanvas: Float) -> Unit,
    onAddNode: (type: NodeType, canvasX: Float, canvasY: Float) -> Unit,
    onAddConnection: (sourceNodeId: String, targetNodeId: String, label: String?) -> Unit,
    onOpenNodeConfig: (nodeId: String) -> Unit,
    onLongPressEdge: (connectionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var viewportSize by remember { mutableStateOf(IntPair(0, 0)) }

    // Per-node session deltas keep drag fluid without thrashing the VM each frame.
    val dragDeltas = remember { mutableStateOf<Map<String, Pair<Float, Float>>>(emptyMap()) }
    val nodesById = remember(graph) { graph.nodes.associateBy { it.id } }
    val nodesWithDrag = remember(graph, dragDeltas.value) {
        graph.nodes.map { node ->
            val delta = dragDeltas.value[node.id]
            if (delta == null) node else node.copy(x = node.x + delta.first, y = node.y + delta.second)
        }
    }
    val nodesByIdLive = remember(nodesWithDrag) { nodesWithDrag.associateBy { it.id } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds() // Prevent node graphics from spilling outside the canvas surface
            // into the EditorToolbar / bottom bars when nodes sit near the viewport edge.
            .background(KnotworkTheme.extended.surface1)
            .onSizeChanged { size -> viewportSize = IntPair(size.width, size.height) }
            // Capture canvas LayoutCoordinates into a non-state ref so EditorNode's
            // port-drag handlers can convert pointer positions via
            // `LayoutCoordinates.localPositionOf` without triggering a recomposition each
            // layout pass — see EditorState.canvasLayoutCoordinatesRef KDoc for the
            // recomposition-cycle / ANR motivation.
            .onGloballyPositioned { editor.canvasLayoutCoordinatesRef.value = it }
            .pointerInput(graph.id) {
                detectTapGestures(
                    onTap = { tapScreen ->
                        // Hit-test edges in canvas-space before falling through to "clear selection".
                        // Bezier edges are pure draw output (no per-edge pointerInput consumer), so
                        // the tap reaches the canvas; we project to canvas-space and ask the helper
                        // in EditorEdges which connection — if any — sits within the screen-space
                        // tolerance. Tap-on-edge selects it (toolbar Delete then removes it);
                        // tap-elsewhere clears selection.
                        val canvasX = editor.transform.screenToCanvasX(tapScreen.x)
                        val canvasY = editor.transform.screenToCanvasY(tapScreen.y)
                        val edgeId = hitTestEdge(
                            pointerCanvasX = canvasX,
                            pointerCanvasY = canvasY,
                            connections = graph.connections,
                            nodesById = nodesByIdLive,
                            transform = editor.transform,
                            density = density,
                        )
                        if (edgeId != null) {
                            editor.selectEdge(edgeId)
                            editor.multiSelectMode = false
                        } else {
                            editor.selection = emptySet()
                            editor.selectedEdgeId = null
                            editor.multiSelectMode = false
                        }
                    },
                    onLongPress = { tapScreen ->
                        // Long-press hit-tests edges first — if the press lands on an edge,
                        // forward to `onLongPressEdge` (screen shows a "Remove connection?"
                        // confirmation). Otherwise open the radial quick-add menu at the
                        // long-press point. Two discoverable paths to delete a connection
                        // (tap-select + toolbar 🗑, OR long-press + confirm) so users find at
                        // least one of them.
                        val canvasX = editor.transform.screenToCanvasX(tapScreen.x)
                        val canvasY = editor.transform.screenToCanvasY(tapScreen.y)
                        val edgeId = hitTestEdge(
                            pointerCanvasX = canvasX,
                            pointerCanvasY = canvasY,
                            connections = graph.connections,
                            nodesById = nodesByIdLive,
                            transform = editor.transform,
                            density = density,
                        )
                        if (edgeId != null) {
                            onLongPressEdge(edgeId)
                        } else {
                            editor.quickAddAnchor = tapScreen.x to tapScreen.y
                        }
                    },
                )
            }
            // Single transform-gesture handler covers 1-finger pan AND 2-finger pinch in
            // one detector — replaces the prior `transformable` + standalone
            // `detectDragGestures` pair, where the drag detector consumed the first
            // pointer before `transformable` could see the second finger and route to
            // zoom. `panZoomLock = false` lets pan and zoom compose freely on the same
            // gesture. We also get the true pinch `centroid` (vs. the prior
            // viewport-centre approximation) so zoom anchors precisely under the fingers.
            .pointerInput(graph.id) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                    if (zoom != 1f) {
                        editor.transform = editor.transform.zoomedBy(zoom, centroid.x, centroid.y)
                    }
                    if (pan != Offset.Zero) {
                        editor.transform = editor.transform.panBy(pan.x, pan.y)
                    }
                }
            },
    ) {
        val draftDraw = editor.connectionInProgress?.let { draft ->
            val source = nodesByIdLive[draft.sourceNodeId] ?: return@let null
            val anchor = outboundPortAnchor(source, portsFor(source), draft.sourcePortLabel, density)
            // The draft is stored in canvas-space (see ConnectionDraft KDoc); project both
            // anchor and live pointer through `transform` here so the draw layer stays in
            // screen-space and doesn't need to know about CanvasTransform.
            ConnectionDraftDrawData(
                sourceScreen = Offset(
                    editor.transform.canvasToScreenX(anchor.xCanvas),
                    editor.transform.canvasToScreenY(anchor.yCanvas),
                ),
                pointerScreen = Offset(
                    editor.transform.canvasToScreenX(draft.pointerCanvasX),
                    editor.transform.canvasToScreenY(draft.pointerCanvasY),
                ),
            )
        }
        EditorEdges(
            connections = graph.connections,
            nodesById = nodesByIdLive,
            transform = editor.transform,
            connectionDraft = draftDraw,
            runningEdgeIds = activeRunningEdgeIds,
            selectedEdgeId = editor.selectedEdgeId,
            reducedMotion = reducedMotion,
        )

        nodesWithDrag.forEach { node ->
            val originalNode = nodesById[node.id] ?: node
            val ports = portsFor(node)
            EditorNode(
                node = node,
                transform = editor.transform,
                selected = node.id in editor.selection && !editor.multiSelectMode,
                multiSelected = node.id in editor.selection && editor.multiSelectMode,
                running = editor.isRunning && editor.activeRunningNodeId == node.id,
                error = errorsByNodeId[node.id],
                ports = ports,
                reducedMotion = reducedMotion,
                onSelect = { editor.toggleSelection(node.id) },
                onOpenConfig = { onOpenNodeConfig(node.id) },
                onLongPress = {
                    editor.multiSelectMode = true
                    editor.toggleSelection(node.id)
                },
                onDrag = { dxCanvas, dyCanvas ->
                    // EditorNode emits canvas-space deltas (its pointerInput is wrapped by a
                    // `graphicsLayer` whose scale already includes `transform.scale`, so the
                    // dragAmount Compose delivers is in the node's un-scaled layout space —
                    // i.e. canvas pixels). Dividing by `transform.scale` again here would
                    // double-scale the delta and make the node lag behind the finger when
                    // zoomed in. Accumulate the canvas-space delta directly.
                    val prev = dragDeltas.value[node.id] ?: (0f to 0f)
                    dragDeltas.value = dragDeltas.value +
                        (node.id to (prev.first + dxCanvas to prev.second + dyCanvas))
                },
                onDragEnd = {
                    val delta = dragDeltas.value[node.id]
                    if (delta != null) {
                        // Snap to grid on commit.
                        val targetX = CanvasTransform.snapToGrid(originalNode.x + delta.first)
                        val targetY = CanvasTransform.snapToGrid(originalNode.y + delta.second)
                        val snappedDx = targetX - originalNode.x
                        val snappedDy = targetY - originalNode.y
                        dragDeltas.value = dragDeltas.value - node.id
                        if (snappedDx != 0f || snappedDy != 0f) {
                            onMoveNode(node.id, snappedDx, snappedDy)
                        }
                    }
                },
                onConnectionStart = { portLabel, pointerBoxX, pointerBoxY ->
                    // pointerBoxX/Y arrive as the absolute pointer position in canvas-Box-local
                    // coords (from `LayoutCoordinates.localPositionOf`), so project once through
                    // the inverse canvas transform to land in canvas-space.
                    editor.connectionInProgress = ConnectionDraft(
                        sourceNodeId = node.id,
                        sourcePortLabel = portLabel,
                        pointerCanvasX = editor.transform.screenToCanvasX(pointerBoxX),
                        pointerCanvasY = editor.transform.screenToCanvasY(pointerBoxY),
                    )
                },
                onConnectionMove = { pointerBoxX, pointerBoxY ->
                    val draft = editor.connectionInProgress ?: return@EditorNode
                    editor.connectionInProgress = draft.copy(
                        pointerCanvasX = editor.transform.screenToCanvasX(pointerBoxX),
                        pointerCanvasY = editor.transform.screenToCanvasY(pointerBoxY),
                    )
                },
                onConnectionEnd = {
                    val draft = editor.connectionInProgress
                    editor.connectionInProgress = null
                    if (draft != null) {
                        val target = hitTestInputPort(
                            pointerCanvasX = draft.pointerCanvasX,
                            pointerCanvasY = draft.pointerCanvasY,
                            nodes = nodesByIdLive.values.toList(),
                            transform = editor.transform,
                            density = density,
                        )
                        if (target != null && target.id != draft.sourceNodeId) {
                            // Forward the source-port label so multi-out nodes (IF / Queue /
                            // Eval / IntentRouter) persist which port the user dragged from.
                            onAddConnection(
                                draft.sourceNodeId,
                                target.id,
                                draft.sourcePortLabel.ifBlank { null },
                            )
                        }
                    }
                },
                canvasLayoutCoordinatesRef = editor.canvasLayoutCoordinatesRef,
            )
        }

        if (graph.nodes.isEmpty()) {
            Box(
                modifier = Modifier.align(Alignment.Center).padding(KnotworkTheme.spacing.sp6),
            ) {
                Text(
                    text = stringResource(R.string.pipeline_editor_empty_subtitle),
                    style = KnotworkTextStyles.BodyBase,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }

        val anchor = editor.quickAddAnchor
        if (anchor != null) {
            QuickAddRadialMenu(
                screenAnchorX = anchor.first,
                screenAnchorY = anchor.second,
                onPick = { type ->
                    val canvasX = editor.transform.screenToCanvasX(anchor.first)
                    val canvasY = editor.transform.screenToCanvasY(anchor.second)
                    editor.quickAddAnchor = null
                    onAddNode(type, CanvasTransform.snapToGrid(canvasX), CanvasTransform.snapToGrid(canvasY))
                },
                onDismiss = { editor.quickAddAnchor = null },
            )
        }
    }

    LaunchedEffect(reducedMotion) {
        // Touchpoint reserved for future a11y signals; the dependency keys the effect
        // to motion-flag changes so animations recreate cleanly across system toggles.
    }
}

private typealias IntPair = Pair<Int, Int>

/**
 * Returns the node whose inbound port is closest to a **canvas-space** point — used to
 * decide which node a release-from-port gesture lands on. Returns `null` when no node is
 * within the hit tolerance of [INBOUND_HIT_DP].
 *
 * The tolerance is supplied in dp (a screen-space measure) and is divided by the current
 * canvas scale so that the user always gets the same visual tolerance regardless of zoom.
 */
private fun hitTestInputPort(
    pointerCanvasX: Float,
    pointerCanvasY: Float,
    nodes: List<NodeModel>,
    transform: CanvasTransform,
    density: androidx.compose.ui.unit.Density,
): NodeModel? {
    val toleranceScreenPx = with(density) { INBOUND_HIT_DP.dp.toPx() }
    val toleranceCanvas = toleranceScreenPx / transform.scale
    var best: NodeModel? = null
    var bestDist = Float.MAX_VALUE
    nodes.forEach { node ->
        val anchor = inboundPortAnchor(node, density)
        val dx = anchor.xCanvas - pointerCanvasX
        val dy = anchor.yCanvas - pointerCanvasY
        val dist = kotlin.math.hypot(dx, dy)
        if (dist < toleranceCanvas && dist < bestDist) {
            best = node
            bestDist = dist
        }
    }
    return best
}

private const val INBOUND_HIT_DP = 32f

// Surface a stable name for the unused Color import (used elsewhere in this module).
@Suppress("unused")
private val transparentReference: Color = Color.Transparent
