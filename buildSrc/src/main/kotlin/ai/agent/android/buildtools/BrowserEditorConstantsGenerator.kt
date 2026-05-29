package ai.agent.android.buildtools

/**
 * Derives the JavaScript constant blocks that the browser pipeline editor
 * (`pipeline-editor.html`) mirrors from the Android domain layer, and injects
 * them between dedicated `AUTO-GEN` markers.
 *
 * The editor is a standalone single-file tool that deliberately duplicates a
 * slice of the app's data so it can run with no build step. Before Phase 24 /
 * Task 8 that duplication was kept in sync purely by review, and it drifted
 * (Task 6 had to re-align the prompt constants). This generator removes the
 * human from that loop for the drift-prone blocks:
 *
 *  - **`NODE_TYPES`** — the set and ordering guarantee comes from
 *    [parseNodeTypeNames] reading `domain/models/NodeType.kt`; the editor-only
 *    presentation metadata (label / colour / icon / port counts / palette
 *    order) has no Kotlin source of truth and therefore lives in
 *    [NODE_TYPE_META]. The two are cross-checked: adding or removing a
 *    `NodeType` without updating [NODE_TYPE_META] fails generation.
 *  - **`PROMPT_VARIABLES`** — fully derived: the active provider set and order
 *    come from the `@Binds @IntoSet` declarations in `di/PromptTemplateModule.kt`,
 *    each resolved to its `KEY` constant in the `*VariableProvider.kt` files under `data/prompt/`.
 *  - **`AVAILABLE_TOOLS`** — ids come from the `TOOL_NAME` constants referenced
 *    by `di/LocalToolsModule.kt`; the human-facing labels have no Kotlin source
 *    of truth and live in [TOOL_META], again cross-checked against the ids.
 *  - **`DEFAULT_SYSTEM_PROMPTS`** (and `SYSTEM_PROMPT_PREFIX`) — the prompt
 *    texts are evaluated straight out of `domain/constants/DefaultPrompts.kt`.
 *
 * Everything outside the markers (drawflow wiring, popup structure, form
 * rendering, the rest of the editor's UI logic) is intentionally left untouched
 * and remains hand-maintained.
 *
 * The entry points are [render] (pure transform used by both Gradle tasks) and
 * [drift] (per-block comparison used by the verify task to report which blocks
 * are out of date). Every helper is a pure string transform to keep the module
 * unit-testable.
 */
object BrowserEditorConstantsGenerator {

    /** Thrown when the sources cannot be parsed or contradict [NODE_TYPE_META] / [TOOL_META]. */
    class GenerationException(message: String) : RuntimeException(message)

    /**
     * Editor-only presentation metadata for one [ai.agent.android.domain.models.NodeType],
     * in the order the node should appear in the editor palette.
     *
     * @property id Must equal a `NodeType` enum constant name.
     * @property label Human-facing palette label.
     * @property color Palette accent colour (hex).
     * @property icon Single-glyph palette icon.
     * @property inputs Number of input ports the drawflow node exposes.
     * @property outputs Number of output ports the drawflow node exposes.
     */
    data class NodeTypeMeta(
        val id: String,
        val label: String,
        val color: String,
        val icon: String,
        val inputs: Int,
        val outputs: Int,
    )

    /**
     * Palette presentation table for [emitNodeTypes]. Order here is the editor
     * palette order (INPUT / OUTPUT first), which deliberately differs from the
     * `NodeType` enum declaration order. The *set* of ids must equal the enum.
     */
    val NODE_TYPE_META: List<NodeTypeMeta> = listOf(
        NodeTypeMeta("INPUT", "Input", "#607D8B", "▶", 0, 1),
        NodeTypeMeta("OUTPUT", "Output", "#F44336", "⏹", 1, 0),
        NodeTypeMeta("LITE_RT", "LiteRT", "#4CAF50", "🧠", 1, 1),
        NodeTypeMeta("CLOUD", "Cloud", "#2196F3", "☁", 1, 1),
        NodeTypeMeta("TOOL", "Tool", "#FF9800", "🛠", 1, 1),
        NodeTypeMeta("IF_CONDITION", "If Condition", "#FFC107", "❓", 1, 2),
        NodeTypeMeta("INTENT_ROUTER", "Intent Router", "#E91E63", "🧭", 1, 1),
        NodeTypeMeta("DECOMPOSITION", "Decomposition", "#3F51B5", "🧩", 1, 1),
        NodeTypeMeta("QUEUE_PROCESSOR", "Queue Processor", "#795548", "🔁", 1, 2),
        NodeTypeMeta("EVALUATION", "Evaluation", "#009688", "✅", 1, 1),
        NodeTypeMeta("SUMMARY", "Summary", "#8BC34A", "📝", 1, 1),
        NodeTypeMeta("CLARIFICATION", "Clarification", "#9C27B0", "💬", 1, 1),
    )

    /**
     * Human-facing labels for the local tools, keyed by tool id (`TOOL_NAME`),
     * in editor display order. The *set* of ids must equal the tool names
     * discovered from `di/LocalToolsModule.kt`.
     */
    val TOOL_META: List<Pair<String, String>> = listOf(
        "search_tool" to "Search (Wikipedia)",
        "delegate_task" to "Delegate Task",
        "schedule_task" to "Schedule Task",
    )

    /**
     * Mapping from the JS `DEFAULT_SYSTEM_PROMPTS` keys to the `DefaultPrompts`
     * constant that supplies each one, mirroring
     * `DefaultPrompts.getDefaultPromptForNodeType`. `LITE_RT` / `CLOUD` reference
     * the shared `SYSTEM_PROMPT_PREFIX` and are emitted by reference, so they are
     * not listed here.
     */
    private val PROMPT_CONST_BY_JS_KEY: List<Pair<String, String>> = listOf(
        "INTENT_ROUTER" to "INTENT_ROUTER_PROMPT",
        "DECOMPOSITION" to "DECOMPOSITION_PROMPT",
        "EVALUATION" to "EVALUATION_PROMPT",
        "SUMMARY" to "SUMMARY_PROMPT",
        "OUTPUT" to "OUTPUT_FORMAT_PROMPT",
        "CLARIFICATION" to "CLARIFICATION_PROMPT",
    )

    /** Identifiers of the four auto-generated blocks, used in the `AUTO-GEN` markers. */
    const val BLOCK_NODE_TYPES = "NODE_TYPES"
    const val BLOCK_PROMPT_VARIABLES = "PROMPT_VARIABLES"
    const val BLOCK_AVAILABLE_TOOLS = "AVAILABLE_TOOLS"
    const val BLOCK_DEFAULT_PROMPTS = "DEFAULT_PROMPTS"

    /** Every block this generator owns, in a stable reporting order. */
    val BLOCKS: List<String> = listOf(
        BLOCK_NODE_TYPES,
        BLOCK_PROMPT_VARIABLES,
        BLOCK_AVAILABLE_TOOLS,
        BLOCK_DEFAULT_PROMPTS,
    )

    // ------------------------------------------------------------------ //
    //  Public entry points
    // ------------------------------------------------------------------ //

    /**
     * Returns [html] with all four auto-generated blocks regenerated from the
     * supplied Android sources. Pure and idempotent: `render(render(x)) == render(x)`.
     *
     * @param html Current `pipeline-editor.html` content (must already contain
     * the `AUTO-GEN` markers for every entry in [BLOCKS]).
     * @param nodeTypeSource Content of `domain/models/NodeType.kt`.
     * @param defaultPromptsSource Content of `domain/constants/DefaultPrompts.kt`.
     * @param promptTemplateModuleSource Content of `di/PromptTemplateModule.kt`.
     * @param localToolsModuleSource Content of `di/LocalToolsModule.kt`.
     * @param classSources Map from class simple-name to its `.kt` source, covering
     * every bound `PromptVariableProvider` and every class referenced as
     * `<Class>.TOOL_NAME` in the tools module.
     * @return The rewritten HTML.
     * @throws GenerationException on parse failure or metadata/source mismatch.
     */
    fun render(
        html: String,
        nodeTypeSource: String,
        defaultPromptsSource: String,
        promptTemplateModuleSource: String,
        localToolsModuleSource: String,
        classSources: Map<String, String>,
    ): String {
        val blocks = generateBlocks(
            nodeTypeSource = nodeTypeSource,
            defaultPromptsSource = defaultPromptsSource,
            promptTemplateModuleSource = promptTemplateModuleSource,
            localToolsModuleSource = localToolsModuleSource,
            classSources = classSources,
        )
        var result = html
        for ((name, content) in blocks) {
            result = injectBlock(result, name, content)
        }
        return result
    }

    /**
     * Returns the names of the blocks whose freshly-generated content differs
     * from what is currently committed in [html]. Empty list ⇒ no drift.
     *
     * Drives `verifyBrowserEditorConstants`: comparing per block lets the task
     * tell the user exactly which constants are stale.
     */
    fun drift(
        html: String,
        nodeTypeSource: String,
        defaultPromptsSource: String,
        promptTemplateModuleSource: String,
        localToolsModuleSource: String,
        classSources: Map<String, String>,
    ): List<String> {
        val blocks = generateBlocks(
            nodeTypeSource = nodeTypeSource,
            defaultPromptsSource = defaultPromptsSource,
            promptTemplateModuleSource = promptTemplateModuleSource,
            localToolsModuleSource = localToolsModuleSource,
            classSources = classSources,
        )
        return blocks.filter { (name, content) -> extractBlock(html, name) != content }
            .map { it.first }
    }

    /** Builds every block's generated content, keyed by block name (no HTML mutation). */
    private fun generateBlocks(
        nodeTypeSource: String,
        defaultPromptsSource: String,
        promptTemplateModuleSource: String,
        localToolsModuleSource: String,
        classSources: Map<String, String>,
    ): List<Pair<String, String>> {
        val enumNames = parseNodeTypeNames(nodeTypeSource)
        val providerKeys = parseBoundProviderClassNames(promptTemplateModuleSource).map { className ->
            val src = classSources[className]
                ?: throw GenerationException("Missing source for prompt provider class '$className'")
            parseKeyConst(src)
        }
        val toolIds = parseBoundToolClassNames(localToolsModuleSource).map { className ->
            val src = classSources[className]
                ?: throw GenerationException("Missing source for tool class '$className'")
            parseToolNameConst(src)
        }
        return listOf(
            BLOCK_NODE_TYPES to emitNodeTypes(enumNames),
            BLOCK_PROMPT_VARIABLES to emitPromptVariables(providerKeys),
            BLOCK_AVAILABLE_TOOLS to emitAvailableTools(toolIds),
            BLOCK_DEFAULT_PROMPTS to emitDefaultPrompts(defaultPromptsSource),
        )
    }

    // ------------------------------------------------------------------ //
    //  Parsers
    // ------------------------------------------------------------------ //

    private val ENUM_CONSTANT = Regex("""(?m)^\s*([A-Z_][A-Z0-9_]*)\s*,\s*$""")
    private val PROVIDER_BIND = Regex("""fun\s+bind\w+\(\s*impl:\s*(\w+)\s*\)\s*:\s*PromptVariableProvider""")
    private val KEY_CONST = Regex("""const\s+val\s+KEY\s*=\s*"([^"]+)"""")
    private val TOOL_STRING_KEY = Regex("""@StringKey\(\s*(\w+)\.TOOL_NAME\s*\)""")
    private val TOOL_NAME_CONST = Regex("""const\s+val\s+TOOL_NAME\s*=\s*"([^"]+)"""")

    /**
     * Parses the `NodeType` enum-constant names, in declaration order, from
     * `NodeType.kt`. Only top-level `UPPER_SNAKE,` lines inside the enum body
     * match, so KDoc references (`[NodeType.OUTPUT]`) are ignored.
     */
    fun parseNodeTypeNames(source: String): List<String> {
        val bodyStart = source.indexOf("enum class NodeType")
        if (bodyStart < 0) throw GenerationException("'enum class NodeType' not found")
        val body = source.substring(bodyStart)
        val names = ENUM_CONSTANT.findAll(body).map { it.groupValues[1] }.toList()
        if (names.isEmpty()) throw GenerationException("No NodeType enum constants found")
        return names
    }

    /**
     * Parses the bound [ai.agent.android.domain.prompt.PromptVariableProvider]
     * implementation class names, in `@Binds @IntoSet` declaration order, from
     * `PromptTemplateModule.kt`.
     */
    fun parseBoundProviderClassNames(moduleSource: String): List<String> {
        val names = PROVIDER_BIND.findAll(moduleSource).map { it.groupValues[1] }.toList()
        if (names.isEmpty()) throw GenerationException("No PromptVariableProvider bindings found")
        return names
    }

    /** Parses the `KEY` string constant from a `PromptVariableProvider` source. */
    fun parseKeyConst(providerSource: String): String =
        KEY_CONST.find(providerSource)?.groupValues?.get(1)
            ?: throw GenerationException("`const val KEY` not found in provider source")

    /**
     * Parses the class names referenced as `<Class>.TOOL_NAME` in the
     * `@StringKey(...)` annotations of `LocalToolsModule.kt`, in declaration order.
     */
    fun parseBoundToolClassNames(moduleSource: String): List<String> {
        val names = TOOL_STRING_KEY.findAll(moduleSource).map { it.groupValues[1] }.toList()
        if (names.isEmpty()) throw GenerationException("No @StringKey(<Class>.TOOL_NAME) bindings found")
        return names
    }

    /** Parses the `TOOL_NAME` string constant from a tool / executor source. */
    fun parseToolNameConst(toolSource: String): String =
        TOOL_NAME_CONST.find(toolSource)?.groupValues?.get(1)
            ?: throw GenerationException("`const val TOOL_NAME` not found in tool source")

    /**
     * Evaluates the value of a top-level `const val <name>` in `DefaultPrompts.kt`.
     *
     * The right-hand side is a Kotlin string-concatenation expression
     * (`"..." + "..." + ...`); this collects every double-quoted literal up to the
     * next declaration / comment boundary, unescapes each, and concatenates them.
     */
    fun parseStringConst(source: String, name: String): String {
        val anchor = Regex("""const\s+val\s+$name\s*=""").find(source)
            ?: throw GenerationException("`const val $name` not found")
        val rest = source.substring(anchor.range.last + 1)
        // The expression ends at the next line that starts a new declaration, a
        // KDoc/line comment, or the enclosing brace close.
        val boundary = Regex("""(?m)^\s*(/\*\*|//|const |val |var |fun |object |class |private |\})""")
            .find(rest)
        val region = if (boundary != null) rest.substring(0, boundary.range.first) else rest
        val literals = STRING_LITERAL.findAll(region).map { it.groupValues[1] }.toList()
        if (literals.isEmpty()) throw GenerationException("No string literal found for `$name`")
        return literals.joinToString(separator = "") { unescapeKotlin(it) }
    }

    private val STRING_LITERAL = Regex(""""((?:\\.|[^"\\])*)"""")

    // ------------------------------------------------------------------ //
    //  Emitters (all produce content at the editor's 4-space base indent)
    // ------------------------------------------------------------------ //

    /** Emits the `NODE_TYPES` array, validating [enumNames] against [NODE_TYPE_META]. */
    fun emitNodeTypes(enumNames: List<String>): String {
        val metaIds = NODE_TYPE_META.map { it.id }.toSet()
        val enumSet = enumNames.toSet()
        if (metaIds != enumSet) {
            val missing = enumSet - metaIds
            val extra = metaIds - enumSet
            throw GenerationException(
                "NODE_TYPE_META is out of sync with NodeType.kt." +
                    (if (missing.isNotEmpty()) " Missing metadata for: $missing." else "") +
                    (if (extra.isNotEmpty()) " Unknown node types in metadata: $extra." else ""),
            )
        }
        val rows = NODE_TYPE_META.joinToString(separator = "\n") { m ->
            "        { id: ${jsonString(m.id)}, label: ${jsonString(m.label)}, " +
                "color: ${jsonString(m.color)}, icon: ${jsonString(m.icon)}, " +
                "inputs: ${m.inputs}, outputs: ${m.outputs} },"
        }
        return "    const NODE_TYPES = [\n$rows\n    ];"
    }

    /** Emits the `PROMPT_VARIABLES` array from the resolved provider keys. */
    fun emitPromptVariables(keys: List<String>): String {
        val joined = keys.joinToString(separator = ", ") { jsonString(it) }
        return "    const PROMPT_VARIABLES = [$joined];"
    }

    /** Emits the `AVAILABLE_TOOLS` array, validating [toolIds] against [TOOL_META]. */
    fun emitAvailableTools(toolIds: List<String>): String {
        val metaIds = TOOL_META.map { it.first }.toSet()
        val idSet = toolIds.toSet()
        if (metaIds != idSet) {
            val missing = idSet - metaIds
            val extra = metaIds - idSet
            throw GenerationException(
                "TOOL_META is out of sync with LocalToolsModule.kt." +
                    (if (missing.isNotEmpty()) " Missing label for tool ids: $missing." else "") +
                    (if (extra.isNotEmpty()) " Unknown tool ids in metadata: $extra." else ""),
            )
        }
        val rows = TOOL_META.joinToString(separator = "\n") { (id, label) ->
            "        { id: ${jsonString(id)}, label: ${jsonString(label)} },"
        }
        return "    const AVAILABLE_TOOLS = [\n$rows\n    ];"
    }

    /** Emits `SYSTEM_PROMPT_PREFIX` and the `DEFAULT_SYSTEM_PROMPTS` map from `DefaultPrompts.kt`. */
    fun emitDefaultPrompts(defaultPromptsSource: String): String {
        val prefix = parseStringConst(defaultPromptsSource, "SYSTEM_PROMPT_PREFIX")
        val entries = PROMPT_CONST_BY_JS_KEY.map { (jsKey, constName) ->
            jsKey to parseStringConst(defaultPromptsSource, constName)
        }
        val builder = StringBuilder()
        builder.append("    const SYSTEM_PROMPT_PREFIX = ${jsonString(prefix)};\n")
        builder.append("\n")
        builder.append("    const DEFAULT_SYSTEM_PROMPTS = {\n")
        builder.append("        LITE_RT: SYSTEM_PROMPT_PREFIX,\n")
        builder.append("        CLOUD: SYSTEM_PROMPT_PREFIX,\n")
        for ((jsKey, value) in entries) {
            builder.append("        $jsKey: ${jsonString(value)},\n")
        }
        builder.append("    };")
        return builder.toString()
    }

    // ------------------------------------------------------------------ //
    //  Marker injection
    // ------------------------------------------------------------------ //

    /** Opening marker line for [block]. */
    fun openMarker(block: String): String =
        "    /* AUTO-GEN:$block — generated by :app:generateBrowserEditorConstants. DO NOT EDIT BY HAND. */"

    /** Closing marker line for [block]. */
    fun closeMarker(block: String): String = "    /* /AUTO-GEN:$block */"

    /**
     * Replaces the content between [block]'s `AUTO-GEN` markers with [content],
     * preserving the marker lines and everything outside them.
     *
     * @throws GenerationException if either marker is absent or mis-ordered.
     */
    fun injectBlock(html: String, block: String, content: String): String {
        val openToken = "/* AUTO-GEN:$block"
        val closeToken = "/* /AUTO-GEN:$block */"
        val openIdx = html.indexOf(openToken)
        if (openIdx < 0) throw GenerationException("Missing open marker for block '$block'")
        val openLineEnd = html.indexOf('\n', openIdx)
        if (openLineEnd < 0) throw GenerationException("Malformed open marker for block '$block'")
        val closeIdx = html.indexOf(closeToken, openLineEnd)
        if (closeIdx < 0) throw GenerationException("Missing close marker for block '$block'")
        val closeLineStart = html.lastIndexOf('\n', closeIdx) + 1
        return html.substring(0, openLineEnd + 1) +
            content + "\n" +
            html.substring(closeLineStart)
    }

    /**
     * Extracts the current content between [block]'s markers, used by [drift] to
     * compare against freshly-generated content. Returns `null` if the block is
     * absent.
     */
    fun extractBlock(html: String, block: String): String? {
        val openToken = "/* AUTO-GEN:$block"
        val closeToken = "/* /AUTO-GEN:$block */"
        val openIdx = html.indexOf(openToken)
        if (openIdx < 0) return null
        val openLineEnd = html.indexOf('\n', openIdx)
        if (openLineEnd < 0) return null
        val closeIdx = html.indexOf(closeToken, openLineEnd)
        if (closeIdx < 0) return null
        val closeLineStart = html.lastIndexOf('\n', closeIdx) + 1
        // Content sits between the open marker's trailing newline and the close
        // marker line, minus the single separating newline injectBlock adds.
        return html.substring(openLineEnd + 1, closeLineStart).removeSuffix("\n")
    }

    // ------------------------------------------------------------------ //
    //  String helpers
    // ------------------------------------------------------------------ //

    /**
     * Unescapes a Kotlin double-quoted string-literal body (the text between the
     * quotes), handling the standard escape sequences plus `\uXXXX`.
     */
    fun unescapeKotlin(literalBody: String): String {
        val sb = StringBuilder(literalBody.length)
        var i = 0
        while (i < literalBody.length) {
            val c = literalBody[i]
            if (c == '\\' && i + 1 < literalBody.length) {
                when (val next = literalBody[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    '"' -> sb.append('"')
                    '\'' -> sb.append('\'')
                    '\\' -> sb.append('\\')
                    '$' -> sb.append('$')
                    'u' -> {
                        val hex = literalBody.substring(i + 2, i + 6)
                        sb.append(hex.toInt(16).toChar())
                        i += 4
                    }
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /**
     * Encodes [value] as a JavaScript/JSON double-quoted string literal. Control
     * characters are escaped; printable Unicode (including emoji) is left as-is.
     */
    fun jsonString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
