package ai.agent.android.domain.models

/**
 * Represents the type of a node in the visual orchestrator pipeline.
 */
enum class NodeType {
    /**
     * Node representing a local LiteRT-LM instance.
     */
    LITE_RT,

    /**
     * Node representing the external DeepSeek API client.
     */
    DEEPSEEK,

    /**
     * Node representing an external OpenAI API client.
     */
    OPENAI,

    /**
     * Node representing an Anthropic Claude API client.
     */
    ANTHROPIC,

    /**
     * Node representing an Google API client.
     */
    GOOGLE,

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
     * The starting point of the pipeline.
     */
    INPUT,

    /**
     * The ending point of the pipeline.
     */
    OUTPUT
}
