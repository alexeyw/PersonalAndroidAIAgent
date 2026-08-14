package app.knotwork.android.presentation.ui.chat.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [contentReportIssueUrl].
 *
 * The failure this guards against is invisible at the call site: an over-long
 * URL is not rejected loudly, it is clipped or dropped by the browser, and the
 * user believes they filed a report that nobody can read. So the length bound
 * and the truncation marker are asserted, not assumed.
 */
class ContentReportIssueUrlTest {

    @Test
    fun `given a report when the url is built then it targets the public issue tracker`() {
        val url = contentReportIssueUrl(subject = "Content report: other", body = "body text")

        assertTrue(
            "url must open the repository's new-issue form, was $url",
            url.startsWith("https://github.com/alexeyw/knotwork/issues/new?title="),
        )
    }

    @Test
    fun `given text with spaces and newlines when the url is built then it is percent encoded`() {
        val url = contentReportIssueUrl(subject = "a b", body = "line one\nline two")

        assertFalse("a raw space would break the link: $url", url.contains(' '))
        assertFalse("a raw newline would break the link: $url", url.contains('\n'))
        assertTrue("the body must survive encoding: $url", url.contains("line+one"))
    }

    @Test
    fun `given an oversized body when the url is built then it fits the limit and says it was cut`() {
        val url = contentReportIssueUrl(subject = "Content report: other", body = "x".repeat(OVERSIZED_CHARS))

        assertTrue("url is ${url.length} characters, over the browser-safe bound", url.length <= MAX_URL_CHARS)
        assertTrue("a shortened report must admit it: $url", url.contains("report+truncated+to+fit+the+link"))
    }

    @Test
    fun `given multi byte text when the url is built then the encoded form still fits`() {
        // Percent-encoding expands a multi-byte character up to ninefold, so a
        // body well under the character bound can still blow past it encoded.
        val url = contentReportIssueUrl(subject = "Content report: other", body = "😀".repeat(MULTIBYTE_CHARS))

        assertTrue("url is ${url.length} characters, over the browser-safe bound", url.length <= MAX_URL_CHARS)
    }

    private companion object {
        /** Mirrors the private bound in the production file. */
        const val MAX_URL_CHARS = 8000

        /** Comfortably past the bound for a plain-ASCII body. */
        const val OVERSIZED_CHARS = 12_000

        /** Enough emoji that the encoded form exceeds the bound on its own. */
        const val MULTIBYTE_CHARS = 2_000
    }
}
