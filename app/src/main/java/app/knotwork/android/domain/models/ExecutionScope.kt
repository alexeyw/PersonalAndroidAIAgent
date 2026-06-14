package app.knotwork.android.domain.models

/**
 * Run-tree-scoped execution context threaded from
 * [app.knotwork.android.domain.engine.GraphExecutionEngine] into every
 * [app.knotwork.android.domain.engine.executors.NodeExecutor.execute] call.
 *
 * Replaces the bare `depth: Int` parameter the executor interface used to
 * carry: bundling the nesting depth, the shared step budget and the
 * per-node visit index in one value object keeps the executor signature stable
 * as composition features grow (the only consumer today is
 * `PipelineNodeExecutor`, which re-enters the engine for a sub-pipeline).
 *
 * The engine builds a fresh [ExecutionScope] for each node it dispatches: the
 * run-tree-wide fields ([depth], [stepBudget]) are constant across a run while
 * [pipelineVisitIndex] is recomputed per node so a `PIPELINE` node visited more
 * than once (e.g. inside a `QUEUE_PROCESSOR` loop) can mint a distinct,
 * resume-stable child run id per visit.
 *
 * @property depth Pipeline-nesting depth of the current run: `0` for a
 *   top-level run, `parentDepth + 1` for a sub-pipeline. Used to enforce the
 *   runtime nesting ceiling and to stamp console/trace records with their
 *   nesting level for the indented console rendering.
 * @property stepBudget The step budget shared across the whole run tree, or
 *   `null` when the engine was invoked without one (non-persisted editor test
 *   runs). See [RunStepBudget].
 * @property pipelineVisitIndex Zero-based index of *this* `PIPELINE`-node visit
 *   within the current run. Incremented by the engine each time it enters a
 *   `PIPELINE` node (including replayed visits during resume), so the value is
 *   re-derived deterministically on resume and the in-flight visit lands on the
 *   same index as on the original run — letting `PipelineNodeExecutor` find the
 *   exact child run to resume. `0` for every non-`PIPELINE` node.
 */
data class ExecutionScope(val depth: Int = 0, val stepBudget: RunStepBudget? = null, val pipelineVisitIndex: Int = 0)
