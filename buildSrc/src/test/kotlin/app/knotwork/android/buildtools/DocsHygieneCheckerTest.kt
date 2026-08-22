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
        // Assert the exact ordered list (not a Set) so a spurious extra or
        // duplicated violation on an already-expected line cannot be masked.
        assertEquals(
            listOf("a.md:2", "b.md:2", "c.md:1", "d.md:1"),
            violations.map { "${it.file}:${it.line}" },
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
            // Token embedded in a longer filename on either side, or a different
            // extension, must not false-positive.
            "index.md" to "See PLAN-archive.md, DESCRIPTIONS.md, OLDPLAN.md, " +
                "my_TODO.md and PLAN.mdx — none are the internal docs.\n",
        )

        assertTrue(DocsHygieneChecker.scan(files).isEmpty())
    }

    @Test
    fun `given genuine reference at a path or word boundary when scanned then flagged`() {
        val files = mapOf(
            "a.md" to "the log lives in decisions.md\n",
            "b.md" to "see docs/PLAN.md\n",
            "c.md" to "read [the vision](VISION.md).\n",
            "d.md" to "PLAN.md at the very start of a line\n",
        )

        val violations = DocsHygieneChecker.scan(files)

        assertEquals(4, violations.size)
        assertTrue(violations.all { it.category == Category.PRIVATE_DOC_REFERENCE })
    }

    @Test
    fun `given an unquoted adb shell am example when scanned then flagged`() {
        // The exact shape that shipped and failed on a device: the host's quotes
        // never reach `am`, so a multi-word value arrives split.
        val doc = "```bash\n" +
            "adb shell am broadcast -a x.y.ACTION -p x.y --es prompt 'What is two plus two?'\n" +
            "```"

        val violations = DocsHygieneChecker.scan(mapOf("docs/a.md" to doc))

        assertEquals(1, violations.size)
        assertEquals(DocsHygieneChecker.Category.UNQUOTED_ADB_SHELL, violations.single().category)
        assertEquals(2, violations.single().line)
    }

    @Test
    fun `given a quoted adb shell am example when scanned then not flagged`() {
        val doc = "```bash\n" +
            "adb shell \"am broadcast -a x.y.ACTION -p x.y --es prompt 'What is two plus two?'\"\n" +
            "```"

        assertEquals(emptyList<DocsHygieneChecker.Violation>(), DocsHygieneChecker.scan(mapOf("docs/a.md" to doc)))
    }

    @Test
    fun `given an adb shell command outside the am family when scanned then not flagged`() {
        // The rule is about extras-carrying `am` invocations, not about every adb
        // call — widening it would flag `adb pull` lines that cannot have the defect.
        val doc = "adb shell pm list packages\nadb pull /sdcard/x"

        assertEquals(emptyList<DocsHygieneChecker.Violation>(), DocsHygieneChecker.scan(mapOf("docs/a.md" to doc)))
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
