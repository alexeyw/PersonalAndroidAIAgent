package ai.agent.android.presentation.ui.pipeline.editor.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    /** Node currently open in the [NodeConfigSheet], or `null` when no sheet is up. */
    var configuringNodeId: String? by mutableStateOf(null)

    /** Working [NodeConfig] for the open sheet — mirrors edits before commit. */
    var workingConfig: NodeConfig? by mutableStateOf(null)

    /** Editor's undo/redo stack — bounded to the screen lifetime, never persisted. */
    val undoRedo: EditorUndoRedo = EditorUndoRedo(capacity = undoCapacity)

    /**
     * Clears selection + connection draft + radial-menu anchor. Used by Escape / back-press
     * before navigating away, and by the canvas-level tap handler.
     */
    fun clearTransient() {
        selection = emptySet()
        multiSelectMode = false
        connectionInProgress = null
        quickAddAnchor = null
    }

    /**
     * Toggles [nodeId] in [selection]. In single-select mode, replaces the entire
     * selection; in multi-select mode, toggles membership.
     */
    fun toggleSelection(nodeId: String) {
        selection = if (multiSelectMode) {
            if (nodeId in selection) selection - nodeId else selection + nodeId
        } else {
            setOf(nodeId)
        }
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
