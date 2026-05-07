package ai.agent.android.domain.engine

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.models.ToolInvocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exhaustive test for [NodeContextBuilder] — the single source of truth for how
 * pipeline context blocks are concatenated into a node's executor input.
 *
 * The suite covers four guarantees the builder must keep:
 *
 *  1. **All 32 flag combinations** — for every subset of the five booleans on
 *     [NodeContextConfig], the rendered string contains exactly the headers of
 *     enabled blocks and none of the disabled ones (Group A).
 *  2. **Block order is fixed** regardless of the flag combination (Group B).
 *  3. **Block separator** is exactly one blank line (Group C).
 *  4. **Empty data with an enabled flag drops the whole block** instead of
 *     emitting a header without content (Group D).
 *  5. **Per-block formatting** — chat history, memory and tool results follow
 *     the documented numbered-line shape (Group E).
 */
class NodeContextBuilderTest {

    private lateinit var builder: NodeContextBuilder

    private val originalTaskHeader = "--- Original Task ---"
    private val chatHistoryHeader = "--- Chat History ---"
    private val longTermMemoryHeader = "--- Long-Term Memory ---"
    private val toolResultsHeader = "--- Tool Results ---"
    private val previousNodeOutputHeader = "--- Previous Node Output ---"

    @Before
    fun setUp() {
        builder = NodeContextBuilder()
    }

    /**
     * Builds a [PipelineExecutionContext] in which every field carries
     * meaningful, non-empty data so a flag enabled by the test will actually
     * produce a visible block (otherwise the builder would correctly drop it,
     * which is verified separately in Group D).
     */
    private fun richContext(): PipelineExecutionContext = PipelineExecutionContext(
        originalUserMessage = "What's the weather in Madrid?",
        chatHistory = listOf(
            ChatMessage(
                id = 1L,
                sessionId = "s1",
                role = Role.USER,
                content = "Hi",
                timestamp = 0L,
            ),
            ChatMessage(
                id = 2L,
                sessionId = "s1",
                role = Role.AGENT,
                content = "Hello, how can I help?",
                timestamp = 1L,
            ),
        ),
        previousNodeOutput = "intermediate node payload",
        toolResults = listOf(
            ToolInvocationResult(toolName = "web.search", output = "23C, sunny"),
        ),
        memoryEntries = listOf(
            MemoryChunk(
                id = 10L,
                text = "User lives in Spain",
                embedding = FloatArray(0),
                timestamp = 0L,
            ),
        ),
    )

    /**
     * Builds a [NodeContextConfig] from the lower five bits of [mask]. Bit 0 is
     * `originalTask`, bit 1 — `chatHistory`, bit 2 — `longTermMemory`, bit 3 —
     * `toolResults`, bit 4 — `nodeInput`. The bit ordering is irrelevant to the
     * builder's contract (it consumes named flags) but documenting it here
     * keeps the per-mask debugging trail explicit.
     */
    private fun configFromMask(mask: Int): NodeContextConfig = NodeContextConfig(
        originalTask = mask and 0b00001 != 0,
        chatHistory = mask and 0b00010 != 0,
        longTermMemory = mask and 0b00100 != 0,
        toolResults = mask and 0b01000 != 0,
        nodeInput = mask and 0b10000 != 0,
    )

    // ─── Group A: every one of the 32 flag combinations ─────────────────────

    @Test
    fun `all 32 flag combinations include exactly the enabled block headers`() {
        val ctx = richContext()

        for (mask in 0 until 32) {
            val config = configFromMask(mask)
            val rendered = builder.build(config, ctx)

            assertHeaderPresence(
                rendered = rendered,
                mask = mask,
                header = originalTaskHeader,
                expected = config.originalTask,
            )
            assertHeaderPresence(
                rendered = rendered,
                mask = mask,
                header = chatHistoryHeader,
                expected = config.chatHistory,
            )
            assertHeaderPresence(
                rendered = rendered,
                mask = mask,
                header = longTermMemoryHeader,
                expected = config.longTermMemory,
            )
            assertHeaderPresence(
                rendered = rendered,
                mask = mask,
                header = toolResultsHeader,
                expected = config.toolResults,
            )
            assertHeaderPresence(
                rendered = rendered,
                mask = mask,
                header = previousNodeOutputHeader,
                expected = config.nodeInput,
            )
        }
    }

    @Test
    fun `all-flags-false combination produces empty string`() {
        // The validation layer (Phase 15-3/6) refuses to save such a config on
        // a context-aware node, but the builder itself must remain a pure
        // function and degrade gracefully — empty in, empty out.
        val rendered = builder.build(
            NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = false,
                longTermMemory = false,
                toolResults = false,
            ),
            richContext(),
        )

        assertEquals("", rendered)
    }

    // ─── Group B: deterministic block order ─────────────────────────────────

    @Test
    fun `block order is Original then Chat then Memory then Tools then PreviousOutput when all enabled`() {
        val rendered = builder.build(NodeContextConfig.ALL_ENABLED, richContext())

        val orderedHeaders = listOf(
            originalTaskHeader,
            chatHistoryHeader,
            longTermMemoryHeader,
            toolResultsHeader,
            previousNodeOutputHeader,
        )
        val indices = orderedHeaders.map { rendered.indexOf(it) }

        indices.forEachIndexed { i, idx ->
            assertTrue(
                "Header '${orderedHeaders[i]}' must be present in rendered output",
                idx >= 0,
            )
        }
        assertEquals(
            "Headers must appear in canonical order regardless of flag combinations",
            indices.sorted(),
            indices,
        )
    }

    @Test
    fun `block order is preserved across arbitrary subsets of enabled flags`() {
        val ctx = richContext()
        // Several arbitrary subsets — covers cases where the missing first
        // block could mask a regression in relative order of the remaining
        // blocks.
        val subsets = listOf(
            NodeContextConfig(
                originalTask = true, chatHistory = false, longTermMemory = true,
                toolResults = false, nodeInput = true,
            ),
            NodeContextConfig(
                originalTask = false, chatHistory = true, longTermMemory = false,
                toolResults = true, nodeInput = false,
            ),
            NodeContextConfig(
                originalTask = false, chatHistory = false, longTermMemory = true,
                toolResults = true, nodeInput = true,
            ),
            NodeContextConfig(
                originalTask = true, chatHistory = true, longTermMemory = false,
                toolResults = false, nodeInput = false,
            ),
        )

        subsets.forEach { config ->
            val rendered = builder.build(config, ctx)
            val presentInCanonicalOrder = listOfNotNull(
                originalTaskHeader.takeIf { config.originalTask },
                chatHistoryHeader.takeIf { config.chatHistory },
                longTermMemoryHeader.takeIf { config.longTermMemory },
                toolResultsHeader.takeIf { config.toolResults },
                previousNodeOutputHeader.takeIf { config.nodeInput },
            )
            val indices = presentInCanonicalOrder.map { rendered.indexOf(it) }
            assertEquals(
                "Headers in subset $config must remain in canonical order",
                indices.sorted(),
                indices,
            )
        }
    }

    // ─── Group C: separator between blocks ──────────────────────────────────

    @Test
    fun `enabled blocks are separated by exactly one blank line`() {
        val rendered = builder.build(NodeContextConfig.ALL_ENABLED, richContext())

        // Each adjacent pair of headers is divided by the canonical
        // separator: "<body>\n\n--- Next Header ---". Asserting on the
        // separator string itself is the most direct way to lock the contract
        // — extra whitespace would surface as a missing match.
        val pairs = listOf(
            chatHistoryHeader,
            longTermMemoryHeader,
            toolResultsHeader,
            previousNodeOutputHeader,
        )
        pairs.forEach { header ->
            assertTrue(
                "Header '$header' must be preceded by exactly one blank line",
                rendered.contains("\n\n$header"),
            )
        }

        // Triple-newline (== two blank lines) must never appear: that would
        // mean the builder is double-padding either side of the separator.
        assertFalse(
            "Builder must not emit double blank lines between blocks",
            rendered.contains("\n\n\n"),
        )
    }

    // ─── Group D: enabled flag with empty data → block omitted ──────────────

    @Test
    fun `enabled chatHistory flag with empty list does not emit header`() {
        val ctx = richContext().copy(chatHistory = emptyList())

        val rendered = builder.build(
            NodeContextConfig.ALL_ENABLED,
            ctx,
        )

        assertFalse(
            "Empty chat history must not produce a '--- Chat History ---' header",
            rendered.contains(chatHistoryHeader),
        )
    }

    @Test
    fun `enabled longTermMemory flag with empty list does not emit header`() {
        val ctx = richContext().copy(memoryEntries = emptyList())

        val rendered = builder.build(NodeContextConfig.ALL_ENABLED, ctx)

        assertFalse(rendered.contains(longTermMemoryHeader))
    }

    @Test
    fun `enabled toolResults flag with empty list does not emit header`() {
        val ctx = richContext().copy(toolResults = emptyList())

        val rendered = builder.build(NodeContextConfig.ALL_ENABLED, ctx)

        assertFalse(rendered.contains(toolResultsHeader))
    }

    @Test
    fun `originalTask flag with blank message does not emit header`() {
        val ctx = richContext().copy(originalUserMessage = "   ")

        val rendered = builder.build(NodeContextConfig.ALL_ENABLED, ctx)

        assertFalse(rendered.contains(originalTaskHeader))
    }

    @Test
    fun `nodeInput flag with blank previous output does not emit header`() {
        val ctx = richContext().copy(previousNodeOutput = "   ")

        val rendered = builder.build(NodeContextConfig.ALL_ENABLED, ctx)

        assertFalse(rendered.contains(previousNodeOutputHeader))
    }

    @Test
    fun `every flag enabled but every data field empty produces empty string`() {
        val rendered = builder.build(
            NodeContextConfig.ALL_ENABLED,
            PipelineExecutionContext(
                originalUserMessage = "",
                chatHistory = emptyList(),
                previousNodeOutput = "",
                toolResults = emptyList(),
                memoryEntries = emptyList(),
            ),
        )

        assertEquals("", rendered)
    }

    // ─── Group E: per-block body formatting ─────────────────────────────────

    @Test
    fun `chat history is rendered as one numbered line per message with role name`() {
        val rendered = builder.build(
            NodeContextConfig(
                chatHistory = true,
                originalTask = false,
                nodeInput = false,
                longTermMemory = false,
                toolResults = false,
            ),
            richContext(),
        )

        assertEquals(
            "$chatHistoryHeader\n1. USER: Hi\n2. AGENT: Hello, how can I help?",
            rendered,
        )
    }

    @Test
    fun `memory entries are rendered as one numbered line per chunk`() {
        val rendered = builder.build(
            NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = false,
                longTermMemory = true,
                toolResults = false,
            ),
            richContext().copy(
                memoryEntries = listOf(
                    MemoryChunk(1L, "fact one", FloatArray(0), 0L),
                    MemoryChunk(2L, "fact two", FloatArray(0), 0L),
                ),
            ),
        )

        assertEquals(
            "$longTermMemoryHeader\n1. fact one\n2. fact two",
            rendered,
        )
    }

    @Test
    fun `tool results are rendered as numbered lines prefixed by tool name`() {
        val rendered = builder.build(
            NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = false,
                longTermMemory = false,
                toolResults = true,
            ),
            richContext().copy(
                toolResults = listOf(
                    ToolInvocationResult("web.search", "result A"),
                    ToolInvocationResult("calendar.read", "result B"),
                ),
            ),
        )

        assertEquals(
            "$toolResultsHeader\n1. web.search: result A\n2. calendar.read: result B",
            rendered,
        )
    }

    @Test
    fun `originalTask body is the trimmed user message`() {
        val rendered = builder.build(
            NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = false,
                longTermMemory = false,
                toolResults = false,
            ),
            richContext().copy(originalUserMessage = "  do the thing  "),
        )

        assertEquals("$originalTaskHeader\ndo the thing", rendered)
    }

    @Test
    fun `previousNodeOutput body is the trimmed input string`() {
        val rendered = builder.build(
            NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
            richContext().copy(previousNodeOutput = "  upstream payload\n"),
        )

        assertEquals("$previousNodeOutputHeader\nupstream payload", rendered)
    }

    private fun assertHeaderPresence(
        rendered: String,
        mask: Int,
        header: String,
        expected: Boolean,
    ) {
        val actual = rendered.contains(header)
        if (expected != actual) {
            val maskBin = mask.toString(2).padStart(5, '0')
            val verb = if (expected) "must contain" else "must NOT contain"
            throw AssertionError(
                "mask=0b$maskBin output $verb header '$header'.\nRendered output:\n$rendered",
            )
        }
    }
}
