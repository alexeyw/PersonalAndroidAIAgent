package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.MemoryChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MemoryAccessLogFormatter] — the pure formatter behind the
 * `MemoryAccess` console event. Covers terse/verbose rendering, the empty
 * corpus, query/snippet truncation, and locale-independent score formatting.
 */
class MemoryAccessLogFormatterTest {

    private fun chunk(id: Long, text: String): MemoryChunk =
        MemoryChunk(id = id, text = text, embedding = FloatArray(0), timestamp = 0L)

    @Test
    fun `given hits when terse then renders query count and scores on one line`() {
        val hits = listOf(
            chunk(1, "user prefers dark mode") to 0.834f,
            chunk(2, "user lives in Berlin") to 0.401f,
        )

        val message = MemoryAccessLogFormatter.format(
            query = "what is my UI preference",
            source = RetrievalQuerySource.USER_PROMPT,
            hits = hits,
            verbose = false,
        )

        assertEquals("Memory: query='what is my UI preference' [user prompt] → 2 hits (0.83, 0.40)", message)
    }

    @Test
    fun `given no hits when formatted then renders zero-hit line without score parens`() {
        val message = MemoryAccessLogFormatter.format(
            query = "anything",
            source = RetrievalQuerySource.USER_PROMPT,
            hits = emptyList(),
            verbose = false,
        )

        assertEquals("Memory: query='anything' [user prompt] → 0 hits", message)
    }

    @Test
    fun `given no hits when verbose then still renders only the header line`() {
        val message = MemoryAccessLogFormatter.format(
            query = "anything",
            source = RetrievalQuerySource.USER_PROMPT,
            hits = emptyList(),
            verbose = true,
        )

        assertEquals("Memory: query='anything' [user prompt] → 0 hits", message)
        assertFalse(message.contains("\n"))
    }

    @Test
    fun `given hits when verbose then appends one indented snippet line per hit`() {
        val hits = listOf(
            chunk(1, "user prefers dark mode") to 0.9f,
            chunk(2, "user lives in Berlin") to 0.5f,
        )

        val message = MemoryAccessLogFormatter.format(
            query = "prefs",
            source = RetrievalQuerySource.DECLARED,
            hits = hits,
            verbose = true,
        )

        val lines = message.split("\n")
        assertEquals("Memory: query='prefs' [pipeline-declared] → 2 hits (0.90, 0.50)", lines[0])
        assertEquals("  1. [0.90] user prefers dark mode", lines[1])
        assertEquals("  2. [0.50] user lives in Berlin", lines[2])
        assertEquals(3, lines.size)
    }

    @Test
    fun `given long query when formatted then truncates to the configured maximum with ellipsis`() {
        val query = "a".repeat(MemoryAccessLogFormatter.QUERY_MAX_LENGTH + 20)

        val message = MemoryAccessLogFormatter.format(
            query = query,
            source = RetrievalQuerySource.USER_PROMPT,
            hits = emptyList(),
            verbose = false,
        )

        val rendered = message.substringAfter("query='").substringBefore("'")
        assertEquals(MemoryAccessLogFormatter.QUERY_MAX_LENGTH + 1, rendered.length) // chars + ellipsis
        assertTrue(rendered.endsWith("…"))
    }

    @Test
    fun `given long chunk text when verbose then snippet is truncated to the configured maximum`() {
        val text = "z".repeat(MemoryAccessLogFormatter.SNIPPET_MAX_LENGTH + 50)
        val hits = listOf(chunk(1, text) to 0.7f)

        val message = MemoryAccessLogFormatter.format(
            query = "q",
            source = RetrievalQuerySource.NODE_INPUT,
            hits = hits,
            verbose = true,
        )

        val snippet = message.split("\n")[1].substringAfter("] ")
        assertEquals(MemoryAccessLogFormatter.SNIPPET_MAX_LENGTH + 1, snippet.length)
        assertTrue(snippet.endsWith("…"))
    }

    @Test
    fun `given each query source when formatted then the header carries its label`() {
        // The label is what makes a background run's retrieval explainable
        // after the fact, so every source must render a distinct tag.
        val labels = RetrievalQuerySource.entries.map { source ->
            MemoryAccessLogFormatter.format(
                query = "q",
                source = source,
                hits = emptyList(),
                verbose = false,
            ).substringAfter("[").substringBefore("]")
        }

        assertEquals(listOf("pipeline-declared", "node input", "user prompt"), labels)
    }

    @Test
    fun `given multi-line query and chunk text when formatted then whitespace is collapsed`() {
        val hits = listOf(chunk(1, "line one\n\tline two") to 0.6f)

        val message = MemoryAccessLogFormatter.format(
            query = "multi\nline\nquery",
            source = RetrievalQuerySource.NODE_INPUT,
            hits = hits,
            verbose = true,
        )

        val lines = message.split("\n")
        assertEquals("Memory: query='multi line query' [node input] → 1 hits (0.60)", lines[0])
        assertEquals("  1. [0.60] line one line two", lines[1])
    }
}
