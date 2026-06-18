package app.knotwork.android.domain.engine.structured

/**
 * [RepairListener] that buffers every repair attempt instead of acting on it
 * immediately.
 *
 * The gate reports repair attempts through a **non-suspend** callback, but a
 * node executor can only surface a console line by `emit`-ing a
 * [app.knotwork.android.domain.models.NodeOutput.Console] (a suspend operation)
 * into its flow. This listener bridges the two: it records each attempt while
 * the gate runs, and the executor drains [attempts] afterwards — emitting one
 * console line per attempt, which the engine then both renders and counts as a
 * repair in [app.knotwork.android.domain.models.AgentMetrics.repairAttemptsPerNode].
 *
 * Off-graph consumers (e.g. memory extraction) that record the repair metric
 * directly do not need this buffer and pass an inline [RepairListener] instead.
 */
class CollectingRepairListener : RepairListener {

    private val buffer = mutableListOf<RepairAttempt>()

    /** The repair attempts recorded so far, in occurrence order. */
    val attempts: List<RepairAttempt> get() = buffer.toList()

    override fun onRepairAttempt(nodeName: String, attempt: Int, maxRepairs: Int) {
        buffer += RepairAttempt(nodeName = nodeName, attempt = attempt, maxRepairs = maxRepairs)
    }

    /**
     * One buffered repair attempt.
     *
     * @property nodeName Display label of the node whose output was repaired.
     * @property attempt 1-based index of this repair attempt.
     * @property maxRepairs Configured repair ceiling, for the progress fraction.
     */
    data class RepairAttempt(val nodeName: String, val attempt: Int, val maxRepairs: Int) {
        /**
         * The user-visible console line for this attempt, e.g.
         * `"Output repair 1/2 for node Decompose"`.
         */
        fun consoleMessage(): String = "Output repair $attempt/$maxRepairs for node $nodeName"
    }
}
