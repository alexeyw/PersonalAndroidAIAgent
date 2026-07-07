package app.knotwork.android.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the shared single-line-title helpers. */
class TitleTextTest {

    @Test
    fun `collapseWhitespace trims and collapses runs of whitespace`() {
        assertEquals("Plan a trip to Rome", "  Plan a trip\n\nto   Rome  ".collapseWhitespace())
    }

    @Test
    fun `toSingleLineTitle returns the collapsed text when within the limit`() {
        assertEquals("hello world", "  hello   world ".toSingleLineTitle(maxLength = 40, ellipsis = "…"))
    }

    @Test
    fun `toSingleLineTitle truncates with the ellipsis when over the limit`() {
        val result = "x".repeat(80).toSingleLineTitle(maxLength = 60, ellipsis = "…")

        assertEquals(61, result.length) // 60 chars + the ellipsis
        assertEquals("x".repeat(60) + "…", result)
    }

    @Test
    fun `toSingleLineTitle trims a trailing space left by the cut before appending the ellipsis`() {
        // "ab " cut at 3 → "ab " → trimEnd → "ab" + ellipsis.
        assertEquals("ab...", "ab cd".toSingleLineTitle(maxLength = 3, ellipsis = "..."))
    }

    @Test
    fun `toSingleLineTitle returns empty for blank input`() {
        assertEquals("", "   \n  ".toSingleLineTitle(maxLength = 10, ellipsis = "…"))
    }
}
