package app.knotwork.android.presentation.ui.pipeline.editor.core

import app.knotwork.android.domain.models.PipelineGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUndoRedoTest {

    private fun pipeline(name: String): PipelineGraph = PipelineGraph(id = name, name = name)

    @Test
    fun `given empty stack when undo then null and canUndo is false`() {
        val stack = EditorUndoRedo()
        assertFalse(stack.canUndo)
        assertNull(stack.undo(pipeline("a")))
    }

    @Test
    fun `given pushed snapshot when undo then returns it`() {
        val stack = EditorUndoRedo()
        val first = pipeline("v1")
        val second = pipeline("v2")
        stack.push(first)
        assertTrue(stack.canUndo)
        assertEquals(first, stack.undo(current = second))
        assertFalse(stack.canUndo)
        assertTrue(stack.canRedo)
    }

    @Test
    fun `given undone snapshot when redo then returns it`() {
        val stack = EditorUndoRedo()
        val first = pipeline("v1")
        val second = pipeline("v2")
        stack.push(first)
        stack.undo(current = second)
        assertEquals(second, stack.redo(current = first))
        assertTrue(stack.canUndo)
        assertFalse(stack.canRedo)
    }

    @Test
    fun `given new push after undo when redo then redo is cleared`() {
        val stack = EditorUndoRedo()
        val a = pipeline("a")
        val b = pipeline("b")
        val c = pipeline("c")
        stack.push(a)
        stack.undo(b)
        assertTrue(stack.canRedo)
        stack.push(c)
        assertFalse(stack.canRedo)
    }

    @Test
    fun `given push past capacity when push then oldest is dropped`() {
        val stack = EditorUndoRedo(capacity = 3)
        val v1 = pipeline("v1")
        val v2 = pipeline("v2")
        val v3 = pipeline("v3")
        val v4 = pipeline("v4")
        stack.push(v1)
        stack.push(v2)
        stack.push(v3)
        stack.push(v4)
        // Undo three times: should yield v4, v3, v2 in order. v1 was dropped.
        assertEquals(v4, stack.undo(pipeline("now")))
        assertEquals(v3, stack.undo(pipeline("now")))
        assertEquals(v2, stack.undo(pipeline("now")))
        assertFalse(stack.canUndo)
    }

    @Test
    fun `given reset when called then both stacks empty`() {
        val stack = EditorUndoRedo()
        stack.push(pipeline("a"))
        stack.undo(pipeline("b"))
        stack.reset()
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)
    }
}
