package app.knotwork.android.domain.models

/**
 * Represents the type of a node in the visual orchestrator pipeline.
 */
enum class NodeType {
    /**
     * Node representing a local LiteRT-LM instance.
     */
    LITE_RT,

    /**
     * Node representing an external Cloud API client (e.g. OpenAI, Anthropic, Google, DeepSeek).
     */
    CLOUD,

    /**
     * Node representing a Tool or AppFunction that can be executed.
     */
    TOOL,

    /**
     * Node representing a logical if condition for branching.
     */
    IF_CONDITION,

    /**
     * Node representing an intent router that determines the type of user input (e.g., simple message, complex question, task).
     */
    INTENT_ROUTER,

    /**
     * Node representing a decomposition unit that breaks down complex tasks into a list of simpler subtasks.
     */
    DECOMPOSITION,

    /**
     * Node representing a queue processor that iterates over a list of subtasks and executes them sequentially.
     */
    QUEUE_PROCESSOR,

    /**
     * Node representing an evaluation unit that analyzes the result of a subtask to determine if it was successful or needs rework.
     */
    EVALUATION,

    /**
     * Node representing a summarization unit that summarizes the results of multiple subtasks or actions.
     */
    SUMMARY,

    /**
     * Node that pauses the pipeline to ask the user a clarifying question.
     *
     * The node uses a local LLM (configured via `systemPrompt` and `modelPath`) to
     * generate a context-aware question and an optional list of answer options based
     * on the upstream input. While waiting for the user's reply, the engine emits
     * [AgentOrchestratorState.AwaitingClarification]. The user's answer becomes the
     * node's output and is forwarded downstream as `inputText`.
     */
    CLARIFICATION,

    /**
     * Node whose execution runs another pipeline graph — the composition
     * primitive that turns a reusable branch into a callable block (analogous
     * to a function call).
     *
     * The node references its callee by [NodeModel.targetPipelineId]. At
     * runtime the engine feeds the node's input to the sub-pipeline's
     * [INPUT] node as the user prompt, executes the sub-graph, and the text
     * produced by the sub-pipeline's [OUTPUT] node becomes this node's output.
     *
     * Unbounded recursion (A → B → A) and runaway nesting depth are the two
     * inherent hazards; both are rejected statically before a run starts
     * (the call graph is known ahead of time — see
     * `PipelineCompositionValidator`), with a runtime depth ceiling
     * ([app.knotwork.android.domain.repositories.SettingsRepository.pipelineMaxNestingDepth])
     * as a race-condition safety net.
     */
    PIPELINE,

    /**
     * The starting point of the pipeline.
     */
    INPUT,

    /**
     * The ending point of the pipeline.
     */
    OUTPUT,
}
