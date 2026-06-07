package app.knotwork.android.presentation.ui.pipeline.editor.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.LayoutCoordinates
import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.design.components.pipelineeditor.NodeConfig

/**
 * Local state owned by the pipeline editor screen — everything that does NOT need to
 * survive process death and does NOT belong in the ViewModel (gestures, transient
 * selection, in-flight connection drafts, undo / redo stack, run-trace tick).
 *
 * `OrchestratorViewModel` continues to own canonical graph state (nodes, connections,
 * persistence). The editor maintains [EditorState] separately because driving the
 * canvas gesture stream through Hilt + StateFlow would otherwise pay a recomposition
 * tax for every pointer event (which can hit 120 Hz on modern devices).
 *
 * Use [rememberEditorState] from a `@Composable` to get one bound to the current
 * composition. Pure logic lives on this class so unit tests can construct it
 * directly without entering a composition.
 */
@Stable
class EditorState(undoCapacity: Int = EditorUndoRedo.DEFAULT_CAPACITY) {

    /** Pan + zoom of the canvas viewport. */
    var transform: CanvasTransform by mutableStateOf(CanvasTransform())

    /** Currently selected node ids (single- or multi-select). */
    var selection: Set<String> by mutableStateOf(emptySet())

    /**
     * Currently selected connection (edge) id, or `null` when no edge is selected.
     *
     * Node selection and edge selection are mutually exclusive — selecting an edge clears
     * `selection`, and selecting a node clears this. The editor toolbar's Delete button
     * removes whichever one is non-empty.
     */
    var selectedEdgeId: String? by mutableStateOf(null)

    /** `true` when the user is in multi-select mode (long-press entry). */
    var multiSelectMode: Boolean by mutableStateOf(false)

    /** In-flight connection draft; `null` while the canvas is idle. */
    var connectionInProgress: ConnectionDraft? by mutableStateOf(null)

    /** Canvas-space anchor for an open radial quick-add menu; `null` when closed. */
    var quickAddAnchor: Pair<Float, Float>? by mutableStateOf(null)

    /** Run-trace state — currently active node id while the pipeline is running, else `null`. */
    var activeRunningNodeId: String? by mutableStateOf(null)

    /** `true` while a pipeline run is in progress; toggles the bottom bar between validation and trace. */
    var isRunning: Boolean by mutableStateOf(false)

    /**
     * `true` when the mini-map overlay is visible. Toggled from the overflow
     * menu. While open the toolbar subtitle switches to the overview wording
     * (`Overview · 0.42× · 11 nodes`) and the canvas grants the mini-map's
     * bottom-right anchor first-class real estate.
     */
    var miniMapOpen: Boolean by mutableStateOf(false)

    /**
     * `true` when the canvas renders the dot grid background. The visual grid
     * is on initially because the user expects to
     * see snap-to-grid feedback while dragging. Toggled from the overflow menu
     * so power users can hide it for a clean visual.
     */
    var gridVisible: Boolean by mutableStateOf(true)

    /** Node currently open in the [NodeConfigSheet], or `null` when no sheet is up. */
    var configuringNodeId: String? by mutableStateOf(null)

    /** Working [NodeConfig] for the open sheet — mirrors edits before commit. */
    var workingConfig: NodeConfig? by mutableStateOf(null)

    /**
     * Working [NodeContextConfig] tracked alongside [workingConfig] while the
     * sheet is open. The catalog `NodeConfigSheet` doesn't model context flags
     * (those are domain-level), so the production sheet wires its own
     * `NodeContextConfigSection` via the `extraSection` slot and mirrors the
     * checkbox state here. Reset to `null` on sheet close.
     */
    var workingContextConfig: NodeContextConfig? by mutableStateOf(null)

    /** Editor's undo/redo stack — bounded to the screen lifetime, never persisted. */
    val undoRedo: EditorUndoRedo = EditorUndoRedo(capacity = undoCapacity)

    /**
     * Clipboard for Copy / Paste node. Holds a snapshot of every node copied
     * during the active screen session (cleared on screen destroy). Each entry
     * captures the source node verbatim — IDs are regenerated on paste so
     * duplicates can coexist with the originals.
     */
    var clipboard: List<NodeModel> by mutableStateOf(emptyList())

    /** `true` while the canvas Find-bar is visible. Toggled from the overflow menu. */
    var searchOpen: Boolean by mutableStateOf(false)

    /**
     * Current search query — empty string means "no matches; render everyone
     * normally". Non-empty drives the [searchHighlightIds] derived set and
     * (when the user submits) a `transform.centeredOn` jump to the first match.
     */
    var searchQuery: String by mutableStateOf("")

    /**
     * Holds the canvas Box's `LayoutCoordinates`, captured via
     * `Modifier.onGloballyPositioned`. Intentionally a **plain non-state ref**
     * (not a `mutableStateOf`): `LayoutCoordinates` doesn't override `equals`, so every
     * layout-pass callback produces a fresh-identity instance that would mark the state
     * as changed even when the position is the same. With every `EditorNode` reading
     * the state by parameter, that propagates a re-layout pump and the main thread
     * loops itself into an ANR.
     *
     * Reads happen just-in-time inside port-drag handlers via [CoordinatesRef.value];
     * writes from `onGloballyPositioned` don't notify Compose. The same instance always
     * reflects the current layout, so just-in-time reads are always correct.
     */
    val canvasLayoutCoordinatesRef: CoordinatesRef = CoordinatesRef()

    /**
     * Clears selection + connection draft + radial-menu anchor. Used by Escape / back-press
     * before navigating away, and by the canvas-level tap handler.
     */
    fun clearTransient() {
        selection = emptySet()
        selectedEdgeId = null
        multiSelectMode = false
        connectionInProgress = null
        quickAddAnchor = null
    }

    /**
     * Toggles [nodeId] in [selection]. In single-select mode, replaces the entire
     * selection; in multi-select mode, toggles membership. Selecting a node always
     * clears any edge selection (the two are mutually exclusive).
     */
    fun toggleSelection(nodeId: String) {
        selectedEdgeId = null
        selection = if (multiSelectMode) {
            if (nodeId in selection) selection - nodeId else selection + nodeId
        } else {
            setOf(nodeId)
        }
    }

    /**
     * Selects a single connection (edge). Clears node selection; multi-select mode is left
     * alone — exiting multi-select via long-press cancel still works.
     */
    fun selectEdge(edgeId: String?) {
        selectedEdgeId = edgeId
        if (edgeId != null) selection = emptySet()
    }
}

/**
 * Description of a connection being drawn from an output port. The pointer is tracked in
 * **canvas-space** (not screen space): both the source-port anchor and the pointer live in
 * the same un-projected coordinate system used by `NodeModel.x / y`. The edge layer projects
 * through [CanvasTransform] at draw time; hit-testing for the connection's drop target also
 * runs in canvas-space.
 *
 * Keeping the draft in canvas-space avoids mixing coordinate systems while the user pans /
 * zooms the canvas mid-drag and removes a class of double-scaling bugs that crept in when
 * the draft used screen-space.
 *
 * @property sourceNodeId id of the originating node.
 * @property sourcePortLabel label of the originating outbound port (empty for single-out nodes).
 * @property pointerCanvasX live canvas-X of the pointer.
 * @property pointerCanvasY live canvas-Y of the pointer.
 */
data class ConnectionDraft(
    val sourceNodeId: String,
    val sourcePortLabel: String,
    val pointerCanvasX: Float,
    val pointerCanvasY: Float,
)

/**
 * Composable factory that builds (and remembers) an [EditorState] keyed to the current
 * composition.
 */
@Composable
fun rememberEditorState(): EditorState = remember { EditorState() }

/**
 * Mutable non-state container for a [LayoutCoordinates] reference. Writes don't notify
 * Compose; reads don't subscribe. Used by [EditorState.canvasLayoutCoordinatesRef] —
 * see that property's KDoc for why a plain ref beats `mutableStateOf` for this value.
 */
class CoordinatesRef {
    /** Current canvas `LayoutCoordinates`. `null` before the first layout pass. */
    var value: LayoutCoordinates? = null
}
