package app.knotwork.android.buildtools

import app.knotwork.android.buildtools.DocLinkChecker.PathKind
import app.knotwork.android.buildtools.DocLinkChecker.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DocLinkChecker].
 *
 * The file system arrives as a lambda, so these describe a repository that does
 * not exist — including the shapes that are awkward to create on disk (a target
 * climbing above the root, a Markdown file outside the scanned set). Run with
 * `./gradlew -p buildSrc test`.
 */
class DocLinkCheckerTest {

    /** Resolver for a repository holding exactly the named files and directories. */
    private fun repository(vararg entries: Pair<String, PathKind>): (String) -> PathKind {
        val known = entries.toMap()
        return { path -> known[path] ?: PathKind.MISSING }
    }

    @Test
    fun `given resolvable links when checked then no violations`() {
        val docs = mapOf(
            "README.md" to "See [the guide](docs/user-guide.md) and [a section](#features).\n\n## Features\n",
            "docs/user-guide.md" to "Back to [the readme](../README.md).\n",
        )

        val repository = repository("docs/user-guide.md" to PathKind.FILE, "README.md" to PathKind.FILE)
        val result = DocLinkChecker.check(docs, repository)

        assertTrue(result.violations.isEmpty())
        assertEquals(3, result.internalLinkCount)
    }

    @Test
    fun `given a missing file when checked then reported with its line`() {
        val docs = mapOf("README.md" to "text\n\n[gone](docs/gone.md)\n")

        val violations = DocLinkChecker.check(docs, repository()).violations

        assertEquals(1, violations.size)
        assertEquals(Reason.MISSING_FILE, violations[0].reason)
        assertEquals(3, violations[0].line)
        assertEquals("README.md:3: target does not exist -> `docs/gone.md`", violations[0].format())
    }

    @Test
    fun `given a missing anchor when checked then reported`() {
        val docs = mapOf(
            "README.md" to "[x](docs/a.md#no-such-heading)\n",
            "docs/a.md" to "## Real heading\n",
        )

        val violations = DocLinkChecker.check(docs, repository("docs/a.md" to PathKind.FILE)).violations

        assertEquals(listOf(Reason.MISSING_ANCHOR), violations.map { it.reason })
    }

    @Test
    fun `given an anchor into the same document when checked then it resolves`() {
        val docs = mapOf("docs/a.md" to "[x](#real-heading)\n\n## Real heading\n")

        assertTrue(DocLinkChecker.check(docs, repository()).violations.isEmpty())
    }

    @Test
    fun `given a target above the repository root when checked then reported`() {
        val docs = mapOf(".github/pull_request_template.md" to "[c](../../CONTRIBUTING.md)\n")

        val violations = DocLinkChecker.check(docs, repository()).violations

        assertEquals(listOf(Reason.OUTSIDE_REPOSITORY), violations.map { it.reason })
    }

    @Test
    fun `given a site-absolute target when checked then reported`() {
        val docs = mapOf("README.md" to "[c](/CONTRIBUTING.md)\n")

        val violations = DocLinkChecker.check(docs, repository()).violations

        assertEquals(listOf(Reason.SITE_ABSOLUTE), violations.map { it.reason })
    }

    @Test
    fun `given an anchor on a directory when checked then reported`() {
        val docs = mapOf("README.md" to "[d](docs/decisions#x)\n")

        val violations = DocLinkChecker.check(docs, repository("docs/decisions" to PathKind.DIRECTORY)).violations

        assertEquals(listOf(Reason.ANCHOR_ON_DIRECTORY), violations.map { it.reason })
    }

    @Test
    fun `given a directory target without an anchor when checked then it resolves`() {
        val docs = mapOf("README.md" to "[d](docs/decisions/)\n")

        val result = DocLinkChecker.check(docs, repository("docs/decisions" to PathKind.DIRECTORY))

        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `given an anchor into a Markdown file outside the scanned set when checked then the hole is reported`() {
        val docs = mapOf("README.md" to "[x](other/notes.md#section)\n")

        val violations = DocLinkChecker.check(docs, repository("other/notes.md" to PathKind.FILE)).violations

        assertEquals(listOf(Reason.UNSCANNED_TARGET), violations.map { it.reason })
    }

    @Test
    fun `given an anchor into a non-Markdown file when checked then only the file is required`() {
        val docs = mapOf("README.md" to "[x](app/build.gradle.kts#L10)\n")

        val result = DocLinkChecker.check(docs, repository("app/build.gradle.kts" to PathKind.FILE))

        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `given external and mail links when checked then they are not gated`() {
        val docs = mapOf("README.md" to "[a](https://example.com/x) [b](mailto:x@example.com)\n")

        val result = DocLinkChecker.check(docs, repository())

        assertTrue(result.violations.isEmpty())
        assertEquals(0, result.internalLinkCount)
        assertEquals(listOf("https://example.com/x"), result.external.map { it.url })
    }

    @Test
    fun `given external links when only those are asked for then no file system is consulted`() {
        val docs = mapOf(
            "README.md" to "[a](https://example.com/x) and [b](docs/gone.md)\n",
            "docs/a.md" to "[c](HTTPS://Example.com/Y)\n",
        )

        val external = DocLinkChecker.externalLinksOf(docs)

        assertEquals(listOf("https://example.com/x", "HTTPS://Example.com/Y"), external.map { it.url })
        assertEquals(listOf("README.md", "docs/a.md"), external.map { it.file })
    }

    @Test
    fun `given a percent-encoded anchor when checked then it resolves against the decoded slug`() {
        val docs = mapOf(
            "README.md" to "[x](docs/a.md#%D1%82%D0%B5%D1%81%D1%82)\n",
            "docs/a.md" to "## тест\n",
        )

        assertTrue(DocLinkChecker.check(docs, repository("docs/a.md" to PathKind.FILE)).violations.isEmpty())
    }
}
