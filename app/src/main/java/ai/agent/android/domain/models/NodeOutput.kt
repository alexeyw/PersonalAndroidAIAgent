package ai.agent.android.domain.models

/**
 * Discriminated output of a [ai.agent.android.domain.engine.executors.NodeExecutor.execute] flow.
 *
 * Replaces the previous untyped `Flow<Any>` channel. A node executor emits intermediate
 * pipeline-state updates (rendered as UI states by the engine) followed by exactly one
 * terminal [Result] that carries the node's textual output and metrics. Modelling these
 * two payloads as a sealed class lets [ai.agent.android.domain.engine.GraphExecutionEngine]
 * dispatch via an exhaustive `when` instead of brittle `is`-checks against `Any`.
 */
sealed class NodeOutput {
    /**
     * Carries an [AgentOrchestratorState] update. Multiple [State] values may be emitted by a
     * single execution as the node streams progress (e.g. `Thinking`, `Answering`,
     * `ExecutingTool`, `AwaitingClarification`).
     */
    data class State(val state: AgentOrchestratorState) : NodeOutput()

    /**
     * Terminal value of a node execution. Exactly one [Result] is expected per `execute()` flow;
     * it provides the [NodeExecutionResult] consumed by the engine (output text, error, routing
     * key, condition result, token count, resolved tool name).
     */
    data class Result(val result: NodeExecutionResult) : NodeOutput()
}
