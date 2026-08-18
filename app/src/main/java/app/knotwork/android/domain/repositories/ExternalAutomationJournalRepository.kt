package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.ExternalAutomationJournalEntry
import app.knotwork.android.domain.models.ExternalAutomationStatus
import kotlinx.coroutines.flow.Flow

/**
 * Store of the external-automation request journal.
 *
 * Modelled on [TriggerJournalRepository], with one deliberate difference in
 * posture: the scheduler's rate guard may fail **open** because an unreadable
 * database there only weakens a diagnostic bound the app itself set, whereas the
 * same posture here would let an unreadable database lift the ceiling on an entry
 * point driven by third-party code. So [admitAcceptedWithinCeiling] fails
 * **closed** and says so in its return value.
 *
 * Reads and non-admission writes stay best-effort, exactly as the trigger journal:
 * a journal defect must never disturb the run it merely observes.
 */
interface ExternalAutomationJournalRepository {

    /**
     * Records a refused request.
     *
     * Consecutive refusals that repeat an earlier one are collapsed onto that row
     * by incrementing its repeat count rather than inserting a new row — the write
     * rate at this entry point is set by a third-party app, and an app looping
     * against a switched-off contract must not be able to grow the encrypted
     * database one row per attempt.
     *
     * @param entry The refusal to record. Its status must be a refusal
     *   ([ExternalAutomationStatus.Rejected] or [ExternalAutomationStatus.Blocked]).
     */
    suspend fun recordRefusal(entry: ExternalAutomationJournalEntry)

    /**
     * Atomically admits a request if the rate ceiling still allows it.
     *
     * The count and the insert are one transaction because a `BroadcastReceiver`
     * answers immediately and several can be in flight at once: counting first and
     * inserting afterwards would let a burst of broadcasts each read the same
     * pre-burst count and each be admitted. For the same reason the count is taken
     * over **accepted journal rows** and never over `pipeline_runs`, whose row does
     * not exist until the work reaches execution in-process.
     *
     * @param entry The request to admit, carrying its pre-minted run id.
     * @param windowStartEpochMs Earliest acceptance that still counts toward the
     *   ceiling.
     * @param limitPerWindow How many acceptances the window allows.
     * @return `true` when the row was inserted and the caller may schedule the run;
     *   `false` when the ceiling refused it **or** the journal could not be written
     *   — both mean "do not start work", which is why they share a return value.
     */
    suspend fun admitAcceptedWithinCeiling(
        entry: ExternalAutomationJournalEntry,
        windowStartEpochMs: Long,
        limitPerWindow: Int,
    ): Boolean

    /**
     * Settles the terminal outcome of an admitted request onto its journal row.
     *
     * A no-op when no row references [runId] — which is what makes the hook
     * root-only for free: a nested sub-pipeline child inherits its parent's origin
     * but never owns a journal row of its own.
     *
     * @param runId Id of the settled run.
     * @param status The terminal status to attribute.
     */
    suspend fun recordOutcome(runId: String, status: ExternalAutomationStatus)

    /**
     * Reads the journal row an admitted run belongs to.
     *
     * @param runId Id of the run.
     * @return The row, or `null` when the run did not come from this entry point.
     */
    suspend fun findByRunId(runId: String): ExternalAutomationJournalEntry?

    /**
     * Observes the whole journal, newest request first.
     *
     * @return A [Flow] emitting the journal on every change; an empty list when the
     *   store cannot be read.
     */
    fun observeAll(): Flow<List<ExternalAutomationJournalEntry>>

    /**
     * Applies the retention policy — age window first, then the hard row cap — in
     * one transaction.
     *
     * @param olderThanEpochMs Age cutoff; rows received before it are deleted.
     * @param maxRecords Hard cap on retained rows after the age pass. Must be
     *   positive: a non-positive cap would delete the entire journal.
     * @return The total number of rows deleted.
     */
    suspend fun applyRetention(olderThanEpochMs: Long, maxRecords: Int): Int
}
