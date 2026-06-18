package app.knotwork.android.domain.engine.structured

/**
 * Observability seam invoked by [StructuredOutputGate] immediately before each
 * repair re-inference.
 *
 * The gate itself is pure-domain and knows nothing about the agent console or
 * the metrics repository — it only reports, through this listener, that a node
 * "stumbled" and is about to retry. Consumers (the engine-side integration)
 * supply an implementation that emits a
 * [app.knotwork.android.domain.models.ConsoleEventType.StructuredOutputRepair]
 * console event ("Output repair 1/2 for node <name>") and bumps the per-node
 * `repairAttemptsPerNode` counter in
 * [app.knotwork.android.domain.models.AgentMetrics]. Pass [NONE] when no
 * observability is desired (e.g. unit tests of unrelated behaviour).
 */
fun interface RepairListener {

    /**
     * Called once per repair attempt, before the corrective inference runs.
     *
     * @param nodeName Display name of the node whose structured output is being
     *   repaired, used to compose the user-visible console line.
     * @param attempt 1-based index of this repair attempt.
     * @param maxRepairs The configured repair ceiling, so the listener can render
     *   the "<attempt>/<maxRepairs>" progress fraction.
     */
    fun onRepairAttempt(nodeName: String, attempt: Int, maxRepairs: Int)

    /** Shared no-op instance. */
    companion object {
        /** No-op listener for callers that do not need repair observability. */
        val NONE: RepairListener = RepairListener { _, _, _ -> }
    }
}
