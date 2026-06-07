package app.knotwork.android.domain.memoryio

import app.knotwork.android.domain.models.MemoryChunk
import app.knotwork.android.domain.models.MemoryImportOutcome
import app.knotwork.android.domain.models.MemorySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MemoryJsonSerializer] — the serialize → parse round-trip, the
 * provenance / tag fidelity, and the never-throwing failure paths.
 */
class MemoryJsonSerializerTest {

    private fun chunk(
        id: Long,
        text: String,
        embedding: FloatArray,
        source: MemorySource = MemorySource.Unknown,
        tags: List<String> = emptyList(),
        isPinned: Boolean = false,
    ) = MemoryChunk(
        id = id,
        text = text,
        embedding = embedding,
        timestamp = 1_000L + id,
        isPinned = isPinned,
        source = source,
        tags = tags,
    )

    @Test
    fun `serialize then parse round-trips every field including provenance and tags`() {
        val chunks = listOf(
            chunk(1, "alpha", floatArrayOf(0.1f, 0.2f), MemorySource.Manual, listOf("preference"), isPinned = true),
            chunk(2, "beta", floatArrayOf(0.3f), MemorySource.ChatSession("sess-7")),
            chunk(3, "gamma", floatArrayOf(0.4f, 0.5f, 0.6f), MemorySource.Compaction(listOf(10L, 11L))),
            chunk(4, "delta", floatArrayOf(0.7f)),
        )

        val json = MemoryJsonSerializer.serialize(chunks, embeddingProviderId = "use", exportedAt = 99L)
        val outcome = MemoryJsonSerializer.parse(json)

        assertTrue(outcome is MemoryImportOutcome.Success)
        val document = (outcome as MemoryImportOutcome.Success).document
        assertEquals("use", document.embeddingProviderId)
        assertEquals(99L, document.exportedAt)
        assertEquals(chunks, document.chunks)
    }

    @Test
    fun `serialize emits the current schema version`() {
        val json = MemoryJsonSerializer.serialize(emptyList(), embeddingProviderId = "use", exportedAt = 0L)
        // An empty export still parses cleanly (Success with zero chunks).
        val outcome = MemoryJsonSerializer.parse(json)
        assertTrue(outcome is MemoryImportOutcome.Success)
        assertEquals(0, (outcome as MemoryImportOutcome.Success).document.chunks.size)
    }

    @Test
    fun `parse returns Failure on malformed JSON`() {
        val outcome = MemoryJsonSerializer.parse("{ not json")
        assertTrue(outcome is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse returns Failure when schemaVersion is missing`() {
        val outcome = MemoryJsonSerializer.parse("""{"embeddingProviderId":"use","chunks":[]}""")
        assertTrue(outcome is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse returns Failure when embeddingProviderId is missing`() {
        val outcome = MemoryJsonSerializer.parse("""{"schemaVersion":1,"chunks":[]}""")
        assertTrue(outcome is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse returns Failure when chunks array is missing`() {
        val outcome = MemoryJsonSerializer.parse("""{"schemaVersion":1,"embeddingProviderId":"use"}""")
        assertTrue(outcome is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse returns Failure when a chunk is missing a required field`() {
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"id":1,"text":"x"}]}
        """.trimIndent()
        val outcome = MemoryJsonSerializer.parse(json)
        assertTrue(outcome is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse reports SchemaMismatch when the version differs but still parses`() {
        val older = """
            {"schemaVersion":99,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"id":1,"text":"x","embedding":[0.1],"timestamp":5,"isPinned":false}]}
        """.trimIndent()
        val outcome = MemoryJsonSerializer.parse(older)
        assertTrue(outcome is MemoryImportOutcome.SchemaMismatch)
        val mismatch = outcome as MemoryImportOutcome.SchemaMismatch
        assertEquals(99, mismatch.foundVersion)
        assertEquals(MemoryJsonSerializer.CURRENT_SCHEMA_VERSION, mismatch.expectedVersion)
        assertEquals(1, mismatch.document.chunks.size)
    }

    @Test
    fun `parse rejects an empty embedding array`() {
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"id":1,"text":"x","embedding":[],"timestamp":5}]}
        """.trimIndent()
        assertTrue(MemoryJsonSerializer.parse(json) is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse rejects a non-numeric embedding entry`() {
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"id":1,"text":"x","embedding":[0.1,"oops",0.2],"timestamp":5}]}
        """.trimIndent()
        assertTrue(MemoryJsonSerializer.parse(json) is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse rejects a chunk with explicit null text`() {
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"id":1,"text":null,"embedding":[0.1],"timestamp":5}]}
        """.trimIndent()
        assertTrue(MemoryJsonSerializer.parse(json) is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse rejects a chunk with blank text`() {
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"id":1,"text":"  ","embedding":[0.1],"timestamp":5}]}
        """.trimIndent()
        assertTrue(MemoryJsonSerializer.parse(json) is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse rejects a non-numeric timestamp`() {
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"id":1,"text":"x","embedding":[0.1],"timestamp":"oops"}]}
        """.trimIndent()
        assertTrue(MemoryJsonSerializer.parse(json) is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse rejects a non-positive timestamp`() {
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"id":1,"text":"x","embedding":[0.1],"timestamp":0}]}
        """.trimIndent()
        assertTrue(MemoryJsonSerializer.parse(json) is MemoryImportOutcome.Failure)
    }

    @Test
    fun `parse does not throw on a malformed compaction ids entry`() {
        // A non-numeric ids element must not escape parse() as a JSONException;
        // it resolves to 0 and the chunk still parses.
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"id":1,"text":"x","embedding":[0.1],"timestamp":5,
                        "source":{"type":"compaction","ids":["oops"]}}]}
        """.trimIndent()
        assertTrue(MemoryJsonSerializer.parse(json) is MemoryImportOutcome.Success)
    }

    @Test
    fun `parse defaults a missing id to zero so Room assigns a fresh key`() {
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"text":"x","embedding":[0.1],"timestamp":5}]}
        """.trimIndent()
        val outcome = MemoryJsonSerializer.parse(json)
        assertTrue(outcome is MemoryImportOutcome.Success)
        assertEquals(0L, (outcome as MemoryImportOutcome.Success).document.chunks.single().id)
    }

    @Test
    fun `parse defaults optional fields when absent`() {
        val json = """
            {"schemaVersion":1,"embeddingProviderId":"use","exportedAt":0,
             "chunks":[{"text":"x","embedding":[0.1],"timestamp":5}]}
        """.trimIndent()
        val outcome = MemoryJsonSerializer.parse(json)
        assertTrue(outcome is MemoryImportOutcome.Success)
        val chunk = (outcome as MemoryImportOutcome.Success).document.chunks.single()
        assertEquals(false, chunk.isPinned)
        assertEquals(emptyList<String>(), chunk.tags)
        assertEquals(MemorySource.Unknown, chunk.source)
    }
}
