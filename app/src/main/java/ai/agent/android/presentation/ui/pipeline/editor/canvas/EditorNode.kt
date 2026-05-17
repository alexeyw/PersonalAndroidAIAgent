package ai.agent.android.presentation.ui.pipeline.editor.canvas

import ai.agent.android.domain.models.NodeModel
import ai.agent.android.presentation.ui.pipeline.editor.config.NodeTypeMapper
import ai.agent.android.presentation.ui.pipeline.editor.core.CanvasTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.knotwork.design.components.pipelineeditor.NodeCard
import app.knotwork.design.components.pipelineeditor.NodeError
import app.knotwork.design.components.pipelineeditor.NodePorts
import app.knotwork.design.theme.KnotworkTheme
import androidx.compose.animation.core.Animatable as AnimFloat

private const val PORT_HIT_TARGET_DP = 24f
private const val DRAG_PICKUP_SCALE = 1.04f
private const val DRAG_PICKUP_DURATION_MS = 100

/**
 * Wraps the catalog [NodeCard] with editor-only behaviour: drag-to-move, tap-to-select,
 * long-press multi-select, and a hit-target overlay on the outbound port that hands off
 * to connection mode.
 *
 * Position is rendered through `graphicsLayer.translationX / Y` so a node moving never
 * triggers a re-measure of the canvas surface (`animations.md` §Performance notes). The
 * pickup scale animation (1.00 → 1.04 over 100 ms, ease-out) and the release settle
 * (`spring(.7f, 15f)`) live on local [AnimFloat] handles.
 *
 * @param node the node to render.
 * @param transform pan / zoom applied to the canvas.
 * @param selected `true` when the node is the single-selected entry.
 * @param multiSelected `true` when the node is part of a multi-select set.
 * @param running `true` when the node is the currently-running step of a pipeline trace.
 * @param error optional validation / runtime error surface.
 * @param ports outbound / inbound port layout.
 * @param reducedMotion reduced-motion flag — pickup / release animations collapse to instant.
 * @param onSelect invoked on tap.
 * @param onLongPress invoked on long-press (multi-select entry).
 * @param onDrag invoked while the user is dragging the node; receives **canvas-space**
 * deltas (the per-event `dragAmount` from `detectDragGestures`, which Compose delivers in
 * the pointerInput modifier's local layout space — that already accounts for the
 * `graphicsLayer` scale wrapping this composable, so the deltas line up with canvas
 * coordinates without a second division by `transform.scale`).
 * @param onDragEnd invoked once the pickup-release animation has settled; caller commits
 * the final canvas-space position to the ViewModel.
 * @param onConnectionStart invoked when the user starts dragging from the outbound port.
 * The caller switches into connection-draft mode.
 * @param onConnectionMove receives per-event **canvas-space** deltas for the connection
 * pointer (same coordinate-space rationale as [onDrag]). The caller accumulates them into
 * `EditorState.connectionInProgress.pointerCanvas{X,Y}`.
 * @param onConnectionEnd invoked on release. The caller hit-tests the accumulated
 * canvas-space pointer position from `EditorState.connectionInProgress` directly — no
 * coordinate parameters are needed here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList") // The canvas node needs every input as a single seam.
internal fun EditorNode(
    node: NodeModel,
    transform: CanvasTransform,
    selected: Boolean,
    multiSelected: Boolean,
    running: Boolean,
    error: NodeError?,
    ports: NodePorts,
    reducedMotion: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (dxCanvas: Float, dyCanvas: Float) -> Unit,
    onDragEnd: () -> Unit,
    onConnectionStart: () -> Unit,
    onConnectionMove: (dxCanvas: Float, dyCanvas: Float) -> Unit,
    onConnectionEnd: () -> Unit,
) {
    val scale = remember { AnimFloat(1f) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging) {
        val target = if (isDragging) DRAG_PICKUP_SCALE else 1f
        if (reducedMotion) {
            scale.snapTo(target)
        } else if (isDragging) {
            scale.animateTo(target, tween(DRAG_PICKUP_DURATION_MS))
        } else {
            scale.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    val screenX = transform.canvasToScreenX(node.x)
    val screenY = transform.canvasToScreenY(node.y)
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = screenX
                translationY = screenY
                scaleX = scale.value * transform.scale
                scaleY = scale.value * transform.scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
                onLongClick = onLongPress,
            )
            .pointerInput(node.id) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        onDragEnd()
                    },
                    onDrag = { _, drag ->
                        onDrag(drag.x, drag.y)
                    },
                )
            },
    ) {
        NodeCard(
            type = NodeTypeMapper.toCatalog(node.type),
            title = node.label.ifBlank { node.type.name },
            subtitle = null,
            selected = selected,
            error = error,
            running = running,
            multiSelected = multiSelected,
            ports = ports,
        )
        // Output-port hit target — invisible 24 dp circle straddling the node's bottom edge
        // so it lines up with the catalog NodeCard's port dot (which protrudes 6 dp past
        // the card). `Modifier.padding` rejects negative values, so we shift the sized box
        // down by half its diameter via `Modifier.offset` (which accepts negative offsets);
        // BoxScope.align places the box's bottom edge at the parent's bottom, then offset
        // pushes it down 12 dp so half the hit-target overflows the node.
        //
        // Routed to its own pointerInput so the gesture arbitration is unambiguous:
        // dragging from inside this Box always means "start a connection", never "move the node".
        if (ports.outbound.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(PORT_HIT_TARGET_DP.dp)
                    .offset(y = (PORT_HIT_TARGET_DP / 2).dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = { onConnectionStart() },
                            // Emit canvas-space deltas only. Using `change.position` (absolute
                            // pointer in the port-Box's local coords) and adding it to the
                            // node's screen-space anchor would mix coordinate systems and
                            // make the preview edge snap to the node's top-left on drag start;
                            // accumulating `dragAmount` keeps the pointer in canvas-space and
                            // the canvas projects it back to screen for drawing.
                            onDrag = { _, dragAmount -> onConnectionMove(dragAmount.x, dragAmount.y) },
                            onDragEnd = { onConnectionEnd() },
                            onDragCancel = { onConnectionEnd() },
                        )
                    },
            )
        }
    }
}

/**
 * Builds [NodePorts] for [node]. For [ai.agent.android.domain.models.NodeType.INTENT_ROUTER]
 * the class names come from the persisted `NodeConfig` payload (via the codec); for
 * [ai.agent.android.domain.models.NodeType.EVALUATION] the retry-port is derived from
 * the same payload.
 *
 * Pure helper — extracted so the snapshot tests can pass a deterministic ports object.
 */
internal fun portsForNode(node: NodeModel, ports: NodePorts? = null): NodePorts =
    ports ?: NodePorts.forType(type = NodeTypeMapper.toCatalog(node.type))

// Keep the KnotworkTheme import alive for callers that inline tokens against the same theme.
@Suppress("unused")
private val themeReferenceMarker = KnotworkTheme
