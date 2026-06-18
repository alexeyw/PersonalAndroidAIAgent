package app.knotwork.android.domain.models

/**
 * Data class holding performance metrics for the AI agent.
 *
 * Captures both last-inference statistics (used by the live Monitoring widget) and
 * aggregated per-session counters used to visualize cumulative cost.
 *
 * @property lastInferenceTimeMs Time taken for the last LLM inference in milliseconds.
 * @property tokensPerSecond Number of tokens processed per second in the last inference.
 * @property totalTokensProcessed Total number of tokens processed in the current session/lifetime.
 * @property totalExecutionTimeMs Sum of all node execution times in the current session.
 * @property timePerNodeType Aggregated execution time per pipeline [NodeType].
 * @property repairAttemptsPerNode Number of structured-output repair
 *   re-inferences spent per node, keyed by node display name. A node that never
 *   produced malformed output is simply absent from the map. Populated when the
 *   structured-output gate
 *   ([app.knotwork.android.domain.engine.structured.StructuredOutputGate]) is
 *   wired into the engine's structured consumers; surfaces how often the local
 *   model "stumbled" before emitting valid output.
 */
data class AgentMetrics(
    val lastInferenceTimeMs: Long = 0L,
    val tokensPerSecond: Float = 0f,
    val totalTokensProcessed: Int = 0,
    val totalExecutionTimeMs: Long = 0L,
    val timePerNodeType: Map<NodeType, Long> = emptyMap(),
    val repairAttemptsPerNode: Map<String, Int> = emptyMap(),
)
