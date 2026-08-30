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
     * insert and leave two rows describing the same uninterrupted repeat, and so
     * the row cap holds at every observable moment.
     *
     * @param entity The refusal to record.
     * @param maxRecords Hard cap the table is trimmed to after an insert.
     */
    @Transaction
    suspend fun recordRefusal(entity: ExternalAutomationRequestEntity, maxRecords: Int) {
        val folded = collapseNewestRefusal(
            receivedAt = entity.receivedAt,
            requestId = entity.requestId,
            action = entity.action,
            targetKind = entity.targetKind,
            targetValue = entity.targetValue,
            statusKind = entity.statusKind,
            statusReason = entity.statusReason,
        )
        if (folded != 0) return
        insert(entity)
        // Trim in the same transaction, so the cap is an invariant of the table
        // rather than something the daily retention pass restores later. The fold
        // above only bounds a caller that repeats itself verbatim; one that varies
        // any keyed field — the pipeline id it names, say — inserts a fresh row
        // every time. The write rate here belongs to a third-party app, and the
        // retention worker runs at most daily and only while charging and idle, so
        // relying on it would leave a phone off the charger accumulating rows for a
        // day. Enforcing on insert costs one indexed DELETE on a bounded table.
        //
        // Refusals only. This trim is attacker-timed — it runs on a write whose rate
        // a third-party app sets — so it must never reach an admission: those rows
        // ARE the rate ledger, and evicting them would let a refusal flood reset the
        // ceiling and admit unlimited runs. They must also survive to carry their
        // run's terminal callback.
        enforceRefusalCap(maxRecords)
    }

    /**
     * Settles a terminal status onto the journal row of [runId], **once**.
     *
     * Guarded on the row still being un-settled, because a run can reach a terminal
     * status more than once: `INTERRUPTED` is terminal *and* resumable, so a run
     * killed by the platform settles once, resumes, and settles again. Without the
     * guard the caller would be told `Failed` and then `Completed` for one request —
     * a contradiction it cannot act on, having already reacted to the first message.
     * The first terminal outcome is the one that stands.
     *
     * A no-op when no row references [runId], which is what keeps the hook root-only
     * for free: a nested sub-pipeline child inherits its parent's origin but owns no
     * row.
     *
     * @param runId Id of the settled run.
     * @param statusKind Terminal status discriminator to write.
     * @param fromStatusKind The un-settled discriminator this may transition from.
     * @return `1` when the row was settled by this call, `0` when it already was.
     */
    @Query(
        "UPDATE external_automation_requests SET statusKind = :statusKind " +
            "WHERE runId = :runId AND statusKind = :fromStatusKind",
    )
    suspend fun settleStatus(runId: String, statusKind: String, fromStatusKind: String): Int

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
     * Reads the whole journal once, newest request first.
     *
     * Deliberately the same `ORDER BY` as [observeAll] — the export and the screen
     * describe one journal, and a row order that differed between them would make
     * an exported file impossible to check against what its owner is looking at.
     *
     * @return Every stored row, newest first.
     */
    @Query("SELECT * FROM external_automation_requests ORDER BY receivedAt DESC, id ASC")
    suspend fun getAllOrderedByReceivedAt(): List<ExternalAutomationRequestEntity>

    /**
     * Deletes every record received before [cutoff].
     *
     * @param cutoff Age cutoff, epoch-millis.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM external_automation_requests WHERE receivedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    /**
     * Trims **admitted requests** to the newest [maxRecords], leaving every request
     * still awaiting its outcome untouched however old it is.
     *
     * Partitioned from [enforceRefusalCap] rather than sharing one newest-N pool,
     * and that is the whole safety argument. A shared pool lets the high-volume
     * partition crowd out the low-volume one: refusals arrive at a rate a
     * third-party app chooses, admissions at most [
     * app.knotwork.android.domain.usecases.RunRateCeiling.EXTERNAL]'s allowance, so
     * a flood would push every settled admission out of the keep-set and the next
     * pass would delete rows the rate ceiling still counts — resetting it.
     * Partitioned, an admission can only be evicted by [maxRecords] newer
     * admissions, which the ceiling itself makes impossible inside its window.
     *
     * Sparing the un-settled ones additionally keeps a daily pass from stranding a
     * caller: dropping that row would leave its run unable to find the address to
     * report back to, and no record that the request ever existed.
     *
     * @param maxRecords Hard cap on retained admitted rows.
     * @return The number of rows deleted.
     */
    @Query(
        "DELETE FROM external_automation_requests " +
            "WHERE runId IS NOT NULL AND statusKind != 'Accepted' AND id NOT IN (" +
            "SELECT id FROM external_automation_requests " +
            "WHERE runId IS NOT NULL AND statusKind != 'Accepted' " +
            "ORDER BY receivedAt DESC LIMIT :maxRecords)",
    )
    suspend fun enforceAdmissionCap(maxRecords: Int): Int

    /**
     * Trims **refusals** to the newest [maxRecords], leaving admitted requests
     * untouched however old they are.
     *
     * Used on the write path as well as in retention, because that path's rate a
     * third-party app controls: an unrestricted trim there would be attacker-timed,
     * and evicting an admitted row would reset the rate ceiling
     * ([countAdmittedSince]) and drop the row an in-flight run needs to deliver its
     * terminal callback.
     *
     * @param maxRecords Hard cap on retained refusal rows.
     * @return The number of rows deleted.
     */
    @Query(
        "DELETE FROM external_automation_requests WHERE runId IS NULL AND id NOT IN (" +
            "SELECT id FROM external_automation_requests WHERE runId IS NULL " +
            "ORDER BY receivedAt DESC LIMIT :maxRecords)",
    )
    suspend fun enforceRefusalCap(maxRecords: Int): Int

    /**
     * Applies the retention limits — the age window, then a hard cap **per
     * partition** — in one transaction so the journal can never be observed
     * half-trimmed.
     *
     * The cap is applied to refusals and to admissions separately rather than to
     * the table as a whole: see [enforceAdmissionCap] for why one shared pool would
     * let a refusal flood evict the rows the rate ceiling counts.
     *
     * @param cutoff Age cutoff, epoch-millis; older rows are deleted first.
     * @param maxRecords Hard cap applied to each partition after the age pass.
     * @return The total number of rows deleted across the passes.
     */
    @Transaction
    suspend fun applyRetention(cutoff: Long, maxRecords: Int): Int =
        deleteOlderThan(cutoff) + enforceRefusalCap(maxRecords) + enforceAdmissionCap(maxRecords)
}
