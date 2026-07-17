package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.TriggerJournalRepository
import javax.inject.Inject

/**
 * Applies the trigger-evaluation journal retention policy: one bounded cleanup
 * pass that keeps the diagnostic log useful without letting it grow forever.
 *
 * Two limits bound the journal at rest, both applied in a single pass:
 *  - **Age window** ([RETENTION_WINDOW_DAYS]) — evaluations older than the window
 *    are deleted. Diagnostics lose value quickly; a month of history is ample for
 *    explaining a background-reliability regression.
 *  - **Hard record cap** ([MAX_RECORDS]) — after the age pass the table is trimmed
 *    to its newest [MAX_RECORDS] rows, so even a pathologically chatty poll (many
 *    evaluations per day across many triggers) cannot bloat the database between
 *    two age windows.
 *
 * The policy is expressed as domain constants rather than user settings: the
 * journal is an internal diagnostic surface, not user content, so it needs no
 * per-user tuning. The pass is invoked from the same daily charging + idle
 * maintenance window as the other database-tidying jobs.
 *
 * Storage failures are absorbed by the repository per its best-effort contract —
 * a failed pass simply reports zero deletions and the next scheduled pass retries.
 *
 * @property journal The journal store the retention pass runs against.
 */
class CleanupTriggerJournalUseCase @Inject constructor(private val journal: TriggerJournalRepository) {

    /**
     * Runs one retention pass.
     *
     * @param nowMillis Current wall-clock time, epoch-millis (injectable for
     *   tests); the age cutoff is [RETENTION_WINDOW_DAYS] before it.
     * @return The number of journal rows deleted by the pass.
     */
    suspend operator fun invoke(nowMillis: Long = System.currentTimeMillis()): Int {
        val cutoff = nowMillis - RETENTION_WINDOW_DAYS * MILLIS_PER_DAY
        return journal.applyRetention(olderThanEpochMs = cutoff, maxRecords = MAX_RECORDS)
    }

    /** Retention limits for the trigger-evaluation journal (see the class KDoc). */
    companion object {
        /** Age window: evaluations older than this many days are deleted. */
        const val RETENTION_WINDOW_DAYS: Long = 30L

        /** Hard cap on retained journal rows after the age pass. */
        const val MAX_RECORDS: Int = 2_000

        /** Milliseconds in one day, for the age-cutoff arithmetic. */
        private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1_000L
    }
}
