package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coverage for [FileMapGenerator]. */
class FileMapGeneratorTest {

    private fun document(body: String): String =
        "# Map\n\nprose\n\n" +
            FileMapGenerator.openMarker(FileMapGenerator.BLOCK_SOURCES) + "\n" +
            body.trimEnd('\n').let { if (it.isEmpty()) "" else "$it\n" } +
            FileMapGenerator.closeMarker(FileMapGenerator.BLOCK_SOURCES) + "\n\ntrailing prose\n"

    private fun render(body: String, files: List<FileMapGenerator.SourceFile>) =
        FileMapGenerator.render(document(body), FileMapGenerator.BLOCK_SOURCES, files)

    private fun file(path: String, kdoc: String? = null) = FileMapGenerator.SourceFile(path, kdoc)

    private fun renderedBody(markdown: String): String =
        markdown
            .substringAfter(FileMapGenerator.openMarker(FileMapGenerator.BLOCK_SOURCES) + "\n")
            .substringBefore(FileMapGenerator.closeMarker(FileMapGenerator.BLOCK_SOURCES))

    @Test
    fun `given files in several directories when rendering then the tree is nested and alphabetical`() {
        val result = render(
            body = "",
            files = listOf(
                file("z/Late.kt", "Late."),
                file("App.kt", "The application."),
                file("a/Early.kt", "Early."),
                file("a/b/Deep.kt", "Deep."),
            ),
        )

        assertEquals(
            """
            - `a/` - **No description** — replace this line by hand.
              - `b/` - **No description** — replace this line by hand.
                - `Deep.kt` - Deep.
              - `Early.kt` - Early.
            - `App.kt` - The application.
            - `z/` - **No description** — replace this line by hand.
              - `Late.kt` - Late.
            """.trimIndent() + "\n",
            renderedBody(result.markdown),
        )
    }

    @Test
    fun `given a hand-written description when rendering then it wins over the KDoc sentence`() {
        val result = render(
            body = "- `Pool.kt` - The single owner of live connections, and why there is only one.",
            files = listOf(file("Pool.kt", "A pool.")),
        )

        assertTrue(result.markdown.contains("and why there is only one."))
        assertFalse(result.markdown.contains("- `Pool.kt` - A pool."))
    }

    @Test
    fun `given a new path when rendering then the KDoc sentence seeds its description`() {
        val result = render(body = "", files = listOf(file("Fresh.kt", "A freshly added file.")))

        assertTrue(result.markdown.contains("- `Fresh.kt` - A freshly added file."))
        assertEquals(emptyList<String>(), result.undescribed)
    }

    @Test
    fun `given a new path with no KDoc when rendering then it is marked and counted`() {
        val result = render(body = "", files = listOf(file("Silent.kt")))

        assertTrue(result.markdown.contains(FileMapGenerator.NO_DESCRIPTION_MARKER))
        assertEquals(listOf("Silent.kt"), result.undescribed)
        assertEquals(listOf("Silent.kt"), result.filesWithoutSeed)
    }

    @Test
    fun `given a described file that has no KDoc when rendering then it counts as documented but unseeded`() {
        val result = render(body = "- `Silent.kt` - Written by hand.", files = listOf(file("Silent.kt")))

        assertEquals(emptyList<String>(), result.undescribed)
        assertEquals(listOf("Silent.kt"), result.filesWithoutSeed)
    }

    @Test
    fun `given a description whose path is gone when rendering then it is reported rather than discarded`() {
        val result = render(
            body = "- `Moved.kt` - A paragraph of rationale.\n- `Stays.kt` - Still here.",
            files = listOf(file("Stays.kt")),
        )

        assertEquals(listOf("Moved.kt" to "A paragraph of rationale."), result.dropped)
        assertFalse(result.markdown.contains("Moved.kt"))
    }

    @Test
    fun `given every live path described when rendering then none of those descriptions is dropped`() {
        val result = render(
            body = "- `a/` - The a package.\n  - `One.kt` - First.\n- `Two.kt` - Second.",
            files = listOf(file("a/One.kt"), file("Two.kt")),
        )

        assertEquals(emptyList<Pair<String, String>>(), result.dropped)
        assertTrue(result.markdown.contains("- `a/` - The a package."))
        assertTrue(result.markdown.contains("- `One.kt` - First."))
        assertTrue(result.markdown.contains("- `Two.kt` - Second."))
    }

    @Test
    fun `given a rendered document when rendering it again then nothing changes`() {
        val files = listOf(file("a/One.kt", "First."), file("Two.kt"), file("b/c/Three.kt", "Third."))
        val once = render(body = "- `a/` - The a package.", files = files).markdown
        val twice = FileMapGenerator.render(once, FileMapGenerator.BLOCK_SOURCES, files).markdown

        assertEquals(once, twice)
    }

    @Test
    fun `given prose around the block when rendering then it is left untouched`() {
        val result = render(body = "", files = listOf(file("One.kt", "First.")))

        assertTrue(result.markdown.startsWith("# Map\n\nprose\n\n"))
        assertTrue(result.markdown.endsWith("\n\ntrailing prose\n"))
    }

    @Test
    fun `given an em-dash separator when parsing then the description is still carried`() {
        val carried = FileMapGenerator.parseDescriptions("- `One.kt` — Written with an em dash.")

        assertEquals(mapOf("One.kt" to "Written with an em dash."), carried)
    }

    @Test
    fun `given a description wrapped over several lines when parsing then it is joined`() {
        val carried = FileMapGenerator.parseDescriptions(
            "- `One.kt` — First half\n  and the second half.\n- `Two.kt` - Short.",
        )

        assertEquals(
            mapOf("One.kt" to "First half and the second half.", "Two.kt" to "Short."),
            carried,
        )
    }

    @Test
    fun `given an entry with no description when parsing then no empty description is recorded`() {
        assertEquals(emptyMap<String, String>(), FileMapGenerator.parseDescriptions("- `dir/`"))
    }

    @Test
    fun `given a marker line when parsing then it is not carried as a description`() {
        val body = "- `One.kt` - ${FileMapGenerator.NO_DESCRIPTION_MARKER} — replace this line by hand."

        assertEquals(emptyMap<String, String>(), FileMapGenerator.parseDescriptions(body))
    }

    @Test(expected = FileMapGenerator.GenerationException::class)
    fun `given an entry indented past its parent when parsing then generation fails`() {
        FileMapGenerator.parseDescriptions("- `a/` - A.\n      - `Deep.kt` - Too deep.")
    }

    @Test(expected = FileMapGenerator.GenerationException::class)
    fun `given a document without the markers when rendering then generation fails`() {
        FileMapGenerator.render("# Map\n\nno markers here\n", FileMapGenerator.BLOCK_SOURCES, listOf(file("One.kt")))
    }

    @Test
    fun `given a committed block that matches the sources when checking drift then none is reported`() {
        val files = listOf(file("One.kt", "First."))
        val rendered = render(body = "", files = files).markdown

        assertFalse(FileMapGenerator.drift(rendered, FileMapGenerator.BLOCK_SOURCES, files))
    }

    @Test
    fun `given a file missing from the committed block when checking drift then it is reported`() {
        val rendered = render(body = "", files = listOf(file("One.kt", "First."))).markdown

        assertTrue(
            FileMapGenerator.drift(
                rendered,
                FileMapGenerator.BLOCK_SOURCES,
                listOf(file("One.kt", "First."), file("Two.kt", "Second.")),
            ),
        )
    }

    @Test
    fun `given two blocks in one document when rendering one then the other is untouched`() {
        val other = FileMapGenerator.openMarker(FileMapGenerator.BLOCK_SOURCE_SETS) +
            "\n- `full/Flavour.kt` - A flavour file.\n" +
            FileMapGenerator.closeMarker(FileMapGenerator.BLOCK_SOURCE_SETS)
        val markdown = document("- `One.kt` - First.") + "\n" + other + "\n"

        val result = FileMapGenerator.render(markdown, FileMapGenerator.BLOCK_SOURCES, listOf(file("One.kt")))

        assertTrue(result.markdown.contains("- `full/Flavour.kt` - A flavour file."))
    }
}
