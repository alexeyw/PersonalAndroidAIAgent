package app.knotwork.android.domain.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WorkspaceGlob], pinning the documented glob semantics:
 * `*` stays within a path segment, `**` crosses directories, `?` matches a
 * single non-separator character, and everything else is literal.
 */
class WorkspaceGlobTest {

    @Test
    fun `given single-star glob when matching then stays within one segment`() {
        assertTrue(WorkspaceGlob.matches("*.md", "notes.md"))
        assertFalse(WorkspaceGlob.matches("*.md", "reports/notes.md"))
    }

    @Test
    fun `given single-star glob when extension differs then no match`() {
        assertFalse(WorkspaceGlob.matches("*.md", "notes.txt"))
    }

    @Test
    fun `given double-star suffix when matching then crosses directories`() {
        assertTrue(WorkspaceGlob.matches("reports/**", "reports/a.md"))
        assertTrue(WorkspaceGlob.matches("reports/**", "reports/2026/q1/b.md"))
        assertFalse(WorkspaceGlob.matches("reports/**", "notes.md"))
    }

    @Test
    fun `given double-star suffix when path is the bare prefix then no match`() {
        // `reports/**` requires the separator, so the directory name alone does not match.
        assertFalse(WorkspaceGlob.matches("reports/**", "reports"))
    }

    @Test
    fun `given leading double-star slash when matching then optional leading segments`() {
        assertTrue(WorkspaceGlob.matches("**/*.md", "a.md"))
        assertTrue(WorkspaceGlob.matches("**/*.md", "sub/a.md"))
        assertTrue(WorkspaceGlob.matches("**/*.md", "sub/deeper/a.md"))
        assertFalse(WorkspaceGlob.matches("**/*.md", "a.txt"))
    }

    @Test
    fun `given question mark when matching then single non-separator char`() {
        assertTrue(WorkspaceGlob.matches("file?.md", "file1.md"))
        assertFalse(WorkspaceGlob.matches("file?.md", "file.md"))
        assertFalse(WorkspaceGlob.matches("a?b", "a/b"))
    }

    @Test
    fun `given regex metacharacters when matching then treated as literals`() {
        assertTrue(WorkspaceGlob.matches("report.(final).md", "report.(final).md"))
        // The literal dot must not behave like a regex wildcard.
        assertFalse(WorkspaceGlob.matches("a.b", "axb"))
    }

    @Test
    fun `given full path glob when matching then anchored end to end`() {
        assertTrue(WorkspaceGlob.matches("reports/q1.md", "reports/q1.md"))
        assertFalse(WorkspaceGlob.matches("reports/q1.md", "reports/q1.md.bak"))
        assertFalse(WorkspaceGlob.matches("q1.md", "reports/q1.md"))
    }

    @Test
    fun `given bare double-star when matching then matches everything`() {
        assertTrue(WorkspaceGlob.matches("**", "a.md"))
        assertTrue(WorkspaceGlob.matches("**", "deep/nested/path/file.json"))
    }
}
