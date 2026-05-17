package ai.agent.android.presentation.ui.pipeline.editor.canvas

import ai.agent.android.R
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.presentation.ui.pipeline.editor.core.CanvasTransform
import ai.agent.android.presentation.ui.pipeline.editor.core.ConnectionDraft
import ai.agent.android.presentation.ui.pipeline.editor.core.EditorState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var viewportSize by remember { mutableStateOf(IntPair(0, 0)) }

    val transformableState = rememberTransformableState { zoom, panChange, _ ->
        // Pinch: centred on the viewport mid by default; precise pinch midpoint
        // would require lower-level pointer reading. Acceptable trade-off for
        // a two-finger gesture per `node-specs.md`.
        if (zoom != 1f) {
            val anchorX = viewportSize.first / 2f
            val anchorY = viewportSize.second / 2f
            editor.transform = editor.transform.zoomedBy(zoom, anchorX, anchorY)
        }
        if (panChange != Offset.Zero) {
            editor.transform = editor.transform.panBy(panChange.x, panChange.y)
        }
    }

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
            .transformable(state = transformableState)
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
                    onLongPress = { offset ->
                        editor.quickAddAnchor = offset.x to offset.y
                    },
                )
            }
            // CRITICAL: `editor.transform` is intentionally NOT in the key list. It mutates
            // on every drag frame; including it here would cancel + restart `detectDragGestures`
            // every tick and stutter the pan to death. `editor` is a stable state holder, so
            // reading `editor.transform` inside the lambda gives the current value without
            // ever re-keying the modifier.
            .pointerInput(graph.id) {
                detectDragGestures(
                    onDrag = { _, drag ->
                        editor.transform = editor.transform.panBy(drag.x, drag.y)
                    },
                )
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
                onConnectionStart = { portLabel ->
                    val anchor = outboundPortAnchor(node, ports, portLabel, density)
                    // Pointer starts at the source port's canvas anchor (per-port, so the
                    // preview edge originates exactly at the dot the user grabbed); subsequent
                    // `onConnectionMove` deltas accumulate against this seed in canvas-space.
                    // sourcePortLabel is later forwarded to addConnection so the persisted
                    // ConnectionModel.label identifies which outbound port the user picked.
                    editor.connectionInProgress = ConnectionDraft(
                        sourceNodeId = node.id,
                        sourcePortLabel = portLabel,
                        pointerCanvasX = anchor.xCanvas,
                        pointerCanvasY = anchor.yCanvas,
                    )
                },
                onConnectionMove = { dxCanvas, dyCanvas ->
                    val draft = editor.connectionInProgress ?: return@EditorNode
                    editor.connectionInProgress = draft.copy(
                        pointerCanvasX = draft.pointerCanvasX + dxCanvas,
                        pointerCanvasY = draft.pointerCanvasY + dyCanvas,
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
