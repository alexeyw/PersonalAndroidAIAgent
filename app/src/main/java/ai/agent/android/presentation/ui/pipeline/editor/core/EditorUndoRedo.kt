package ai.agent.android.presentation.ui.pipeline.editor.core

import ai.agent.android.domain.models.PipelineGraph

/**
 * Bounded undo / redo stack over [PipelineGraph] snapshots.
 *
 * Behaviour:
 *  - [push] records the current state and clears the redo branch.
 *  - [undo] pops the most recent snapshot back into the live state.
 *  - [redo] re-applies a previously undone snapshot.
 *  - The stack is capped at [capacity] entries so a long editing session does not
 *    accumulate unbounded snapshots; oldest entries are dropped first.
 *
 * Pure Kotlin — JVM-testable. The editor wraps every graph mutation through
 * `push(previous)` before applying the mutation; reverting walks the inverse path.
 *
 * Not thread-safe; the editor always invokes from the UI coroutine.
 *
 * @property capacity maximum number of snapshots retained on either side of the head.
 */
class EditorUndoRedo(private val capacity: Int = DEFAULT_CAPACITY) {

    private val undoStack: ArrayDeque<PipelineGraph> = ArrayDeque()
    private val redoStack: ArrayDeque<PipelineGraph> = ArrayDeque()

    /**
     * `true` when [undo] would return a snapshot.
     */
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /**
     * `true` when [redo] would return a snapshot.
     */
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Records [snapshot] on the undo stack. Clears the redo branch (standard undo/redo
     * semantics — a new action invalidates the redo lineage).
     */
    fun push(snapshot: PipelineGraph) {
        if (undoStack.size >= capacity) undoStack.removeFirst()
        undoStack.addLast(snapshot)
        redoStack.clear()
    }

    /**
     * Pops the most recent snapshot off the undo stack and pushes [current] onto the
     * redo stack so the caller can step back forward.
     *
     * @param current the live graph displayed in the editor at call time.
     * @return the snapshot to apply, or `null` when the undo stack is empty.
     */
    fun undo(current: PipelineGraph): PipelineGraph? {
        val previous = undoStack.removeLastOrNull() ?: return null
        if (redoStack.size >= capacity) redoStack.removeFirst()
        redoStack.addLast(current)
        return previous
    }

    /**
     * Pops the most recent snapshot off the redo stack and pushes [current] onto the
     * undo stack.
     *
     * @param current the live graph displayed in the editor at call time.
     * @return the snapshot to apply, or `null` when the redo stack is empty.
     */
    fun redo(current: PipelineGraph): PipelineGraph? {
        val next = redoStack.removeLastOrNull() ?: return null
        if (undoStack.size >= capacity) undoStack.removeFirst()
        undoStack.addLast(current)
        return next
    }

    /**
     * Clears both stacks. Used when the editor loads a fresh pipeline so undo history
     * does not bleed across pipelines.
     */
    fun reset() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        /** Maximum snapshots retained on either side of the head — `node-specs.md` §editor. */
        const val DEFAULT_CAPACITY: Int = 50
    }
}
