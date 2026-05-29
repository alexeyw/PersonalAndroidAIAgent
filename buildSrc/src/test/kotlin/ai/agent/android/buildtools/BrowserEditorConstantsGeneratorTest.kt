package ai.agent.android.buildtools

import ai.agent.android.buildtools.BrowserEditorConstantsGenerator.GenerationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BrowserEditorConstantsGenerator].
 *
 * These exercise every parser, emitter, the marker injection round-trip,
 * idempotency, drift detection, and the metadata-vs-source cross-checks.
 * Run with `./gradlew -p buildSrc test`.
 */
class BrowserEditorConstantsGeneratorTest {

    // ---- Fixtures mirroring the shape of the real Android sources ---- //

    private val nodeTypeSource = """
        package ai.agent.android.domain.models

        /** doc with a reference to [NodeType.OUTPUT] that must be ignored */
        enum class NodeType {
            LITE_RT,
            CLOUD,
            TOOL,
            IF_CONDITION,
            INTENT_ROUTER,
            DECOMPOSITION,
            QUEUE_PROCESSOR,
            EVALUATION,
            SUMMARY,
            CLARIFICATION,
            INPUT,
            OUTPUT,
        }
    """.trimIndent()

    private val promptModuleSource = """
        @Module
        abstract class PromptTemplateModule {
            @Binds @IntoSet
            abstract fun bindDateVariableProvider(impl: DateVariableProvider): PromptVariableProvider
            @Binds @IntoSet
            abstract fun bindTimeVariableProvider(impl: TimeVariableProvider): PromptVariableProvider
            @Binds @IntoSet
            abstract fun bindToolsVariableProvider(impl: ToolsVariableProvider): PromptVariableProvider
        }
    """.trimIndent()

    private val toolsModuleSource = """
        @Module
        abstract class LocalToolsModule {
            @Binds @IntoMap @StringKey(ScheduleTaskExecutor.TOOL_NAME)
            abstract fun bindScheduleTaskExecutor(executor: ScheduleTaskExecutor): LocalToolExecutor
            @Binds @IntoMap @StringKey(DelegateTaskExecutor.TOOL_NAME)
            abstract fun bindDelegateTaskExecutor(executor: DelegateTaskExecutor): LocalToolExecutor
            @Binds @IntoMap @StringKey(SearchTool.TOOL_NAME)
            abstract fun bindSearchToolExecutor(executor: SearchToolExecutor): LocalToolExecutor
        }
    """.trimIndent()

    private val defaultPromptsSource = """
        object DefaultPrompts {
            const val SYSTEM_PROMPT_PREFIX = "You are a helpful AI assistant running on an Android device."

            /** kdoc */
            const val INTENT_ROUTER_PROMPT = "You are an Intent Router. " +
                "Output one of:\n" +
                "- Simple (if it's a greeting)"

            const val DECOMPOSITION_PROMPT = "Decompose."
            const val EVALUATION_PROMPT = "Evaluate."
            const val SUMMARY_PROMPT = "Summarize."
            const val OUTPUT_FORMAT_PROMPT = "Format."
            const val CLARIFICATION_PROMPT = "Clarify with \"quotes\" and a brace }."

            object LiteRt {
                const val SYSTEM_FALLBACK = "You are a helpful AI assistant."
            }
        }
    """.trimIndent()

    private val classSources = mapOf(
        "DateVariableProvider" to providerSource("DATE"),
        "TimeVariableProvider" to providerSource("TIME"),
        "ToolsVariableProvider" to providerSource("TOOLS"),
        "ScheduleTaskExecutor" to toolSource("schedule_task"),
        "DelegateTaskExecutor" to toolSource("delegate_task"),
        "SearchTool" to toolSource("search_tool"),
    )

    private fun providerSource(key: String) = """
        class Provider : PromptVariableProvider {
            override fun key(): String = KEY
            companion object { const val KEY = "$key" }
        }
    """.trimIndent()

    private fun toolSource(name: String) = """
        class Tool {
            companion object { const val TOOL_NAME = "$name" }
        }
    """.trimIndent()

    // ---- Parsers ---- //

    @Test
    fun `parseNodeTypeNames returns constants in declaration order ignoring kdoc`() {
        val names = BrowserEditorConstantsGenerator.parseNodeTypeNames(nodeTypeSource)
        assertEquals(
            listOf(
                "LITE_RT", "CLOUD", "TOOL", "IF_CONDITION", "INTENT_ROUTER", "DECOMPOSITION",
                "QUEUE_PROCESSOR", "EVALUATION", "SUMMARY", "CLARIFICATION", "INPUT", "OUTPUT",
            ),
            names,
        )
    }

    @Test(expected = GenerationException::class)
    fun `parseNodeTypeNames throws when enum is absent`() {
        BrowserEditorConstantsGenerator.parseNodeTypeNames("package x")
    }

    @Test
    fun `parseBoundProviderClassNames returns binds in order`() {
        assertEquals(
            listOf("DateVariableProvider", "TimeVariableProvider", "ToolsVariableProvider"),
            BrowserEditorConstantsGenerator.parseBoundProviderClassNames(promptModuleSource),
        )
    }

    @Test
    fun `parseKeyConst extracts the KEY literal`() {
        assertEquals("DATE", BrowserEditorConstantsGenerator.parseKeyConst(providerSource("DATE")))
    }

    @Test
    fun `parseBoundToolClassNames returns referenced classes in order`() {
        assertEquals(
            listOf("ScheduleTaskExecutor", "DelegateTaskExecutor", "SearchTool"),
            BrowserEditorConstantsGenerator.parseBoundToolClassNames(toolsModuleSource),
        )
    }

    @Test
    fun `parseToolNameConst extracts the TOOL_NAME literal`() {
        assertEquals("search_tool", BrowserEditorConstantsGenerator.parseToolNameConst(toolSource("search_tool")))
    }

    @Test
    fun `parseStringConst evaluates concatenation and escapes`() {
        val value = BrowserEditorConstantsGenerator.parseStringConst(defaultPromptsSource, "INTENT_ROUTER_PROMPT")
        assertEquals("You are an Intent Router. Output one of:\n- Simple (if it's a greeting)", value)
    }

    @Test
    fun `parseStringConst handles embedded quotes and braces`() {
        val value = BrowserEditorConstantsGenerator.parseStringConst(defaultPromptsSource, "CLARIFICATION_PROMPT")
        assertEquals("Clarify with \"quotes\" and a brace }.", value)
    }

    @Test
    fun `parseStringConst stops at the next declaration boundary`() {
        // SYSTEM_PROMPT_PREFIX must not absorb the following INTENT_ROUTER_PROMPT literals.
        val value = BrowserEditorConstantsGenerator.parseStringConst(defaultPromptsSource, "SYSTEM_PROMPT_PREFIX")
        assertEquals("You are a helpful AI assistant running on an Android device.", value)
    }

    @Test(expected = GenerationException::class)
    fun `parseStringConst throws for unknown constant`() {
        BrowserEditorConstantsGenerator.parseStringConst(defaultPromptsSource, "NOPE")
    }

    // ---- String helpers ---- //

    @Test
    fun `unescapeKotlin handles standard escapes and unicode`() {
        assertEquals(
            "a\nb\tc\"d\\e\$é",
            BrowserEditorConstantsGenerator.unescapeKotlin("a\\nb\\tc\\\"d\\\\e\\\$\\u00e9"),
        )
    }

    @Test
    fun `jsonString escapes control characters and keeps unicode`() {
        assertEquals("\"a\\nb\\\"c\\\\d\"", BrowserEditorConstantsGenerator.jsonString("a\nb\"c\\d"))
        assertEquals("\"🧠\"", BrowserEditorConstantsGenerator.jsonString("🧠"))
    }

    // ---- Emitters & cross-checks ---- //

    @Test
    fun `emitNodeTypes lists palette order and includes all enum ids`() {
        val enumNames = BrowserEditorConstantsGenerator.parseNodeTypeNames(nodeTypeSource)
        val js = BrowserEditorConstantsGenerator.emitNodeTypes(enumNames)
        assertTrue(js.contains("const NODE_TYPES = ["))
        assertTrue(js.contains("{ id: \"INPUT\", label: \"Input\", color: \"#607D8B\", icon: \"▶\", inputs: 0, outputs: 1 },"))
        // INPUT must appear before LITE_RT (palette order, not enum order).
        assertTrue(js.indexOf("\"INPUT\"") < js.indexOf("\"LITE_RT\""))
    }

    @Test(expected = GenerationException::class)
    fun `emitNodeTypes throws when an enum id has no metadata`() {
        BrowserEditorConstantsGenerator.emitNodeTypes(listOf("INPUT", "BRAND_NEW_TYPE"))
    }

    @Test
    fun `emitPromptVariables joins keys`() {
        assertEquals(
            "    const PROMPT_VARIABLES = [\"DATE\", \"TIME\", \"TOOLS\"];",
            BrowserEditorConstantsGenerator.emitPromptVariables(listOf("DATE", "TIME", "TOOLS")),
        )
    }

    @Test
    fun `emitAvailableTools emits display order`() {
        val js = BrowserEditorConstantsGenerator.emitAvailableTools(
            listOf("schedule_task", "search_tool", "delegate_task"),
        )
        // Display order from TOOL_META: search, delegate, schedule.
        assertTrue(js.indexOf("search_tool") < js.indexOf("delegate_task"))
        assertTrue(js.indexOf("delegate_task") < js.indexOf("schedule_task"))
    }

    @Test(expected = GenerationException::class)
    fun `emitAvailableTools throws on unknown tool id`() {
        BrowserEditorConstantsGenerator.emitAvailableTools(listOf("search_tool", "mystery_tool"))
    }

    @Test
    fun `emitDefaultPrompts references prefix and json-encodes prompts`() {
        val js = BrowserEditorConstantsGenerator.emitDefaultPrompts(defaultPromptsSource)
        assertTrue(js.contains("const SYSTEM_PROMPT_PREFIX = \"You are a helpful AI assistant running on an Android device.\";"))
        assertTrue(js.contains("LITE_RT: SYSTEM_PROMPT_PREFIX,"))
        assertTrue(js.contains("CLOUD: SYSTEM_PROMPT_PREFIX,"))
        assertTrue(js.contains("INTENT_ROUTER: \"You are an Intent Router. Output one of:\\n- Simple (if it's a greeting)\","))
    }

    // ---- Marker injection ---- //

    private fun wrap(block: String, body: String) =
        BrowserEditorConstantsGenerator.openMarker(block) + "\n" + body + "\n" +
            BrowserEditorConstantsGenerator.closeMarker(block)

    @Test
    fun `injectBlock replaces content and preserves markers and surroundings`() {
        val html = "head\n" + wrap("NODE_TYPES", "    const NODE_TYPES = [];") + "\ntail"
        val out = BrowserEditorConstantsGenerator.injectBlock(html, "NODE_TYPES", "    const NODE_TYPES = [1];")
        assertTrue(out.startsWith("head\n"))
        assertTrue(out.endsWith("\ntail"))
        assertTrue(out.contains("const NODE_TYPES = [1];"))
        assertFalse(out.contains("const NODE_TYPES = [];"))
        assertTrue(out.contains(BrowserEditorConstantsGenerator.openMarker("NODE_TYPES")))
        assertTrue(out.contains(BrowserEditorConstantsGenerator.closeMarker("NODE_TYPES")))
    }

    @Test
    fun `extractBlock round-trips injectBlock`() {
        val html = "x\n" + wrap("AVAILABLE_TOOLS", "old") + "\ny"
        val injected = BrowserEditorConstantsGenerator.injectBlock(html, "AVAILABLE_TOOLS", "    new content")
        assertEquals("    new content", BrowserEditorConstantsGenerator.extractBlock(injected, "AVAILABLE_TOOLS"))
    }

    @Test
    fun `extractBlock returns null when block missing`() {
        assertNull(BrowserEditorConstantsGenerator.extractBlock("nothing here", "NODE_TYPES"))
    }

    @Test(expected = GenerationException::class)
    fun `injectBlock throws when marker missing`() {
        BrowserEditorConstantsGenerator.injectBlock("no markers", "NODE_TYPES", "x")
    }

    // ---- End-to-end render / drift ---- //

    private fun skeletonHtml(): String = buildString {
        append("<script>\n")
        append(wrap("NODE_TYPES", "    const NODE_TYPES = [];")).append("\n")
        append(wrap("PROMPT_VARIABLES", "    const PROMPT_VARIABLES = [];")).append("\n")
        append(wrap("AVAILABLE_TOOLS", "    const AVAILABLE_TOOLS = [];")).append("\n")
        append(wrap("DEFAULT_PROMPTS", "    const SYSTEM_PROMPT_PREFIX = \"\";")).append("\n")
        append("</script>\n")
    }

    private fun render(html: String) = BrowserEditorConstantsGenerator.render(
        html = html,
        nodeTypeSource = nodeTypeSource,
        defaultPromptsSource = defaultPromptsSource,
        promptTemplateModuleSource = promptModuleSource,
        localToolsModuleSource = toolsModuleSource,
        classSources = classSources,
    )

    private fun drift(html: String) = BrowserEditorConstantsGenerator.drift(
        html = html,
        nodeTypeSource = nodeTypeSource,
        defaultPromptsSource = defaultPromptsSource,
        promptTemplateModuleSource = promptModuleSource,
        localToolsModuleSource = toolsModuleSource,
        classSources = classSources,
    )

    @Test
    fun `render is idempotent`() {
        val once = render(skeletonHtml())
        val twice = render(once)
        assertEquals(once, twice)
    }

    @Test
    fun `render populates every block`() {
        val out = render(skeletonHtml())
        assertTrue(out.contains("{ id: \"INPUT\""))
        assertTrue(out.contains("const PROMPT_VARIABLES = [\"DATE\", \"TIME\", \"TOOLS\"];"))
        assertTrue(out.contains("{ id: \"search_tool\", label: \"Search (Wikipedia)\" },"))
        assertTrue(out.contains("INTENT_ROUTER: \"You are an Intent Router."))
    }

    @Test
    fun `drift reports all blocks for an unpopulated skeleton then none after render`() {
        assertEquals(BrowserEditorConstantsGenerator.BLOCKS.toSet(), drift(skeletonHtml()).toSet())
        assertTrue(drift(render(skeletonHtml())).isEmpty())
    }

    @Test
    fun `drift pinpoints only the stale block`() {
        val rendered = render(skeletonHtml())
        // Corrupt just the PROMPT_VARIABLES block.
        val tampered = BrowserEditorConstantsGenerator.injectBlock(
            rendered, "PROMPT_VARIABLES", "    const PROMPT_VARIABLES = [\"STALE\"];",
        )
        assertEquals(listOf("PROMPT_VARIABLES"), drift(tampered))
    }

    @Test(expected = GenerationException::class)
    fun `render throws when a bound provider source is missing`() {
        BrowserEditorConstantsGenerator.render(
            html = skeletonHtml(),
            nodeTypeSource = nodeTypeSource,
            defaultPromptsSource = defaultPromptsSource,
            promptTemplateModuleSource = promptModuleSource,
            localToolsModuleSource = toolsModuleSource,
            classSources = emptyMap(),
        )
    }
}
