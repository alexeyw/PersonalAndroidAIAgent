package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerRunOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Persistence port for the **trigger-evaluation journal** — the durable,
 * on-device log of every trigger evaluation and the fate of the runs it started
 * (see [TriggerEvaluation]).
 *
 * The interface lives in `domain` (implemented in `data` by
 * `TriggerJournalRepositoryImpl`) so the journal use cases depend only on this
 * port, never on Room. The journal is stored in the same SQLCipher-encrypted
 * database as the rest of the app and nothing it holds ever leaves the device.
 *
 * **Observer contract.** Recording is a pure observer of the trigger runtime: an
 * implementation must never let a storage failure propagate into (and abort) the
 * evaluation or run being observed. It absorbs write failures per the
 * best-effort convention shared with the usage-telemetry store, re-throwing only
 * `CancellationException`.
 */
interface TriggerJournalRepository {

    /**
     * Records one trigger evaluation. Called once per evaluated
     * *(trigger × moment)* to honour the journal's no-silent-skips invariant.
     *
     * For a [Fired][app.knotwork.android.domain.models.TriggerEvaluationVerdict.Fired]
     * verdict the row is written with [TriggerEvaluation.runId] set and its
     * outcome still `null`; the terminal outcome is filled in later via
     * [recordRunOutcome].
     *
     * @param evaluation The evaluation record to persist.
     */
    suspend fun recordEvaluation(evaluation: TriggerEvaluation)

    /**
     * Attributes the terminal [outcome] of a background run back onto the
     * journal row that started it (matched by [TriggerEvaluation.runId]).
     * Idempotent and a no-op when no row references [runId] (e.g. the journal
     * entry was already aged out by retention).
     *
     * @param runId Id of the enqueued run whose outcome is now known.
     * @param outcome The terminal fate to record.
     */
    suspend fun recordRunOutcome(runId: String, outcome: TriggerRunOutcome)

    /**
     * Observes the journal of a single trigger, newest evaluation first. Backs
     * the per-trigger journal UI.
     *
     * @param triggerId The trigger whose evaluations to observe.
     * @return A hot [Flow] emitting the trigger's evaluation list on every change.
     */
    fun observeByTrigger(triggerId: String): Flow<List<TriggerEvaluation>>

    /**
     * Applies the journal retention policy in one bounded pass: deletes every
     * record older than [olderThanEpochMs] and then trims the table to its newest
     * [maxRecords] rows, so the journal can never grow without bound even under a
     * pathologically chatty poll.
     *
     * @param olderThanEpochMs Age cutoff, epoch-millis; rows with an earlier
     *   [TriggerEvaluation.evaluatedAt] are deleted.
     * @param maxRecords Hard cap on retained rows after the age pass; the newest
     *   [maxRecords] survive, the rest are deleted.
     * @return The total number of rows deleted across both passes.
     */
    suspend fun applyRetention(olderThanEpochMs: Long, maxRecords: Int): Int
}
