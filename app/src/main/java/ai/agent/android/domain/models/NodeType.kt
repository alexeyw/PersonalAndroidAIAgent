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
     * The starting point of the pipeline.
     */
    INPUT,

    /**
     * The ending point of the pipeline.
     */
    OUTPUT
}
