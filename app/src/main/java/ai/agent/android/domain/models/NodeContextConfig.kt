package ai.agent.android.domain.models

/**
 * Per-node configuration that controls which pieces of pipeline context are
 * injected into the node's input when it executes.
 *
 * The orchestrator's [GraphExecutionEngine] composes a node's input by reading
 * these flags and concatenating only the enabled blocks (chat history, original
 * user task, previous node output, long-term memory, tool results) into a
 * single string passed to the node's executor. Disabling unnecessary blocks
 * reduces token usage, speeds up inference and makes small on-device models
 * (LITE_RT) more reliable.
 *
 * The default value enables every flag — this preserves backward compatibility
 * with older pipelines.
 *
 * @property chatHistory When `true`, the agent's chat history block is added.
 * @property originalTask When `true`, the original user message that triggered
 * this pipeline run is added (useful for nodes deep in the chain that lose
 * sight of the user's intent).
 * @property nodeInput When `true`, the previous node's output is added. This
 * is the canonical input source for any node that participates in a chain;
 * the UI keeps it locked on so cycles do not become disconnected.
 * @property longTermMemory When `true`, relevant long-term memory entries are
 * added.
 * @property toolResults When `true`, results produced by tools earlier in the
 * current pipeline run are added.
 */
data class NodeContextConfig(
    val chatHistory: Boolean = true,
    val originalTask: Boolean = true,
    val nodeInput: Boolean = true,
    val longTermMemory: Boolean = true,
    val toolResults: Boolean = true,
) {
    /**
     * Returns `true` when every flag is `false`, meaning a node executed with
     * this configuration would receive an empty input string.
     */
    fun isEmpty(): Boolean = !chatHistory && !originalTask && !nodeInput && !longTermMemory && !toolResults

    /** Holds the canonical `ALL_ENABLED` instance and per-[NodeType] defaults. */
    companion object {
        /**
         * Default configuration with every context block enabled. This is what
         * is applied to legacy pipelines that were stored in Room before the
         * `context_config` column existed, so their behaviour does not change.
         */
        val ALL_ENABLED: NodeContextConfig = NodeContextConfig()

        /**
         * Returns the recommended starting [NodeContextConfig] for a freshly
         * created node of the given [type]. The orchestrator UI applies this
         * when the user drops a new node on the canvas, and the default
         * pipeline factory applies it to seed the first-launch preset, so
         * users see sensible per-type behaviour out of the box without having
         * to tune flags manually.
         *
         * Rationale per type:
         * - [NodeType.INPUT] / [NodeType.IF_CONDITION] / [NodeType.QUEUE_PROCESSOR]:
         *   control-flow nodes that mostly forward upstream input verbatim;
         *   only `nodeInput` is meaningful (plus `originalTask` for the queue
         *   processor so subtasks keep the user's intent).
         * - [NodeType.LITE_RT]: small on-device model — minimal context to
         *   keep the prompt short (`nodeInput` + `originalTask`).
         * - [NodeType.CLOUD]: cloud LLM with a large context window — adds
         *   chat history on top of the LITE_RT defaults.
         * - [NodeType.TOOL]: a tool only needs the concrete query, hence
         *   `nodeInput` only.
         * - [NodeType.CLARIFICATION]: needs the question's source plus the
         *   user's original goal to phrase a useful clarifier.
         * - [NodeType.OUTPUT]: terminal node that composes the user-facing
         *   reply — full context is allowed.
         * - [NodeType.SUMMARY] / [NodeType.EVALUATION]: post-processing nodes
         *   that work over tool results plus the original task.
         * - [NodeType.INTENT_ROUTER] / [NodeType.DECOMPOSITION]: classifiers
         *   that benefit from the user's intent and (for the router) the
         *   recent chat to disambiguate references.
         */
        fun defaultForType(type: NodeType): NodeContextConfig = when (type) {
            NodeType.INPUT,
            NodeType.IF_CONDITION,
            -> NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            )

            NodeType.LITE_RT,
            NodeType.CLARIFICATION,
            NodeType.QUEUE_PROCESSOR,
            NodeType.DECOMPOSITION,
            -> NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            )

            NodeType.CLOUD,
            NodeType.INTENT_ROUTER,
            -> NodeContextConfig(
                chatHistory = true,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            )

            NodeType.TOOL -> NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            )

            NodeType.SUMMARY,
            NodeType.EVALUATION,
            -> NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = true,
            )

            NodeType.OUTPUT -> ALL_ENABLED
        }
    }
}

/**
 * Node types whose executor input is assembled by `NodeContextBuilder` from the
 * pipeline context blocks selected via [NodeContextConfig]. Control-flow types
 * ([NodeType.INPUT], [NodeType.IF_CONDITION], [NodeType.QUEUE_PROCESSOR]) and
 * the terminal [NodeType.OUTPUT] in echo mode (no `systemPrompt`) bypass
 * context composition and forward the raw upstream input verbatim — for those
 * nodes [NodeContextConfig] is ignored at runtime.
 *
 * This single source of truth is consumed by both `GraphExecutionEngine`
 * (deciding whether to compose a node's input) and `PipelineGraph.validate()`
 * (deciding whether an empty configuration is a real validation problem).
 */
private val CONTEXT_AWARE_NODE_TYPES: Set<NodeType> = setOf(
    NodeType.LITE_RT,
    NodeType.CLOUD,
    NodeType.OUTPUT,
    NodeType.SUMMARY,
    NodeType.INTENT_ROUTER,
    NodeType.DECOMPOSITION,
    NodeType.EVALUATION,
    NodeType.CLARIFICATION,
    NodeType.TOOL,
)

/**
 * Returns `true` when this node's [NodeContextConfig] actually drives its
 * executor input at runtime. Nodes for which this returns `false` ignore the
 * configuration entirely, so an "empty" config on them is not a validation
 * error — the runtime forwards the raw upstream `currentInputText` regardless.
 *
 * Specifically:
 * - Control-flow nodes (INPUT, IF_CONDITION, QUEUE_PROCESSOR) always echo
 *   their input.
 * - OUTPUT in echo mode (no `systemPrompt`) passes the upstream text straight
 *   to the user without any context wrapping.
 * - Every other node type relies on `NodeContextBuilder` and therefore
 *   requires at least one enabled context source.
 */
fun NodeModel.usesContextConfig(): Boolean {
    if (type !in CONTEXT_AWARE_NODE_TYPES) return false
    if (type == NodeType.OUTPUT && systemPrompt.isNullOrBlank()) return false
    return true
}
