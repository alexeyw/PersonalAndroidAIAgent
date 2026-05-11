package ai.agent.android.domain.constants

import ai.agent.android.domain.models.NodeType

/**
 * Canonical, code-level storage for every default LLM prompt and prompt template
 * used by the agent's pipeline executors.
 *
 * **Two distinct kinds of strings live here:**
 *
 *  - **`SYSTEM_FALLBACK`** — the default `systemPrompt` for a node when the user
 *    has not configured one (i.e. `node.systemPrompt == null`). They are emitted
 *    verbatim into the LLM input.
 *  - **`*_TEMPLATE`** — internal wrap-templates used by an executor to splice the
 *    upstream `inputText`, the resolved `systemPrompt`, and other per-node values
 *    into a final LLM prompt. Placeholders use the literal `${'$'}KEY` form
 *    (e.g. `${'$'}INPUT_TEXT`, `${'$'}ORIGINAL_TASK`) and are substituted via
 *    [renderTemplate] in a **single left-to-right pass** so a value that itself
 *    contains a `${'$'}KEY` token cannot trigger a follow-up replacement and
 *    corrupt downstream substitutions. They are **not** routed through
 *    [ai.agent.android.domain.prompt.PromptTemplateEngine] — that engine is
 *    reserved for runtime-resolved variables (`${'$'}DATE`, `${'$'}TOOLS`, …) that
 *    apply to user-authored prompts.
 *
 * The historical flat constants (`SYSTEM_PROMPT_PREFIX`, `INTENT_ROUTER_PROMPT`,
 * etc.) are kept in place because they are still consumed by
 * `DefaultPipelineFactory`, `ClarificationNodeExecutor`, and the browser-side
 * `pipeline-editor.html` mirror. The per-node sub-objects expose the same texts
 * by reference to avoid drift.
 */
object DefaultPrompts {
    /**
     * Matches a literal `${'$'}KEY` placeholder where `KEY` follows the project-wide
     * variable convention `[A-Z_][A-Z0-9_]*` (see DESCRIPTION.md §5). Lowercase
     * tokens or sequences like `${'$'}50` are deliberately not matched.
     */
    private val PLACEHOLDER_REGEX = Regex("\\\$([A-Z_][A-Z0-9_]*)")

    /**
     * Substitutes `${'$'}KEY` placeholders inside [template] with values from
     * [values] in a **single left-to-right pass** that does not rescan replaced
     * text. This is critical when a substituted value (for example, a user-authored
     * `systemPrompt` or a runtime `originalPrompt`) itself contains a literal
     * `${'$'}KEY` token — a chained `String.replace` sequence would treat such a
     * token as a follow-up placeholder and corrupt the prompt.
     *
     * Unknown placeholders are left verbatim — same defensive behavior as
     * [ai.agent.android.domain.prompt.PromptTemplateEngine] applied to runtime
     * variables.
     *
     * @param template Template string containing zero or more `${'$'}KEY` placeholders.
     * @param values Map from placeholder key (no leading `${'$'}`) to its replacement.
     * @return The template with every recognised placeholder substituted exactly once.
     */
    fun renderTemplate(template: String, values: Map<String, String>): String =
        PLACEHOLDER_REGEX.replace(template) { match ->
            // Regex.replace builds the result by appending matched-region replacements
            // to the un-matched prefix; the replacement string itself is not re-scanned,
            // so a value containing "$OTHER_KEY" stays literal in the output.
            val key = match.groupValues[1]
            values[key] ?: match.value
        }

    /**
     * Generic preamble injected at the top of every LiteRT/Cloud system prompt
     * (`${'$'}systemPromptPrefix\n${'$'}nodeSystemPrompt\n` — see
     * [LiteRtNodeExecutor]). Stored in `SettingsRepository.systemPromptPrefix`
     * with this value as its first-launch default.
     */
    const val SYSTEM_PROMPT_PREFIX = "You are a helpful AI assistant running on an Android device."

    /**
     * Tool-usage instruction surfaced to user-authored prompts when the agent
     * wants to advertise the available tools. The literal `[TOOL_LIST]`
     * placeholder is substituted by the caller (currently consumed by
     * `pipeline-editor.html` as a copy-pasteable template snippet).
     */
    val TOOL_USAGE_INSTRUCTION = """
        You have access to the following tools:
        [TOOL_LIST]

        To use a tool, output a JSON block like this:
        ```json
        {
          "tool": "tool_name",
          "arguments": "{ \"param\": \"value\" }"
        }
        ```
        If you don't need to use a tool, just answer the user directly.
    """.trimIndent()

    /**
     * Default `systemPrompt` for an [NodeType.INTENT_ROUTER] node. The model is
     * expected to emit one of the four keywords (`Simple`, `Data`, `Complex`,
     * `Task`) as its full reply — the router uses the response as a routing key.
     */
    const val INTENT_ROUTER_PROMPT = "You are an Intent Router. Analyze the user input and determine its category. " +
        "Output strictly ONE of the following keywords:\n" +
        "- Simple (if it's a simple chat message or greeting)\n" +
        "- Data (if it requires searching the web or current data)\n" +
        "- Complex (if it requires complex coding, math, or deep reasoning)\n" +
        "- Task (if it's a multi-step task or requires executing an action/tool)"

    /**
     * Default `systemPrompt` for an [NodeType.DECOMPOSITION] node. The model is
     * expected to return a JSON array of strings — each string is one subtask
     * that downstream `QUEUE_PROCESSOR` iterates over.
     */
    const val DECOMPOSITION_PROMPT = "You are a Task Decomposer. Break down the given complex task into a list " +
        "of simpler, actionable subtasks. Output the result as a JSON array of strings."

    /**
     * Default `systemPrompt` for an [NodeType.EVALUATION] node. The model is
     * expected to inspect a subtask's result and report success / what went wrong.
     */
    const val EVALUATION_PROMPT = "You are a Task Evaluator. Analyze the result of the executed subtask and " +
        "determine if it was successful. If not, explain what went wrong and how to fix it."

    /**
     * Default `systemPrompt` for an [NodeType.SUMMARY] node. The model is
     * expected to synthesise multiple subtask results into a single coherent
     * answer. See [Summary.SYNTHESIS_TEMPLATE] for the wrap-template that
     * surrounds this prompt at execution time.
     */
    const val SUMMARY_PROMPT = "You are a Summarizer. Given the results of multiple executed subtasks, provide " +
        "a concise and comprehensive summary of the overall outcome."

    /**
     * Default `systemPrompt` for an [NodeType.OUTPUT] node when the user has not
     * authored one. See [Output.FORMATTING_TEMPLATE] for the wrap-template that
     * embeds this prompt before sending to the LLM.
     */
    const val OUTPUT_FORMAT_PROMPT = "You are a Formatter. Please format the provided input text into a clear, " +
        "readable markdown response for the user."

    /**
     * Default instruction for a [NodeType.CLARIFICATION] node.
     *
     * Tells the LLM to inspect the upstream context and produce a clarification
     * question — together with an optional list of answer options — as strict JSON.
     * The executor parses the JSON and forwards it to the user; an empty `options`
     * array means "free-form input expected".
     */
    const val CLARIFICATION_PROMPT = "You are a Clarification Generator. Inspect the user's request and the upstream " +
        "context, then craft ONE concise clarifying question that would help the agent proceed. If a small set of " +
        "likely answers is obvious, list them as options; otherwise return an empty array to ask for free-form input. " +
        "Output STRICTLY valid JSON with this shape and nothing else:\n" +
        "{\n  \"question\": \"<the question to ask the user>\",\n  \"options\": [\"<option 1>\", \"<option 2>\"]\n}"

    /** Prompts for [NodeType.LITE_RT] (on-device inference) nodes. */
    object LiteRt {
        /**
         * Used as the node's `systemPrompt` when the user has not configured one.
         * Intentionally generic because the heavy-lifting domain-specific prompt
         * comes from [SYSTEM_PROMPT_PREFIX], which the executor concatenates ahead
         * of this fallback.
         */
        const val SYSTEM_FALLBACK = "You are a helpful AI assistant."
    }

    /** Prompts for [NodeType.CLOUD] (remote LLM provider) nodes. */
    object Cloud {
        /** @see LiteRt.SYSTEM_FALLBACK — same contract on the cloud path. */
        const val SYSTEM_FALLBACK = "You are a helpful AI assistant."
    }

    /**
     * Prompts for the legacy "system" node executor (used by
     * `INTENT_ROUTER`/`DECOMPOSITION`/`EVALUATION` paths in
     * [ai.agent.android.domain.engine.executors.SystemNodeExecutor]).
     */
    object System {
        /** Used when no `systemPrompt` is set on a system-style node. */
        const val SYSTEM_FALLBACK = "You are an AI assistant."
    }

    /** Prompts for [NodeType.OUTPUT] nodes. */
    object Output {
        /** @see DefaultPrompts.OUTPUT_FORMAT_PROMPT — re-exported for symmetry with other sub-objects. */
        const val SYSTEM_FALLBACK = OUTPUT_FORMAT_PROMPT

        /**
         * Wrap-template that the OUTPUT executor sends to the LLM.
         *
         * Placeholders (literal `${'$'}KEY` substituted via [String.replace]):
         *  - `${'$'}NODE_SYSTEM_PROMPT` — the resolved (user or fallback) system prompt for the node.
         *  - `${'$'}INPUT_TEXT` — the upstream node output to format.
         *
         * Expected response: the formatted answer with no conversational filler;
         * any "Here is the formatted output:" prefix is stripped by the executor
         * as a defensive heuristic.
         */
        const val FORMATTING_TEMPLATE = "\$NODE_SYSTEM_PROMPT\n\n" +
            "CRITICAL INSTRUCTION: Output ONLY the requested format. Do NOT include any conversational filler, " +
            "explanations, or preambles (e.g., \"Here is the formatted output:\").\n\n" +
            "INPUT: \$INPUT_TEXT\nFORMATTED OUTPUT: "
    }

    /** Prompts for [NodeType.SUMMARY] nodes. */
    object Summary {
        /**
         * Used when no `systemPrompt` is set on a SUMMARY node. Differs from
         * [DefaultPrompts.SUMMARY_PROMPT] (which is the user-facing default in
         * `DefaultPipelineFactory`) — this fallback fires only inside the executor
         * when the persisted node has no explicit prompt.
         */
        const val SYSTEM_FALLBACK = "You are an AI assistant responsible for summarizing the results of subtasks."

        /**
         * Wrap-template that the SUMMARY executor sends to the LLM.
         *
         * Placeholders (literal `${'$'}KEY` substituted via [String.replace]):
         *  - `${'$'}NODE_SYSTEM_PROMPT` — the resolved (user or fallback) system prompt.
         *  - `${'$'}ORIGINAL_TASK` — the original user message that started the run.
         *  - `${'$'}RESULTS_OF_SUBTASKS` — concatenated subtask outputs (the executor's `inputText`).
         *
         * Expected response: a single coherent answer to the original task that
         * actually uses the data from the subtask results (not just a list of
         * what each subtask did).
         */
        const val SYNTHESIS_TEMPLATE = "\$NODE_SYSTEM_PROMPT\n\n" +
            "CRITICAL INSTRUCTION: You must synthesize the provided results into a coherent final answer for the " +
            "original task. Do not just list what each task did. Answer the original task using the data from the " +
            "results.\n\n" +
            "ORIGINAL TASK: \$ORIGINAL_TASK\n\n" +
            "RESULTS OF SUBTASKS:\n\$RESULTS_OF_SUBTASKS\n\n" +
            "FINAL ANSWER: "
    }

    /** Prompts for [NodeType.INTENT_ROUTER] nodes. */
    object IntentRouter {
        /** @see DefaultPrompts.INTENT_ROUTER_PROMPT */
        const val SYSTEM_FALLBACK = INTENT_ROUTER_PROMPT
    }

    /** Prompts for [NodeType.DECOMPOSITION] nodes. */
    object Decomposition {
        /** @see DefaultPrompts.DECOMPOSITION_PROMPT */
        const val SYSTEM_FALLBACK = DECOMPOSITION_PROMPT
    }

    /** Prompts for [NodeType.EVALUATION] nodes. */
    object Evaluation {
        /** @see DefaultPrompts.EVALUATION_PROMPT */
        const val SYSTEM_FALLBACK = EVALUATION_PROMPT
    }

    /** Prompts for [NodeType.CLARIFICATION] nodes. */
    object Clarification {
        /** @see DefaultPrompts.CLARIFICATION_PROMPT */
        const val SYSTEM_FALLBACK = CLARIFICATION_PROMPT
    }

    /** Prompts for [NodeType.TOOL] nodes — internal (not user-customisable) wrap-templates. */
    object Tool {
        /**
         * Sent to the local LLM when the TOOL node is configured with `auto`
         * tool selection.
         *
         * Placeholders (literal `${'$'}KEY` substituted via [String.replace]):
         *  - `${'$'}AVAILABLE_TOOLS` — multi-line list of tools (`Tool: …\nDescription: …\nParameters: …`).
         *  - `${'$'}INPUT_TEXT` — the upstream task description.
         *
         * Expected response: strict JSON `{"tool": "...", "arguments": ...}`.
         */
        const val AUTO_SELECT_TEMPLATE =
            "You are an AI assistant that selects the best tool for a given task and generates arguments.\n" +
                "\n" +
                "AVAILABLE TOOLS:\n" +
                "\$AVAILABLE_TOOLS\n" +
                "\n" +
                "TASK:\n" +
                "\$INPUT_TEXT\n" +
                "\n" +
                "INSTRUCTIONS:\n" +
                "Choose the most appropriate tool to solve the task. \n" +
                "Generate strictly valid JSON with two fields: \"tool\" and \"arguments\".\n" +
                "\"tool\" should be the exact name of the selected tool.\n" +
                "\"arguments\" should contain the parameters matching the tool's schema.\n" +
                "\n" +
                "JSON OUTPUT: "

        /**
         * Sent to the local LLM when the TOOL node has a fixed `toolName` and we
         * only need the model to produce arguments.
         *
         * Placeholders (literal `${'$'}KEY` substituted via [String.replace]):
         *  - `${'$'}TOOL_NAME` — the tool's stable identifier.
         *  - `${'$'}TOOL_DESCRIPTION` — human description.
         *  - `${'$'}TOOL_PARAMETERS` — schema string.
         *  - `${'$'}INPUT_TEXT` — the upstream task description.
         *
         * Expected response: strict JSON for the tool's arguments (the executor
         * also accepts a `{"tool": "...", "arguments": ...}` envelope).
         */
        const val ARGUMENT_GENERATION_TEMPLATE =
            "You are an AI assistant that generates arguments for a specific tool.\n" +
                "\n" +
                "TOOL: \$TOOL_NAME\n" +
                "DESCRIPTION: \$TOOL_DESCRIPTION\n" +
                "PARAMETERS SCHEMA: \$TOOL_PARAMETERS\n" +
                "\n" +
                "TASK:\n" +
                "\$INPUT_TEXT\n" +
                "\n" +
                "INSTRUCTIONS:\n" +
                "Based on the task description, generate strictly valid JSON for the tool's " +
                "\"arguments\" according to its schema.\n" +
                "Do not wrap in anything else, just the JSON for the arguments. " +
                "If it's a primitive, output {\"tool\": \"\$TOOL_NAME\", \"arguments\": <value>}.\n" +
                "\n" +
                "JSON OUTPUT: "
    }

    /** Prompts for [NodeType.IF_CONDITION] nodes — internal wrap-template. */
    object IfCondition {
        /**
         * Sent to the local LLM when the IF_CONDITION node uses a free-form
         * `conditionPrompt` (as opposed to keyword/complexity branches).
         *
         * Placeholders (literal `${'$'}KEY` substituted via [String.replace]):
         *  - `${'$'}CONDITION_PROMPT` — the user-authored condition text.
         *  - `${'$'}INPUT_TEXT` — the upstream content to evaluate.
         *
         * Expected response: contains either `true` or `false` (case-insensitive
         * substring match on the trimmed reply).
         */
        const val EVALUATION_TEMPLATE =
            "Evaluate the following text against the condition: \"\$CONDITION_PROMPT\".\n" +
                "Text: \"\$INPUT_TEXT\"\n" +
                "Reply strictly with 'true' or 'false'."
    }

    /** Prompts for the [NodeType.QUEUE_PROCESSOR] iteration loop. */
    object QueueProcessor {
        /**
         * Critical instruction wrapped around every individual subtask the queue
         * dispatches downstream. Stops the model from trying to "solve" the
         * outer task or include conversational filler when it is meant to focus
         * on a single queue item.
         *
         * Used as a literal string (no placeholders).
         */
        const val SUBTASK_INSTRUCTION = "CRITICAL INSTRUCTION: You are executing a single subtask within a larger " +
            "workflow. Focus ONLY on this specific subtask. Do NOT provide conversational filler, and do NOT attempt " +
            "to solve the overall task or future steps."
    }

    /**
     * Returns the default user-facing system prompt for a specific node type — the
     * value pre-seeded into `node.systemPrompt` by `DefaultPipelineFactory`.
     *
     * `null` for nodes that do not carry a `systemPrompt` field
     * (`INPUT`, `IF_CONDITION`, `TOOL`, `QUEUE_PROCESSOR`, `SYSTEM`).
     */
    fun getDefaultPromptForNodeType(type: NodeType): String? = when (type) {
        NodeType.INTENT_ROUTER -> INTENT_ROUTER_PROMPT
        NodeType.DECOMPOSITION -> DECOMPOSITION_PROMPT
        NodeType.EVALUATION -> EVALUATION_PROMPT
        NodeType.SUMMARY -> SUMMARY_PROMPT
        NodeType.OUTPUT -> OUTPUT_FORMAT_PROMPT
        NodeType.CLARIFICATION -> CLARIFICATION_PROMPT
        NodeType.LITE_RT, NodeType.CLOUD -> SYSTEM_PROMPT_PREFIX
        else -> null
    }
}
