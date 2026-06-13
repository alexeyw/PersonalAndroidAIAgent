package app.knotwork.android.domain.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WorkspaceTextEdit].
 *
 * Pins the anchored find-replace contract that backs `edit_file`: exactly one
 * occurrence is required (zero → not found, many → ambiguous with the count),
 * matching is literal and non-overlapping, an empty replacement deletes the
 * fragment, and replacement edits only the single matched span.
 */
class WorkspaceTextEditTest {

    @Test
    fun `given unique anchor when apply then replaces single occurrence`() {
        val outcome = WorkspaceTextEdit.apply("hello world", "world", "there")

        assertEquals(WorkspaceTextEdit.Outcome.Replaced("hello there"), outcome)
    }

    @Test
    fun `given anchor absent when apply then reports not found`() {
        val outcome = WorkspaceTextEdit.apply("hello world", "missing", "x")

        assertEquals(WorkspaceTextEdit.Outcome.AnchorNotFound, outcome)
    }

    @Test
    fun `given anchor appearing twice when apply then reports not unique with count`() {
        val outcome = WorkspaceTextEdit.apply("a-a-a", "a", "b")

        assertEquals(WorkspaceTextEdit.Outcome.AnchorNotUnique(3), outcome)
    }

    @Test
    fun `given overlapping pattern when apply then counts non-overlapping occurrences`() {
        // "aaaa" contains "aa" twice when counted non-overlapping (indices 0 and 2),
        // not three times — the scan advances past each whole match.
        val outcome = WorkspaceTextEdit.apply("aaaa", "aa", "b")

        assertEquals(WorkspaceTextEdit.Outcome.AnchorNotUnique(2), outcome)
    }

    @Test
    fun `given empty newText when apply then deletes the matched fragment`() {
        val outcome = WorkspaceTextEdit.apply("keep [drop] keep", "[drop] ", "")

        assertEquals(WorkspaceTextEdit.Outcome.Replaced("keep keep"), outcome)
    }

    @Test
    fun `given multiline anchor when apply then replaces across lines`() {
        val content = "line1\nline2\nline3\n"
        val outcome = WorkspaceTextEdit.apply(content, "line2\nline3", "merged")

        assertEquals(WorkspaceTextEdit.Outcome.Replaced("line1\nmerged\n"), outcome)
    }

    @Test
    fun `given anchor with regex metacharacters when apply then matches literally`() {
        // The anchor contains '.' and '(' which would be regex metacharacters if
        // interpreted; matching must be literal so only the exact text is found.
        val outcome = WorkspaceTextEdit.apply("a.b(c)", "a.b(c)", "done")

        assertEquals(WorkspaceTextEdit.Outcome.Replaced("done"), outcome)
    }

    @Test
    fun `given replacement containing the anchor when apply then does not loop`() {
        // Replacing with text that itself contains the anchor must not re-trigger:
        // replaceFirst rewrites only the original span.
        val outcome = WorkspaceTextEdit.apply("x", "x", "xx")

        assertTrue(outcome is WorkspaceTextEdit.Outcome.Replaced)
        assertEquals("xx", (outcome as WorkspaceTextEdit.Outcome.Replaced).newContent)
    }
}
