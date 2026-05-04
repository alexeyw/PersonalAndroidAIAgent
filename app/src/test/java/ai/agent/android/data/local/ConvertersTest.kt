package ai.agent.android.data.local

import ai.agent.android.domain.models.NodeContextConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [Converters], focusing on the [NodeContextConfig] JSON round
 * trip and its legacy-row fallback contract. The fallback to
 * [NodeContextConfig.ALL_ENABLED] is what keeps pipelines that were stored
 * before Phase 15 functionally identical after the migration runs.
 */
class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun `fromNodeContextConfig returns null for null input`() {
        assertNull(converters.fromNodeContextConfig(null))
    }

    @Test
    fun `toNodeContextConfig returns ALL_ENABLED for null input`() {
        assertEquals(NodeContextConfig.ALL_ENABLED, converters.toNodeContextConfig(null))
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

        assertNotNull(result)
        assertEquals(false, result!!.chatHistory)
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
}
