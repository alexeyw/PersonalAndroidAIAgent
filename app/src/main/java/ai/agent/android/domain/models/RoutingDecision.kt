package ai.agent.android.domain.models

/**
 * Represents the decision made by the TaskRouter on which engine to use for a prompt.
 */
sealed class RoutingDecision {
    /**
     * Route to the local on-device LiteRT model.
     */
    data object LocalLiteRT : RoutingDecision()

    /**
     * Route to the local Ollama instance (typically over Wi-Fi).
     */
    data object LocalOllama : RoutingDecision()

    /**
     * Route to a cloud-based LLM (e.g., OpenAI, Anthropic, DeepSeek).
     * 
     * @property provider The name of the cloud provider to use (e.g., "openai", "anthropic").
     */
    data class CloudLLM(val provider: String) : RoutingDecision()
}
