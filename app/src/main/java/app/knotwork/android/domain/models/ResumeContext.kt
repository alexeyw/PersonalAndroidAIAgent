package app.knotwork.android.domain.models

/**
 * Checkpoint payload that switches `GraphExecutionEngine` into resume mode
 * for an interrupted run.
 *
 * The engine walks the graph exactly as on a fresh start, but as long as the
 * seq-ordered [records] cursor is not exhausted, each visited node consumes
 * the next recorded snapshot instead of executing: the recorded output (and
 * the recorded `conditionResult` / `routingKey` routing verdicts) feed the
 * same control-flow code that live results would, so queue iterations,
 * branch picks and the inter-node input text are re-derived deterministically
 * without re-running a single executor. The first node the cursor has no
 * record for is where real execution restarts.
 *
 * A seq-ordered cursor — not a `nodeId → result` map — is deliberate: inside
 * a `QUEUE_PROCESSOR` loop the same node executes once per queue item, so a
 * map keyed by node id would be ambiguous. For linear graphs the cursor
 * degenerates to exactly that map.
 *
 * **TOOL-node asymmetry.** A TOOL node *with* a recorded snapshot completed
 * before the interruption — its observation is replayed, because re-invoking
 * a tool whose side effects already happened is strictly worse than reusing
 * the recorded observation. A TOOL node *without* a snapshot is the node the
 * run died on: whether the tool ever ran is unknowable, so it is never
 * guessed — the node executes live from scratch, raising a fresh
 * human-in-the-loop approval under the standard risk contract.
 *
 * @property records The run's persisted per-node snapshots in seq order —
 *   strictly a prefix of the interrupted execution, which is what makes the
 *   cursor model sound. Empty when the run died before its first node
 *   completed (resume then equals a restart that skips re-saving the user
 *   message).
 * @property memorySnapshot The long-term memory chunks recorded by the
 *   interrupted run's single lazy retrieval, or `null` when that run never
 *   resolved memory (a node requesting memory after the resume point then
 *   triggers a fresh retrieval, exactly as the original run would have).
 * @property nextSeq The first free trace sequence number, i.e. the
 *   interrupted run's highest persisted `seq` plus one. The resumed engine
 *   starts its trace counter here so appended records keep the per-run
 *   uniqueness the console replay/live seam deduplicates by.
 */
data class ResumeContext(
    val records: List<RunTraceRecord.NodeIo>,
    val memorySnapshot: List<MemoryChunk>?,
    val nextSeq: Long,
)
