package ai.agent.android.domain.models

/**
 * Data class holding performance metrics for the AI agent.
 *
 * @property lastInferenceTimeMs Time taken for the last LLM inference in milliseconds.
 * @property tokensPerSecond Number of tokens processed per second in the last inference.
 * @property totalTokensProcessed Total number of tokens processed in the current session/lifetime.
 */
data class AgentMetrics(
    val lastInferenceTimeMs: Long = 0L,
    val tokensPerSecond: Float = 0f,
    val totalTokensProcessed: Int = 0
)
