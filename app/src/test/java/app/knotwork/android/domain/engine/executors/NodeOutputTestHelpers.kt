package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeOutput

/**
 * Test helper: unwraps a list of [NodeOutput] into the underlying state/result objects.
 *
 * Phase 16-7 / Phase 17 tech-debt cleanup migrated `NodeExecutor.execute` from
 * `Flow<Any>` to `Flow<NodeOutput>`. Existing executor tests were written against the
 * untyped flow and used `is AgentOrchestratorState` / `is NodeExecutionResult` checks
 * directly. This extension preserves those assertion patterns without smuggling the
 * `Any` channel back into production code.
 */
internal fun List<NodeOutput>.unwrap(): List<Any> = map { output ->
    when (output) {
        is NodeOutput.State -> output.state as Any
        is NodeOutput.Result -> output.result as Any
    }
}

/** Convenience filters returning the typed payloads carried by [NodeOutput] elements. */
internal inline fun <reified T : AgentOrchestratorState> List<NodeOutput>.filterStates(): List<T> =
    filterIsInstance<NodeOutput.State>().map { it.state }.filterIsInstance<T>()

/** Returns the single terminal [NodeExecutionResult] emitted by an executor flow. */
internal fun List<NodeOutput>.lastResult(): NodeExecutionResult = (last() as NodeOutput.Result).result
