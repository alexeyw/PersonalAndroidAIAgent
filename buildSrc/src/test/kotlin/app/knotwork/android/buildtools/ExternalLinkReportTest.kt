package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ExternalLinkReport].
 *
 * Run with `./gradlew -p buildSrc test`.
 */
class ExternalLinkReportTest {

    @Test
    fun `given the same URL written twice when grouped then it is one target carrying both places`() {
        val links = listOf(
            DocLinkChecker.ExternalLink("README.md", 3, "https://example.com/a"),
            DocLinkChecker.ExternalLink("docs/a.md", 9, "https://example.com/a"),
            DocLinkChecker.ExternalLink("docs/a.md", 11, "https://example.com/b"),
        )

        val targets = ExternalLinkReport.targetsOf(links)

        assertEquals(2, targets.size)
        assertEquals(listOf("README.md:3", "docs/a.md:9"), targets[0].references)
    }

    @Test
    fun `given every URL answering when rendered then the totals are still stated`() {
        val report = ExternalLinkReport.render(
            listOf(ExternalLinkReport.Outcome("https://example.com/a", true, "HTTP 200", listOf("README.md:3"))),
        )

        assertTrue(report.contains("Probed 1 distinct URL(s); 0 did not answer."))
        assertTrue(report.contains("never fails the build"))
    }

    @Test
    fun `given a failing URL when rendered then it is listed with its places`() {
        val report = ExternalLinkReport.render(
            listOf(
                ExternalLinkReport.Outcome("https://example.com/a", true, "HTTP 200", listOf("README.md:3")),
                ExternalLinkReport.Outcome("https://example.com/b", false, "HTTP 404", listOf("docs/a.md:9")),
            ),
        )

        assertTrue(report.contains("| https://example.com/b | HTTP 404 | docs/a.md:9 |"))
        assertTrue(!report.contains("https://example.com/a |"))
    }
}
