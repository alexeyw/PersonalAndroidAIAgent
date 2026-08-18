package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.knotwork.android.data.local.models.ExternalAutomationRequestEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `external_automation_requests` table (introduced in v58) backing the
 * external-automation request journal.
 */
@Dao
interface ExternalAutomationJournalDao {

    /**
     * Inserts one journal record (upsert by primary key). The id is a fresh UUID
     * per record, so in practice this always inserts; `REPLACE` only guards the
     * degenerate id-collision case.
     *
     * @param entity The row to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExternalAutomationRequestEntity)

    /**
     * Counts admitted requests received at or after [windowStart].
     *
     * Admission is `runId IS NOT NULL`: a run id is minted at the moment of
     * acceptance and only for acceptances, so this counts exactly the decisions the
     * ceiling governs and keeps counting them after they settle to `Completed` or
     * `Failed`. Counting `pipeline_runs` instead would be wrong by construction —
     * that row does not exist until the work reaches execution in-process.
     *
     * @param windowStart Earliest acceptance that still counts, epoch-millis.
     * @return How many requests were admitted inside the window.
     */
    @Query("SELECT COUNT(*) FROM external_automation_requests WHERE runId IS NOT NULL AND receivedAt >= :windowStart")
    suspend fun countAdmittedSince(windowStart: Long): Int

    /**
     * Counts admissions and inserts [entity] in one transaction, but only while the
     * ceiling allows it.
     *
     * Atomicity is the whole point: a `BroadcastReceiver` must answer immediately
     * and several can run concurrently, so a count followed by a separate insert
     * would let a burst of broadcasts each observe the same pre-burst total and
     * each be admitted.
     *
     * @param entity The admitted request, carrying its pre-minted run id.
     * @param windowStart Start of the rolling window, epoch-millis.
     * @param limit How many admissions the window allows.
     * @return `true` when the row was inserted, `false` when the ceiling refused it.
     */
    @Transaction
    suspend fun insertIfUnderCeiling(entity: ExternalAutomationRequestEntity, windowStart: Long, limit: Int): Boolean {
        if (countAdmittedSince(windowStart) >= limit) return false
        insert(entity)
        return true
    }

    /**
     * Folds a repeated refusal onto the newest row when that row is the identical
     * refusal, advancing its timestamp and correlation id to the latest occurrence.
     *
     * The match is deliberately on the newest row only, not on any older matching
     * row: the journal stays a chronological record, and only an uninterrupted run
     * of the same refusal collapses. Sender package is not part of the key because
     * it is not attested (see the domain model), and request id is not either
     * because callers mint a fresh one per attempt — keying on it would collapse
     * nothing.
     *
     * `IS` rather than `=` on the nullable columns: SQLite's `=` is never true
     * against `NULL`, so a refusal with no target would never match another.
     *
     * @param receivedAt Timestamp of the latest occurrence.
     * @param requestId Correlation id of the latest occurrence.
     * @param action Action of the repeated refusal.
     * @param targetKind Target kind of the repeated refusal, or `null`.
     * @param targetValue Target value of the repeated refusal, or `null`.
     * @param statusKind Status discriminator of the refusal.
     * @param statusReason Reason discriminator of the refusal.
     * @return `1` when a row was folded, `0` when this refusal is not a repeat.
     */
    @Query(
        "UPDATE external_automation_requests " +
            "SET repeatCount = repeatCount + 1, receivedAt = :receivedAt, requestId = :requestId " +
            "WHERE id = (SELECT id FROM external_automation_requests ORDER BY receivedAt DESC, id ASC LIMIT 1) " +
            "AND runId IS NULL AND action = :action AND targetKind IS :targetKind " +
            "AND targetValue IS :targetValue AND statusKind = :statusKind AND statusReason IS :statusReason",
    )
    suspend fun collapseNewestRefusal(
        receivedAt: Long,
        requestId: String,
        action: String,
        targetKind: String?,
        targetValue: String?,
        statusKind: String,
        statusReason: String?,
    ): Int

    /**
     * Records a refusal, folding it onto the newest row when it repeats it.
     *
     * One transaction so a concurrent burst cannot interleave a collapse and an
     * insert and leave two rows describing the same uninterrupted repeat.
     *
     * @param entity The refusal to record.
     */
    @Transaction
    suspend fun recordRefusal(entity: ExternalAutomationRequestEntity) {
        val folded = collapseNewestRefusal(
            receivedAt = entity.receivedAt,
            requestId = entity.requestId,
            action = entity.action,
            targetKind = entity.targetKind,
            targetValue = entity.targetValue,
            statusKind = entity.statusKind,
            statusReason = entity.statusReason,
        )
        if (folded == 0) insert(entity)
    }

    /**
     * Attributes a terminal status onto the journal row of [runId]. A no-op when no
     * row references it — which is what keeps the hook root-only for free, since a
     * nested sub-pipeline child inherits its parent's origin but owns no row.
     *
     * @param runId Id of the settled run.
     * @param statusKind Terminal status discriminator.
     */
    @Query("UPDATE external_automation_requests SET statusKind = :statusKind WHERE runId = :runId")
    suspend fun updateStatus(runId: String, statusKind: String)

    /**
     * Reads the journal row belonging to an admitted run.
     *
     * @param runId Id of the run.
     * @return The row, or `null` when the run did not come from this entry point.
     */
    @Query("SELECT * FROM external_automation_requests WHERE runId = :runId LIMIT 1")
    suspend fun findByRunId(runId: String): ExternalAutomationRequestEntity?

    /**
     * Observes the whole journal, newest request first.
     *
     * @return A [Flow] emitting the journal rows on every change.
     */
    @Query("SELECT * FROM external_automation_requests ORDER BY receivedAt DESC, id ASC")
    fun observeAll(): Flow<List<ExternalAutomationRequestEntity>>

    /**
     * Deletes every record received before [cutoff].
     *
     * @param cutoff Age cutoff, epoch-millis.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM external_automation_requests WHERE receivedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    /**
     * Trims the table to its newest [maxRecords] rows, deleting the rest.
     *
     * @param maxRecords Hard cap on retained rows.
     * @return The number of rows deleted.
     */
    @Query(
        "DELETE FROM external_automation_requests WHERE id NOT IN (" +
            "SELECT id FROM external_automation_requests ORDER BY receivedAt DESC LIMIT :maxRecords)",
    )
    suspend fun enforceCap(maxRecords: Int): Int

    /**
     * Applies both retention limits — the age window then the hard cap — in one
     * transaction so the journal can never be observed half-trimmed.
     *
     * @param cutoff Age cutoff, epoch-millis; older rows are deleted first.
     * @param maxRecords Hard cap applied after the age pass.
     * @return The total number of rows deleted across both passes.
     */
    @Transaction
    suspend fun applyRetention(cutoff: Long, maxRecords: Int): Int = deleteOlderThan(cutoff) + enforceCap(maxRecords)
}
