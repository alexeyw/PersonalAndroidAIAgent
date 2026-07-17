package app.knotwork.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.knotwork.android.data.local.models.TriggerEvaluationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `trigger_evaluations` table (v51) backing the trigger-evaluation
 * journal.
 */
@Dao
interface TriggerJournalDao {

    /**
     * Inserts one evaluation record (upsert by primary key). The record id is a
     * fresh UUID per evaluation, so in practice this always inserts; `REPLACE`
     * only guards the degenerate id-collision case.
     *
     * @param entity The row to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TriggerEvaluationEntity)

    /**
     * Attributes a terminal run outcome onto the journal row(s) whose run matches
     * [runId]. A no-op when no row references [runId].
     *
     * @param runId Id of the settled run.
     * @param outcomeKind Terminal outcome discriminator.
     * @param outcomeError Human-readable error for a failure outcome, else `null`.
     */
    @Query(
        "UPDATE trigger_evaluations SET outcomeKind = :outcomeKind, outcomeError = :outcomeError " +
            "WHERE runId = :runId",
    )
    suspend fun updateOutcome(runId: String, outcomeKind: String, outcomeError: String?)

    /**
     * Observes a single trigger's journal, newest evaluation first.
     *
     * @param triggerId The trigger whose evaluations to observe.
     * @return A [Flow] emitting the trigger's evaluation rows on every change.
     */
    @Query("SELECT * FROM trigger_evaluations WHERE triggerId = :triggerId ORDER BY evaluatedAt DESC")
    fun observeByTrigger(triggerId: String): Flow<List<TriggerEvaluationEntity>>

    /**
     * Deletes every record older than [cutoff].
     *
     * @param cutoff Age cutoff, epoch-millis.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM trigger_evaluations WHERE evaluatedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    /**
     * Trims the table to its newest [maxRecords] rows, deleting the rest.
     *
     * @param maxRecords Hard cap on retained rows.
     * @return The number of rows deleted.
     */
    @Query(
        "DELETE FROM trigger_evaluations WHERE id NOT IN (" +
            "SELECT id FROM trigger_evaluations ORDER BY evaluatedAt DESC LIMIT :maxRecords)",
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
