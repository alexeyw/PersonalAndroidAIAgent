package app.knotwork.android.domain.models

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

    @Test
    fun `defaultForType returns nodeInput-and-originalTask only for LITE_RT`() {
        val config = NodeContextConfig.defaultForType(NodeType.LITE_RT)

        assertEquals(
            NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType returns nodeInput originalTask and chatHistory for CLOUD`() {
        val config = NodeContextConfig.defaultForType(NodeType.CLOUD)

        assertEquals(
            NodeContextConfig(
                chatHistory = true,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType returns only nodeInput for TOOL`() {
        val config = NodeContextConfig.defaultForType(NodeType.TOOL)

        assertEquals(
            NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType returns nodeInput-and-originalTask for CLARIFICATION`() {
        val config = NodeContextConfig.defaultForType(NodeType.CLARIFICATION)

        assertEquals(
            NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType returns ALL_ENABLED for OUTPUT`() {
        val config = NodeContextConfig.defaultForType(NodeType.OUTPUT)

        assertEquals(NodeContextConfig.ALL_ENABLED, config)
    }

    @Test
    fun `defaultForType returns nodeInput-and-originalTask for QUEUE_PROCESSOR`() {
        val config = NodeContextConfig.defaultForType(NodeType.QUEUE_PROCESSOR)

        assertEquals(
            NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType returns only nodeInput for INPUT`() {
        val config = NodeContextConfig.defaultForType(NodeType.INPUT)

        assertEquals(
            NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType returns only nodeInput for IF_CONDITION`() {
        val config = NodeContextConfig.defaultForType(NodeType.IF_CONDITION)

        assertEquals(
            NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType returns chat history plus original task for INTENT_ROUTER`() {
        val config = NodeContextConfig.defaultForType(NodeType.INTENT_ROUTER)

        assertEquals(
            NodeContextConfig(
                chatHistory = true,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType returns nodeInput-and-originalTask for DECOMPOSITION`() {
        val config = NodeContextConfig.defaultForType(NodeType.DECOMPOSITION)

        assertEquals(
            NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType enables tool results plus original task for SUMMARY`() {
        val config = NodeContextConfig.defaultForType(NodeType.SUMMARY)

        assertEquals(
            NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = true,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType enables tool results plus original task for EVALUATION`() {
        val config = NodeContextConfig.defaultForType(NodeType.EVALUATION)

        assertEquals(
            NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = true,
            ),
            config,
        )
    }

    @Test
    fun `defaultForType result is non-empty for every NodeType`() {
        // Guards that no recommended preset would be rejected by
        // PipelineGraph.validate() as an "all-disabled" configuration on a
        // context-aware node.
        NodeType.values().forEach { type ->
            assertTrue(
                "defaultForType($type) must enable at least one flag",
                !NodeContextConfig.defaultForType(type).isEmpty(),
            )
        }
    }
}
