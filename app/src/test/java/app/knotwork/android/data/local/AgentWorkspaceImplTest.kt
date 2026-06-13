package app.knotwork.android.data.local

import android.content.Context
import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Verifies the [AgentWorkspaceImpl] foundation: the path-traversal containment
 * boundary (the single canonicalisation gate), the per-file and total-size
 * quotas at their exact boundaries, the text/binary read distinction, and the
 * overwrite semantics — each surfaced as a typed [WorkspaceError].
 */
class AgentWorkspaceImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk()
        every { context.filesDir } returns tempFolder.root
    }

    // region helpers

    private fun workspaceWith(
        maxFileSize: Long = DEFAULT_FILE_SIZE,
        maxTotal: Long = DEFAULT_TOTAL,
    ): AgentWorkspaceImpl {
        val settings = mockk<SettingsRepository>()
        every { settings.workspaceMaxFileSizeBytes } returns flowOf(maxFileSize)
        every { settings.workspaceMaxTotalBytes } returns flowOf(maxTotal)
        return AgentWorkspaceImpl(context, settings)
    }

    /** The on-disk workspace root the implementation manages. */
    private fun workspaceRoot() = File(tempFolder.root, "agent_workspace")

    /** Writes a raw file directly under the workspace root, bypassing the workspace API. */
    private fun putRawFile(relativePath: String, bytes: ByteArray) {
        val file = File(workspaceRoot(), relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    private fun <T> assertSuccess(result: WorkspaceResult<T>): T {
        assertTrue("expected Success but was $result", result is WorkspaceResult.Success)
        return (result as WorkspaceResult.Success).value
    }

    private fun assertFailure(result: WorkspaceResult<*>, expected: WorkspaceError) {
        assertTrue("expected Failure($expected) but was $result", result is WorkspaceResult.Failure)
        assertEquals(expected, (result as WorkspaceResult.Failure).error)
    }

    // endregion

    @Test
    fun `given text content when writeText then readText round-trips and file lands in workspace`() = runTest {
        val workspace = workspaceWith()

        val written = assertSuccess(workspace.writeText("notes/todo.md", "hello world"))
        assertEquals("notes/todo.md", written.relativePath)
        assertTrue(written.isText)
        assertFalse(written.isDirectory)

        assertEquals("hello world", assertSuccess(workspace.readText("notes/todo.md")))
        assertTrue(File(workspaceRoot(), "notes/todo.md").isFile)
    }

    @Test
    fun `given existing file when writeText without overwrite then AlreadyExists`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("a.txt", "first"))

        assertFailure(workspace.writeText("a.txt", "second"), WorkspaceError.AlreadyExists)
        assertEquals("first", assertSuccess(workspace.readText("a.txt")))
    }

    @Test
    fun `given existing file when writeText with overwrite then replaced`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("a.txt", "first"))

        assertSuccess(workspace.writeText("a.txt", "second", overwrite = true))
        assertEquals("second", assertSuccess(workspace.readText("a.txt")))
    }

    @Test
    fun `given non-existent in-bounds path when resolve then succeeds with empty metadata`() = runTest {
        val workspace = workspaceWith()

        val resolved = assertSuccess(workspace.resolve("future/report.md"))
        assertEquals("future/report.md", resolved.relativePath)
        assertEquals(0L, resolved.sizeBytes)
        assertFalse(resolved.isDirectory)
    }

    @Test
    fun `given parent-traversal path when readText then PathOutsideWorkspace`() = runTest {
        val workspace = workspaceWith()

        assertFailure(workspace.readText("../secret.txt"), WorkspaceError.PathOutsideWorkspace)
        assertFailure(workspace.readText("../../shared_prefs/keys.xml"), WorkspaceError.PathOutsideWorkspace)
    }

    @Test
    fun `given absolute path when writeText then PathOutsideWorkspace`() = runTest {
        val workspace = workspaceWith()

        assertFailure(
            workspace.writeText("/etc/passwd", "x"),
            WorkspaceError.PathOutsideWorkspace,
        )
    }

    @Test
    fun `given sibling-directory escape when writeText then PathOutsideWorkspace`() = runTest {
        val workspace = workspaceWith()

        // `agent_workspace_evil` shares the `agent_workspace` prefix; the trailing-separator
        // check in canonicalResolve must still reject it.
        assertFailure(
            workspace.writeText("../agent_workspace_evil/loot.txt", "x"),
            WorkspaceError.PathOutsideWorkspace,
        )
    }

    @Test
    fun `given traversal that stays inside when writeText then succeeds`() = runTest {
        val workspace = workspaceWith()

        val written = assertSuccess(workspace.writeText("a/../b.txt", "inside"))
        assertEquals("b.txt", written.relativePath)
        assertEquals("inside", assertSuccess(workspace.readText("b.txt")))
    }

    @Test
    fun `given symlink escaping the root when resolve then PathOutsideWorkspace`() = runTest {
        val outside = tempFolder.newFolder("outside")
        File(outside, "secret.txt").writeText("top secret")
        workspaceRoot().mkdirs()
        try {
            Files.createSymbolicLink(File(workspaceRoot(), "link").toPath(), outside.toPath())
        } catch (e: Exception) {
            assumeNoException("Symlinks unsupported on this platform", e)
        }

        val workspace = workspaceWith()
        assertFailure(workspace.readText("link/secret.txt"), WorkspaceError.PathOutsideWorkspace)
    }

    @Test
    fun `given missing file when readText then NotFound`() = runTest {
        val workspace = workspaceWith()

        assertFailure(workspace.readText("nope.txt"), WorkspaceError.NotFound)
    }

    @Test
    fun `given binary file when readText then NotAText`() = runTest {
        val workspace = workspaceWith()
        putRawFile("image.bin", byteArrayOf(0x00, 0x01, 0x02, 0x7F.toByte(), 0xFF.toByte()))

        assertFailure(workspace.readText("image.bin"), WorkspaceError.NotAText)
    }

    @Test
    fun `given content exactly at the per-file limit when writeText then succeeds`() = runTest {
        val workspace = workspaceWith(maxFileSize = 10L)

        assertSuccess(workspace.writeText("ok.txt", "0123456789")) // 10 ASCII bytes
    }

    @Test
    fun `given content one byte over the per-file limit when writeText then TooLarge`() = runTest {
        val workspace = workspaceWith(maxFileSize = 10L)

        assertFailure(workspace.writeText("big.txt", "01234567890"), WorkspaceError.TooLarge) // 11 bytes
    }

    @Test
    fun `given on-disk file larger than the per-file limit when readText then TooLarge`() = runTest {
        putRawFile("huge.txt", ByteArray(20) { 'a'.code.toByte() })
        val workspace = workspaceWith(maxFileSize = 10L)

        assertFailure(workspace.readText("huge.txt"), WorkspaceError.TooLarge)
    }

    @Test
    fun `given a write that exactly fills the total quota when writeText then succeeds`() = runTest {
        val workspace = workspaceWith(maxFileSize = 100L, maxTotal = 20L)
        assertSuccess(workspace.writeText("a.txt", "x".repeat(15))) // total 15

        assertSuccess(workspace.writeText("b.txt", "x".repeat(5))) // total exactly 20
    }

    @Test
    fun `given a write that crosses the total quota when writeText then QuotaExceeded`() = runTest {
        val workspace = workspaceWith(maxFileSize = 100L, maxTotal = 20L)
        assertSuccess(workspace.writeText("a.txt", "x".repeat(15))) // total 15

        // 15 + 10 = 25 > 20 — the cached total must reflect the first write.
        assertFailure(workspace.writeText("b.txt", "x".repeat(10)), WorkspaceError.QuotaExceeded)
    }

    @Test
    fun `given an overwrite shrinking a file when writeText then prior size is not double-counted`() = runTest {
        val workspace = workspaceWith(maxFileSize = 100L, maxTotal = 20L)
        assertSuccess(workspace.writeText("a.txt", "x".repeat(20))) // total 20, at the ceiling

        // Replacing the 20-byte file with a 5-byte one nets to 5, well within quota.
        val written = assertSuccess(workspace.writeText("a.txt", "x".repeat(5), overwrite = true))
        assertEquals(5L, written.sizeBytes)
    }

    @Test
    fun `given an empty workspace when list then empty`() = runTest {
        val workspace = workspaceWith()

        assertEquals(emptyList<Any>(), assertSuccess(workspace.list()))
    }

    @Test
    fun `given text and binary files when list then sorted with text flag set per file`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("z/note.md", "text"))
        assertSuccess(workspace.writeText("a.txt", "more text"))
        putRawFile("blob.bin", byteArrayOf(0x00, 0x01))

        val listed = assertSuccess(workspace.list())
        assertEquals(listOf("a.txt", "blob.bin", "z/note.md"), listed.map { it.relativePath })
        assertEquals(
            mapOf("a.txt" to true, "blob.bin" to false, "z/note.md" to true),
            listed.associate {
                it.relativePath to
                    it.isText
            },
        )
    }

    @Test
    fun `given a binary marker only beyond the sniff window when list then file still reports as text`() = runTest {
        val workspace = workspaceWith()
        // 8 KB of ASCII then a NUL: the NUL sits past the sniff window, so the advisory
        // isText flag stays true. readText (full validation) remains authoritative.
        putRawFile("long.txt", ByteArray(SNIFF_BYTES) { 'a'.code.toByte() } + byteArrayOf(0))

        val entry = assertSuccess(workspace.list()).single { it.relativePath == "long.txt" }
        assertTrue(entry.isText)
    }

    @Test
    fun `given a binary marker beyond the sniff window when readText then NotAText`() = runTest {
        val workspace = workspaceWith()
        putRawFile("long.txt", ByteArray(SNIFF_BYTES) { 'a'.code.toByte() } + byteArrayOf(0))

        // The authoritative read scans the whole file and catches the trailing NUL.
        assertFailure(workspace.readText("long.txt"), WorkspaceError.NotAText)
    }

    @Test
    fun `given a failed write when next write checks quota then the stale cache is not trusted`() = runTest {
        val workspace = workspaceWith(maxFileSize = 100L, maxTotal = 30L)
        assertSuccess(workspace.writeText("a", "12345")) // 5 bytes; primes the cache to 5
        putRawFile("ext.txt", ByteArray(10) { 'x'.code.toByte() }) // +10 on disk, behind the cache's back

        // Writing under "a" (a regular file, not a directory) throws partway; the fix
        // invalidates the cache instead of leaving it stuck at 5.
        try {
            workspace.writeText("a/b.txt", "x")
            fail("expected the parent-is-a-file write to throw")
        } catch (expected: IOException) {
            // The write into a path whose parent is a regular file is supposed to fail.
        }

        // The next quota check must recompute the true 15 bytes on disk: 15 + 20 = 35 > 30.
        // A stale cache (5) would wrongly allow the write.
        assertFailure(workspace.writeText("c.txt", "x".repeat(20)), WorkspaceError.QuotaExceeded)
    }

    @Test
    fun `given a symlink to an outside directory when list then escaping entries are excluded`() = runTest {
        val outside = tempFolder.newFolder("outside")
        File(outside, "secret.txt").writeText("top secret")
        workspaceRoot().mkdirs()
        try {
            Files.createSymbolicLink(File(workspaceRoot(), "link").toPath(), outside.toPath())
        } catch (e: Exception) {
            assumeNoException("Symlinks unsupported on this platform", e)
        }
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("inside.txt", "ok"))

        // `walkTopDown` descends through the symlink, but the containment filter drops
        // anything whose canonical path escapes the workspace.
        assertEquals(listOf("inside.txt"), assertSuccess(workspace.list()).map { it.relativePath })
    }

    // region editText

    @Test
    fun `given unique anchor when editText then replaces and round-trips`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("notes.md", "hello world"))

        val edited = assertSuccess(workspace.editText("notes.md", "world", "there"))
        assertEquals("notes.md", edited.relativePath)

        assertEquals("hello there", assertSuccess(workspace.readText("notes.md")))
    }

    @Test
    fun `given empty newText when editText then deletes the fragment`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("notes.md", "keep [drop] keep"))

        assertSuccess(workspace.editText("notes.md", "[drop] ", ""))

        assertEquals("keep keep", assertSuccess(workspace.readText("notes.md")))
    }

    @Test
    fun `given anchor absent when editText then fails AnchorNotFound and leaves file intact`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("notes.md", "hello world"))

        assertFailure(workspace.editText("notes.md", "missing", "x"), WorkspaceError.AnchorNotFound)

        assertEquals("hello world", assertSuccess(workspace.readText("notes.md")))
    }

    @Test
    fun `given ambiguous anchor when editText then fails AnchorNotUnique with count`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("notes.md", "a-a-a"))

        assertFailure(workspace.editText("notes.md", "a", "b"), WorkspaceError.AnchorNotUnique(3))

        assertEquals("a-a-a", assertSuccess(workspace.readText("notes.md")))
    }

    @Test
    fun `given missing file when editText then fails NotFound`() = runTest {
        val workspace = workspaceWith()

        assertFailure(workspace.editText("ghost.md", "a", "b"), WorkspaceError.NotFound)
    }

    @Test
    fun `given binary file when editText then fails NotAText`() = runTest {
        val workspace = workspaceWith()
        putRawFile("blob.bin", byteArrayOf(1, 0, 2, 3))

        assertFailure(workspace.editText("blob.bin", "x", "y"), WorkspaceError.NotAText)
    }

    @Test
    fun `given traversal path when editText then fails PathOutsideWorkspace`() = runTest {
        val workspace = workspaceWith()

        assertFailure(workspace.editText("../escape.md", "a", "b"), WorkspaceError.PathOutsideWorkspace)
    }

    @Test
    fun `given edit growing past total quota when editText then fails QuotaExceeded and keeps original`() = runTest {
        val workspace = workspaceWith(maxTotal = 10)
        assertSuccess(workspace.writeText("a.txt", "abcde")) // 5 bytes, workspace cap is 10

        // Replacing the single 'e' with 7 chars grows the file to 11 bytes, over the cap.
        assertFailure(workspace.editText("a.txt", "e", "eeeeeee"), WorkspaceError.QuotaExceeded)

        assertEquals("abcde", assertSuccess(workspace.readText("a.txt")))
    }

    // endregion

    // region delete

    @Test
    fun `given existing file when delete then removed and gone from listing`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("reports/old.md", "stale"))

        assertSuccess(workspace.delete("reports/old.md"))

        assertFalse(File(workspaceRoot(), "reports/old.md").exists())
        assertFailure(workspace.readText("reports/old.md"), WorkspaceError.NotFound)
    }

    @Test
    fun `given missing file when delete then fails NotFound`() = runTest {
        val workspace = workspaceWith()

        assertFailure(workspace.delete("ghost.md"), WorkspaceError.NotFound)
    }

    @Test
    fun `given directory path when delete then fails NotFound and directory survives`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("dir/inside.md", "x"))

        // 'dir' resolves to a directory, not a regular file — refused, not traversed.
        assertFailure(workspace.delete("dir"), WorkspaceError.NotFound)
        assertTrue(File(workspaceRoot(), "dir").isDirectory)
    }

    @Test
    fun `given traversal path when delete then fails PathOutsideWorkspace`() = runTest {
        val workspace = workspaceWith()

        assertFailure(workspace.delete("../../shared_prefs/keys.xml"), WorkspaceError.PathOutsideWorkspace)
    }

    @Test
    fun `given a delete after hitting quota when delete then freed space allows a new write`() = runTest {
        val workspace = workspaceWith(maxTotal = 10)
        assertSuccess(workspace.writeText("a.txt", "aaaaa")) // 5 bytes
        assertSuccess(workspace.writeText("b.txt", "bbbbb")) // 5 bytes → workspace full at 10
        // No room left for a third file.
        assertFailure(workspace.writeText("c.txt", "c"), WorkspaceError.QuotaExceeded)

        assertSuccess(workspace.delete("a.txt")) // frees 5 bytes; cached total must follow

        // The freed space is now usable, proving the delete updated the cached counter.
        assertSuccess(workspace.writeText("c.txt", "ccccc"))
    }

    // endregion

    // region atomic write

    @Test
    fun `given a successful overwrite when writeText then leaves no scratch file and replaces content exactly`() =
        runTest {
            val workspace = workspaceWith()
            assertSuccess(workspace.writeText("a.txt", "a long original content"))

            assertSuccess(workspace.writeText("a.txt", "short", overwrite = true))

            // The whole file is replaced (no tail of the longer previous content survives).
            assertEquals("short", assertSuccess(workspace.readText("a.txt")))
            // No staging artifact is left behind on the happy path.
            val names = workspaceRoot().listFiles()?.map { it.name }.orEmpty()
            assertEquals(listOf("a.txt"), names)
        }

    @Test
    fun `given the scratch path is occupied when overwrite then original survives and no garbage remains`() = runTest {
        val workspace = workspaceWith()
        assertSuccess(workspace.writeText("a.txt", "original"))

        // Occupy the reserved scratch path with a directory so staging the new content
        // fails before the rename — modelling a crash between stage and rename. The
        // literal suffix mirrors AgentWorkspaceImpl.RESERVED_TMP_SUFFIX.
        val scratch = File(workspaceRoot(), "a.txt$RESERVED_TMP_SUFFIX")
        assertTrue(scratch.mkdirs())

        try {
            workspace.writeText("a.txt", "REPLACED", overwrite = true)
            fail("expected the staged write to fail while the scratch path is occupied")
        } catch (e: IOException) {
            // expected: the staged write could not be created at the occupied scratch path.
        }

        // The original is untouched (the rename never happened) and no scratch garbage remains.
        assertEquals("original", assertSuccess(workspace.readText("a.txt")))
        val names = workspaceRoot().listFiles()?.map { it.name }.orEmpty()
        assertEquals(listOf("a.txt"), names)
    }

    // endregion

    private companion object {
        const val DEFAULT_FILE_SIZE = 5L * 1024 * 1024
        const val DEFAULT_TOTAL = 100L * 1024 * 1024

        /** Mirrors `AgentWorkspaceImpl.RESERVED_TMP_SUFFIX`. */
        const val RESERVED_TMP_SUFFIX = ".knotwork-tmp"

        /** Mirrors `AgentWorkspaceImpl.TEXT_SNIFF_BYTES`. */
        const val SNIFF_BYTES = 8 * 1024
    }
}
