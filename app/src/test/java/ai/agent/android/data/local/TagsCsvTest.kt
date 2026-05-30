package ai.agent.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the shared [TagsCsv] codec. */
class TagsCsvTest {

    @Test
    fun `encode joins trimmed non-blank tags`() {
        assertEquals("a,b,c", TagsCsv.encode(listOf(" a ", "b", "", "  ", "c")))
    }

    @Test
    fun `decode splits and drops blanks`() {
        assertEquals(listOf("a", "b"), TagsCsv.decode("a, ,b"))
        assertEquals(emptyList<String>(), TagsCsv.decode(""))
    }

    @Test
    fun `a tag containing the separator round-trips as one tag, not several`() {
        // Without escaping, encode collapses the comma so decode can't split it.
        val encoded = TagsCsv.encode(listOf("sci-fi, fantasy"))
        assertEquals("sci-fi fantasy", encoded)
        assertEquals(listOf("sci-fi fantasy"), TagsCsv.decode(encoded))
    }
}
