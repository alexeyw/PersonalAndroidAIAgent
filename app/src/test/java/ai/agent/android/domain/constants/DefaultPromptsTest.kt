package ai.agent.android.domain.constants

import ai.agent.android.domain.models.NodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke + contract coverage for [DefaultPrompts].
 *
 * Two layers of verification:
 *  1. **Smoke** — every constant is non-blank after [String.trim]; protects against
 *     accidental empty-string regressions.
 *  2. **Contract** — every wrap-template (`*_TEMPLATE`) carries the placeholders
 *     its consumer executor depends on. Drift here would silently leave literal
 *     `${'$'}KEY` tokens in the prompt sent to the LLM.
 */
class DefaultPromptsTest {

    @Test
    fun `given system prompt prefix when read then non-blank`() {
        assertTrue(DefaultPrompts.SYSTEM_PROMPT_PREFIX.trim().isNotEmpty())
    }

    @Test
    fun `given tool usage instruction when read then contains tool list placeholder`() {
        val text = DefaultPrompts.TOOL_USAGE_INSTRUCTION
        assertTrue(text.trim().isNotEmpty())
        assertTrue("Should advertise the [TOOL_LIST] placeholder", text.contains("[TOOL_LIST]"))
    }

    @Test
    fun `given top-level system prompts when read then all non-blank`() {
        val prompts = listOf(
            DefaultPrompts.INTENT_ROUTER_PROMPT,
            DefaultPrompts.DECOMPOSITION_PROMPT,
            DefaultPrompts.EVALUATION_PROMPT,
            DefaultPrompts.SUMMARY_PROMPT,
            DefaultPrompts.OUTPUT_FORMAT_PROMPT,
            DefaultPrompts.CLARIFICATION_PROMPT,
        )
        prompts.forEachIndexed { idx, prompt ->
            assertTrue("Prompt #$idx must be non-blank", prompt.trim().isNotEmpty())
        }
    }

    @Test
    fun `given per-node system fallbacks when read then all non-blank`() {
        val fallbacks = mapOf(
            "LiteRt" to DefaultPrompts.LiteRt.SYSTEM_FALLBACK,
            "Cloud" to DefaultPrompts.Cloud.SYSTEM_FALLBACK,
            "System" to DefaultPrompts.System.SYSTEM_FALLBACK,
            "Output" to DefaultPrompts.Output.SYSTEM_FALLBACK,
            "Summary" to DefaultPrompts.Summary.SYSTEM_FALLBACK,
            "IntentRouter" to DefaultPrompts.IntentRouter.SYSTEM_FALLBACK,
            "Decomposition" to DefaultPrompts.Decomposition.SYSTEM_FALLBACK,
            "Evaluation" to DefaultPrompts.Evaluation.SYSTEM_FALLBACK,
            "Clarification" to DefaultPrompts.Clarification.SYSTEM_FALLBACK,
        )
        fallbacks.forEach { (name, value) ->
            assertTrue("$name.SYSTEM_FALLBACK must be non-blank", value.trim().isNotEmpty())
        }
    }

    @Test
    fun `given output formatting template when read then contains required placeholders`() {
        val template = DefaultPrompts.Output.FORMATTING_TEMPLATE
        assertTrue(template.contains("\$NODE_SYSTEM_PROMPT"))
        assertTrue(template.contains("\$INPUT_TEXT"))
    }

    @Test
    fun `given summary synthesis template when read then contains required placeholders`() {
        val template = DefaultPrompts.Summary.SYNTHESIS_TEMPLATE
        assertTrue(template.contains("\$NODE_SYSTEM_PROMPT"))
        assertTrue(template.contains("\$ORIGINAL_TASK"))
        assertTrue(template.contains("\$RESULTS_OF_SUBTASKS"))
    }

    @Test
    fun `given tool auto-select template when read then contains required placeholders`() {
        val template = DefaultPrompts.Tool.AUTO_SELECT_TEMPLATE
        assertTrue(template.contains("\$AVAILABLE_TOOLS"))
        assertTrue(template.contains("\$INPUT_TEXT"))
    }

    @Test
    fun `given tool argument-generation template when read then contains required placeholders`() {
        val template = DefaultPrompts.Tool.ARGUMENT_GENERATION_TEMPLATE
        assertTrue(template.contains("\$TOOL_NAME"))
        assertTrue(template.contains("\$TOOL_DESCRIPTION"))
        assertTrue(template.contains("\$TOOL_PARAMETERS"))
        assertTrue(template.contains("\$INPUT_TEXT"))
    }

    @Test
    fun `given if-condition evaluation template when read then contains required placeholders`() {
        val template = DefaultPrompts.IfCondition.EVALUATION_TEMPLATE
        assertTrue(template.contains("\$CONDITION_PROMPT"))
        assertTrue(template.contains("\$INPUT_TEXT"))
    }

    @Test
    fun `given queue processor subtask instruction when read then non-blank and contains directive`() {
        val text = DefaultPrompts.QueueProcessor.SUBTASK_INSTRUCTION
        assertTrue(text.trim().isNotEmpty())
        assertTrue("Should keep the CRITICAL INSTRUCTION marker", text.contains("CRITICAL INSTRUCTION"))
    }

    @Test
    fun `given template with placeholders when renderTemplate then substitutes from map`() {
        val rendered = DefaultPrompts.renderTemplate(
            template = "Hello \$NAME, welcome to \$PLACE.",
            values = mapOf("NAME" to "Ada", "PLACE" to "Earth"),
        )
        assertEquals("Hello Ada, welcome to Earth.", rendered)
    }

    @Test
    fun `given value contains other placeholder token when renderTemplate then does not rescan substituted text`() {
        // Regression guard for the chained-replace bug: when a value contains a literal `$KEY`
        // that matches a later substitution, the rescan-free single-pass renderer must keep
        // that token verbatim instead of rewriting it.
        val rendered = DefaultPrompts.renderTemplate(
            template = "[\$NODE_SYSTEM_PROMPT]\n[\$ORIGINAL_TASK]",
            values = mapOf(
                "NODE_SYSTEM_PROMPT" to "Echo back \$ORIGINAL_TASK literally",
                "ORIGINAL_TASK" to "the question",
            ),
        )
        assertEquals(
            "[Echo back \$ORIGINAL_TASK literally]\n[the question]",
            rendered,
        )
    }

    @Test
    fun `given unknown placeholder when renderTemplate then leaves token verbatim`() {
        val rendered = DefaultPrompts.renderTemplate(
            template = "Known: \$KNOWN; Unknown: \$NOT_A_REAL_KEY",
            values = mapOf("KNOWN" to "ok"),
        )
        assertEquals("Known: ok; Unknown: \$NOT_A_REAL_KEY", rendered)
    }

    @Test
    fun `given lowercase or numeric tokens when renderTemplate then ignores them`() {
        // The placeholder regex requires an uppercase/underscore lead, so $name and $50 are
        // not placeholders — keeping `$50` reachable as currency etc.
        val rendered = DefaultPrompts.renderTemplate(
            template = "\$name pays \$50",
            values = mapOf("name" to "Bob", "50" to "USD 50"),
        )
        assertEquals("\$name pays \$50", rendered)
    }

    @Test
    fun `given known node types when getDefaultPromptForNodeType then returns canonical prompt`() {
        assertEquals(
            DefaultPrompts.INTENT_ROUTER_PROMPT,
            DefaultPrompts.getDefaultPromptForNodeType(NodeType.INTENT_ROUTER),
        )
        assertEquals(
            DefaultPrompts.DECOMPOSITION_PROMPT,
            DefaultPrompts.getDefaultPromptForNodeType(NodeType.DECOMPOSITION),
        )
        assertEquals(DefaultPrompts.EVALUATION_PROMPT, DefaultPrompts.getDefaultPromptForNodeType(NodeType.EVALUATION))
        assertEquals(DefaultPrompts.SUMMARY_PROMPT, DefaultPrompts.getDefaultPromptForNodeType(NodeType.SUMMARY))
        assertEquals(DefaultPrompts.OUTPUT_FORMAT_PROMPT, DefaultPrompts.getDefaultPromptForNodeType(NodeType.OUTPUT))
        assertEquals(
            DefaultPrompts.CLARIFICATION_PROMPT,
            DefaultPrompts.getDefaultPromptForNodeType(NodeType.CLARIFICATION),
        )
        assertEquals(DefaultPrompts.SYSTEM_PROMPT_PREFIX, DefaultPrompts.getDefaultPromptForNodeType(NodeType.LITE_RT))
        assertEquals(DefaultPrompts.SYSTEM_PROMPT_PREFIX, DefaultPrompts.getDefaultPromptForNodeType(NodeType.CLOUD))
    }

    @Test
    fun `given control-flow node types when getDefaultPromptForNodeType then returns null`() {
        assertNull(DefaultPrompts.getDefaultPromptForNodeType(NodeType.INPUT))
        assertNull(DefaultPrompts.getDefaultPromptForNodeType(NodeType.IF_CONDITION))
        assertNull(DefaultPrompts.getDefaultPromptForNodeType(NodeType.TOOL))
        assertNull(DefaultPrompts.getDefaultPromptForNodeType(NodeType.QUEUE_PROCESSOR))
    }

    @Test
    fun `given every NodeType when queried then resolver does not throw`() {
        // Catches the case where a new NodeType is added without considering the default-prompt mapping;
        // either branch (mapped value or null) is acceptable, but the function must remain exhaustive.
        NodeType.values().forEach { type ->
            val result = DefaultPrompts.getDefaultPromptForNodeType(type)
            // Result may legitimately be null for control-flow types; just exercise the branch.
            if (result != null) assertNotNull(result)
        }
    }
}
