package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.RunTraceRecord

/**
 * Persistent store of pipeline-run traces — the ordered stream of per-node
 * input/output snapshots and console events emitted while a run executes
 * (see [RunTraceRecord]).
 *
 * **Write path (buffered).** [append] does not hit storage on every call:
 * implementations accumulate records and flush them in batches (by size
 * and/or timer) so trace persistence never competes with on-device inference
 * for disk I/O on every streamed token. [flush] forces the buffer out and is
 * called by the engine at suspension points (approval / clarification waits)
 * and terminal points (completion, failure, cancellation), guaranteeing the
 * persisted trace is complete at any moment the run can pause or end.
 *
 * **Best-effort contract.** Trace persistence is an observability concern and
 * must never break the run it records: implementations absorb storage
 * failures (logging them) instead of throwing. Reads degrade to an empty
 * list. `kotlinx.coroutines.CancellationException` always propagates.
 */
interface RunTraceRepository {
    /**
     * Appends one record to the run's trace. The record becomes durable on
     * the next flush — triggered by buffer size, timer, or an explicit
     * [flush] call.
     *
     * @param record The trace record to append.
     */
    suspend fun append(record: RunTraceRecord)

    /**
     * Forces all buffered records to storage. Called at suspension and
     * terminal points of a run so the persisted trace is complete whenever
     * the run pauses or ends. Safe to call with an empty buffer.
     */
    suspend fun flush()

    /**
     * Loads the full persisted trace of one run, ordered by
     * [RunTraceRecord.seq] (the order the engine emitted the records).
     *
     * @param runId The pipeline run id.
     * @return The run's trace records, oldest first; empty when the run has
     *   no persisted trace or the read fails.
     */
    suspend fun getTraceForRun(runId: String): List<RunTraceRecord>
}
