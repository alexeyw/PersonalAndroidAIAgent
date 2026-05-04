package ai.agent.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the contract that legacy nodes (created before Phase 15 introduced the
 * `context_config` column) keep receiving the full pipeline context. The
 * backward-compatibility migration writes the JSON equivalent of
 * [NodeContextConfig.ALL_ENABLED] for every existing row, so this test is the
 * companion guard on the domain side.
 */
class NodeContextConfigTest {

    @Test
    fun `default constructor enables every flag`() {
        val config = NodeContextConfig()

        assertTrue(config.chatHistory)
        assertTrue(config.originalTask)
        assertTrue(config.nodeInput)
        assertTrue(config.longTermMemory)
        assertTrue(config.toolResults)
    }

    @Test
    fun `ALL_ENABLED equals an explicitly-all-true config`() {
        assertEquals(
            NodeContextConfig(
                chatHistory = true,
                originalTask = true,
                nodeInput = true,
                longTermMemory = true,
                toolResults = true,
            ),
            NodeContextConfig.ALL_ENABLED,
        )
    }

    @Test
    fun `copy preserves untouched flags and only flips the named one`() {
        val base = NodeContextConfig.ALL_ENABLED

        val withoutHistory = base.copy(chatHistory = false)

        assertEquals(false, withoutHistory.chatHistory)
        assertTrue(withoutHistory.originalTask)
        assertTrue(withoutHistory.nodeInput)
        assertTrue(withoutHistory.longTermMemory)
        assertTrue(withoutHistory.toolResults)
    }
}
