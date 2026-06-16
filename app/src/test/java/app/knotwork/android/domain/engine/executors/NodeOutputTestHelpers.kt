package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeOutput

/**
 * Test helper: unwraps a list of [NodeOutput] into the underlying state/result objects.
 *
 * A tech-debt cleanup migrated `NodeExecutor.execute` from
 * `Flow<Any>` to `Flow<NodeOutput>`. Existing executor tests were written against the
 * untyped flow and used `is AgentOrchestratorState` / `is NodeExecutionResult` checks
 * directly. This extension preserves those assertion patterns without smuggling the
 * `Any` channel back into production code.
 */
internal fun List<NodeOutput>.unwrap(): List<Any> = mapNotNull { output ->
    when (output) {
        is NodeOutput.State -> output.state as Any
        is NodeOutput.Result -> output.result as Any
        // Console lines are observability side-channel, not part of the
        // state/result stream these legacy assertions inspect — drop them.
        is NodeOutput.Console -> null
    }
}

/** Convenience filters returning the typed payloads carried by [NodeOutput] elements. */
internal inline fun <reified T : AgentOrchestratorState> List<NodeOutput>.filterStates(): List<T> =
    filterIsInstance<NodeOutput.State>().map { it.state }.filterIsInstance<T>()

/** Returns the [NodeOutput.Console] events emitted by an executor flow. */
internal fun List<NodeOutput>.consoleEvents(): List<NodeOutput.Console> = filterIsInstance<NodeOutput.Console>()

/** Returns the single terminal [NodeExecutionResult] emitted by an executor flow. */
internal fun List<NodeOutput>.lastResult(): NodeExecutionResult = filterIsInstance<NodeOutput.Result>().last().result
