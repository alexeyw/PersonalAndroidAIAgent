package app.knotwork.android.buildtools

import app.knotwork.android.buildtools.DocsHygieneChecker.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DocsHygieneChecker].
 *
 * They assert that every forbidden token of both categories is detected with the
 * correct file, line, and category, that clean input yields no violations, and
 * that near-miss filenames (for example `PLAN-archive.md`) do not false-positive.
 * Run with `./gradlew -p buildSrc test`.
 *
 * The forbidden literals appear verbatim here on purpose — the scanner only reads
 * the `.md` files it is handed, never this Kotlin source, so writing the real
 * tokens in the fixtures cannot make the guard flag itself.
 */
class DocsHygieneCheckerTest {

    @Test
    fun `given clean document when scanned then no violations`() {
        val files = mapOf(
            "README.md" to "# Knotwork\n\nA clean paragraph with a [link](docs/user-guide.md).\n",
            "docs/architecture.md" to "# Architecture\n\nNothing forbidden here.\n",
        )

        assertTrue(DocsHygieneChecker.scan(files).isEmpty())
    }

    @Test
    fun `given each LLM artifact token when scanned then flagged as artifact`() {
        val files = mapOf(
            "a.md" to "text\n</content>\n",
            "b.md" to "text\n</invoke>\n",
            "c.md" to "prefix " + "<" + "antml:invoke> suffix\n",
            "d.md" to "<function_results>\n",
        )

        val violations = DocsHygieneChecker.scan(files)

        assertTrue(violations.all { it.category == Category.LLM_ARTIFACT })
        assertEquals(
            setOf("a.md:2", "b.md:2", "c.md:1", "d.md:1"),
            violations.map { "${it.file}:${it.line}" }.toSet(),
        )
    }

    @Test
    fun `given each private-doc token when scanned then flagged as reference`() {
        val tokens = listOf(
            "decisions.md",
            "DESCRIPTION.md",
            "PLAN.md",
            "VISION.md",
            "TODO.md",
            "CLAUDE.md",
            "project_docs/design/foo.md",
        )
        val files = tokens
            .mapIndexed { index, token -> "doc$index.md" to "see $token for details\n" }
            .toMap()

        val violations = DocsHygieneChecker.scan(files)

        assertEquals(tokens.size, violations.size)
        assertTrue(violations.all { it.category == Category.PRIVATE_DOC_REFERENCE })
    }

    @Test
    fun `given near-miss filename when scanned then not flagged`() {
        val files = mapOf(
            "index.md" to "The archive lives in PLAN-archive.md and DESCRIPTIONS.md.\n",
        )

        assertTrue(DocsHygieneChecker.scan(files).isEmpty())
    }

    @Test
    fun `given multiple hits when scanned then ordered by file then line`() {
        val files = mapOf(
            "z.md" to "clean\n</invoke>\n",
            "a.md" to "see PLAN.md\nclean\n</content>\n",
        )

        val ordered = DocsHygieneChecker.scan(files).map { "${it.file}:${it.line}" }

        assertEquals(listOf("a.md:1", "a.md:3", "z.md:2"), ordered)
    }

    @Test
    fun `given a violation when formatted then uses path-line-message shape`() {
        val files = mapOf("README.md" to "trailing\n</content>\n")

        val formatted = DocsHygieneChecker.scan(files).single().format()

        assertEquals("README.md:2: LLM tool-call artifact `</content>`", formatted)
    }
}
