package ai.agent.android.domain.models

/**
 * Snapshot of a single tool invocation that finished during the current pipeline run.
 *
 * Accumulated by [ai.agent.android.domain.engine.GraphExecutionEngine] each time a
 * [NodeType.TOOL] node completes successfully and consumed by
 * [ai.agent.android.domain.engine.NodeContextBuilder] to render the
 * `--- Tool Results ---` block when a node opts into [NodeContextConfig.toolResults].
 *
 * Kept deliberately minimal: the orchestrator only needs the tool's name (so the
 * LLM can attribute the observation) and the textual output the tool produced.
 *
 * @property toolName The name of the tool that produced the result, as resolved by
 * `ToolRepository` (e.g. `web.search`, `calendar.read`).
 * @property output The raw textual output returned by the tool. May be plain text,
 * JSON, or any other representation the tool emits.
 */
data class ToolInvocationResult(val toolName: String, val output: String)
