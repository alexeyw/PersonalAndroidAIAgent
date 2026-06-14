package app.knotwork.android.domain.models

/**
 * Mutable step budget shared by every run in one execution tree.
 *
 * A single top-level run has its own budget seeded from
 * [app.knotwork.android.domain.repositories.SettingsRepository.pipelineMaxSteps].
 * When a `PIPELINE` node re-enters the engine to run a sub-pipeline, the very
 * same [RunStepBudget] instance is threaded into the child run (via
 * [ExecutionScope]) instead of minting a fresh per-graph counter — so a
 * sub-pipeline cannot side-step the parent's `MAX_STEPS` ceiling by resetting
 * the count. Every node visited at any depth decrements [remaining]; once it
 * reaches zero the engine fails the run with the max-steps error, which
 * propagates up the stack as the parent `PIPELINE` node's error and terminates
 * the whole tree.
 *
 * The holder is deliberately a tiny mutable object passed by reference: the
 * engine's `invoke` flow is the only writer, and the recursion is strictly
 * synchronous within a single coroutine (the parent suspends on the child's
 * flow), so no synchronisation is needed.
 *
 * **Scope.** The budget is per-execution and is *not* persisted: a resumed run
 * re-seeds a fresh budget and re-counts replayed nodes. `pipelineMaxSteps` is
 * sized with comfortable head-room, so re-walking the recorded prefix on
 * resume cannot realistically exhaust it.
 *
 * @property remaining The number of node executions still permitted across the
 *   whole run tree. Decremented on every node entry; the run fails when it
 *   would go below zero.
 */
class RunStepBudget(var remaining: Int)
