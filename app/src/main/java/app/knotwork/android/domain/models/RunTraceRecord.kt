package app.knotwork.android.domain.models

/**
 * One record of the persistent pipeline-run trace.
 *
 * A run's trace is the ordered union of two record kinds: per-node
 * input/output snapshots ([NodeIo]) and console log events ([ConsoleEntry]).
 * The engine appends records through
 * [app.knotwork.android.domain.repositories.RunTraceRepository] as the run
 * executes; the console replays them when a session is reopened after the run
 * finished (or while it is still executing in the background).
 *
 * @property runId The pipeline run the record belongs to.
 * @property sessionId The chat session that owns the run.
 * @property seq Zero-based monotonic position of the record within the run.
 *   Unique per run across both record kinds — the console replay/live seam
 *   deduplicates by this number.
 * @property timestamp Wall-clock time of the record in
 *   `System.currentTimeMillis()` units.
 */
sealed class RunTraceRecord {
    abstract val runId: String
    abstract val sessionId: String
    abstract val seq: Long
    abstract val timestamp: Long

    /**
     * Input/output snapshot of one executed pipeline node. Backs the Vars and
     * Traces console tabs for replayed runs and the checkpoint/resume
     * mechanism (a completed node's recorded output substitutes for
     * re-execution).
     *
     * @property nodeId The graph node id (unique within the pipeline graph).
     * @property nodeType The [app.knotwork.android.domain.models.NodeType]
     *   name of the executed node.
     * @property inputText The text the node received as input.
     * @property outputText The text the node produced.
     * @property durationMs How long the node took to execute, in milliseconds.
     * @property tokenCount The approximate number of LLM tokens produced, or
     *   `null` for non-LLM nodes.
     * @property conditionResult The recorded boolean verdict of an
     *   `IF_CONDITION` node, or `null` for every other node type. Persisted so
     *   checkpoint resume re-routes the True/False branch exactly as the
     *   interrupted run did, without re-evaluating the condition.
     * @property routingKey The recorded routing key of an `INTENT_ROUTER` or
     *   `EVALUATION` node (edge-label selector), or `null` for every other
     *   node type. Persisted for the same branch-restoration reason as
     *   [conditionResult].
     * @property resolvedToolName The tool a `TOOL` node actually executed
     *   (relevant for "auto"-configured nodes), or `null` for non-TOOL nodes.
     *   Persisted so a replayed tool observation is attributed to the real
     *   tool in the `--- Tool Results ---` context block.
     */
    data class NodeIo(
        override val runId: String,
        override val sessionId: String,
        override val seq: Long,
        override val timestamp: Long,
        val nodeId: String,
        val nodeType: String,
        val inputText: String,
        val outputText: String,
        val durationMs: Long,
        val tokenCount: Int?,
        val conditionResult: Boolean? = null,
        val routingKey: String? = null,
        val resolvedToolName: String? = null,
    ) : RunTraceRecord()

    /**
     * One persisted console log event, mirroring [ConsoleEvent]. Backs the
     * Logs console tab for replayed runs.
     *
     * @property type Category of the event (drives line color and filtering).
     * @property message Pre-formatted human-readable console line.
     */
    data class ConsoleEntry(
        override val runId: String,
        override val sessionId: String,
        override val seq: Long,
        override val timestamp: Long,
        val type: ConsoleEventType,
        val message: String,
    ) : RunTraceRecord()

    /**
     * Snapshot of the long-term memory chunks resolved for this run, written
     * at the moment the run's single lazy memory retrieval actually happens.
     * Checkpoint resume seeds the engine's memoized memory list from this
     * record instead of re-running retrieval, so a resumed run sees exactly
     * the `--- Long-Term Memory ---` block the interrupted run saw (and the
     * per-chunk usage counters are not double-counted).
     *
     * Only the chunk identity and text survive persistence — embeddings are
     * neither needed to rebuild the context block nor worth the storage; a
     * chunk restored from a snapshot carries an empty embedding vector.
     *
     * @property entries The resolved memory chunks in retrieval-rank order.
     */
    data class MemorySnapshot(
        override val runId: String,
        override val sessionId: String,
        override val seq: Long,
        override val timestamp: Long,
        val entries: List<MemoryChunk>,
    ) : RunTraceRecord()
}
