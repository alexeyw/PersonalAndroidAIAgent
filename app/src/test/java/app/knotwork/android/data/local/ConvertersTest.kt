package app.knotwork.android.data.local

import app.knotwork.android.domain.models.MemorySource
import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.PipelineSamplePrompt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [Converters], focusing on the [NodeContextConfig] JSON round
 * trip and its legacy-row fallback contract. The fallback to
 * [NodeContextConfig.ALL_ENABLED] is what keeps pipelines that were stored
 * before the context-config column existed functionally identical after the migration runs.
 */
class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun `toNodeContextConfig returns ALL_ENABLED for blank input`() {
        assertEquals(NodeContextConfig.ALL_ENABLED, converters.toNodeContextConfig("   "))
    }

    @Test
    fun `toNodeContextConfig returns ALL_ENABLED for malformed JSON`() {
        assertEquals(NodeContextConfig.ALL_ENABLED, converters.toNodeContextConfig("{not json"))
    }

    @Test
    fun `toNodeContextConfig defaults missing keys to true`() {
        val partial = """{"chatHistory":false}"""

        val result = converters.toNodeContextConfig(partial)

        assertEquals(false, result.chatHistory)
        assertEquals(true, result.originalTask)
        assertEquals(true, result.nodeInput)
        assertEquals(true, result.longTermMemory)
        assertEquals(true, result.toolResults)
    }

    @Test
    fun `round-trip preserves every flag combination`() {
        // 32 combinations of 5 booleans — exhaustive on purpose so a future
        // edit that breaks one flag's serialization can't sneak through.
        for (mask in 0 until 32) {
            val original = NodeContextConfig(
                chatHistory = (mask and 0b00001) != 0,
                originalTask = (mask and 0b00010) != 0,
                nodeInput = (mask and 0b00100) != 0,
                longTermMemory = (mask and 0b01000) != 0,
                toolResults = (mask and 0b10000) != 0,
            )

            val serialized = converters.fromNodeContextConfig(original)
            val restored = converters.toNodeContextConfig(serialized)

            assertEquals("Mask=$mask", original, restored)
        }
    }

    // ─── MemorySource ─────────────────────────────────────────────────────

    @Test
    fun `memory source round-trips every variant`() {
        val sources = listOf(
            MemorySource.ChatSession("session-42"),
            MemorySource.Manual,
            MemorySource.Compaction(listOf(1L, 2L, 3L)),
            MemorySource.Unknown,
        )
        for (source in sources) {
            val restored = converters.toMemorySource(converters.fromMemorySource(source))
            assertEquals(source, restored)
        }
    }

    @Test
    fun `toMemorySource returns Unknown for blank input`() {
        assertEquals(MemorySource.Unknown, converters.toMemorySource("   "))
    }

    @Test
    fun `toMemorySource returns Unknown for malformed json`() {
        assertEquals(MemorySource.Unknown, converters.toMemorySource("{not json"))
    }

    @Test
    fun `toMemorySource returns Unknown for unrecognised type key`() {
        assertEquals(MemorySource.Unknown, converters.toMemorySource("{\"type\":\"future_kind\"}"))
    }

    @Test
    fun `fromMemorySource encodes unknown as the migration default`() {
        assertEquals("{\"type\":\"unknown\"}", converters.fromMemorySource(MemorySource.Unknown))
    }

    // The FloatArray ↔ ByteArray pair delegates to EmbeddingBlobCodec (whose
    // wire format is exhaustively covered by EmbeddingBlobCodecTest); these
    // tests pin the null-passthrough contract and the delegation itself.

    @Test
    fun `fromFloatArray returns null for null input`() {
        assertNull(converters.fromFloatArray(null))
    }

    @Test
    fun `toFloatArray returns null for null input`() {
        assertNull(converters.toFloatArray(null))
    }

    @Test
    fun `toFloatArray returns null for empty blob marker`() {
        assertNull(converters.toFloatArray(ByteArray(0)))
    }

    @Test
    fun `embedding round-trip through converters preserves values`() {
        val original = floatArrayOf(0.5f, -1.25f, 3.75f)

        val restored = converters.toFloatArray(converters.fromFloatArray(original))

        assertArrayEquals(original, restored, 0.0f)
    }

    @Test
    fun `sample prompts round-trip through converters preserving a null tools hint`() {
        val prompts = listOf(
            PipelineSamplePrompt(title = "Look up benchmarks", toolsHint = "search_tool"),
            PipelineSamplePrompt(title = "Explain on-device inference"),
        )

        val restored = converters.toSamplePrompts(converters.fromSamplePrompts(prompts))

        assertEquals(prompts, restored)
        assertNull(restored[1].toolsHint)
    }

    @Test
    fun `toSamplePrompts returns empty list for the empty-array default and blank input`() {
        assertTrue(converters.toSamplePrompts("[]").isEmpty())
        assertTrue(converters.toSamplePrompts("   ").isEmpty())
    }

    @Test
    fun `toSamplePrompts returns empty list for malformed JSON`() {
        assertTrue(converters.toSamplePrompts("{not json").isEmpty())
    }

    @Test
    fun `toSamplePrompts skips entries without a title`() {
        // A title is mandatory; a stray object missing one is dropped rather
        // than surfacing a blank card.
        val json = "[{\"toolsHint\":\"search_tool\"},{\"title\":\"Keep me\"}]"

        val restored = converters.toSamplePrompts(json)

        assertEquals(listOf(PipelineSamplePrompt(title = "Keep me")), restored)
    }
}
