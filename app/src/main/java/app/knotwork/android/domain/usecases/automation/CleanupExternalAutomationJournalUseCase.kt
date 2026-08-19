package app.knotwork.android.domain.usecases.automation

import app.knotwork.android.domain.repositories.ExternalAutomationJournalRepository
import javax.inject.Inject

/**
 * Applies the external-automation journal retention policy: one bounded pass that
 * keeps the request log useful without letting it grow forever.
 *
 * **Retention here is a safety property, not housekeeping.** The write rate of
 * every other table in this app is set by the user; the write rate of this one is
 * set by whatever third-party app is installed, and it is highest exactly when the
 * contract is switched **off** — because a refusal is an event too, so an app
 * looping against a disabled entry point still produces rows. Consecutive
 * identical refusals collapse onto one row at write time, which bounds that loop;
 * these two limits bound everything else.
 *
 * Both are applied in a single pass:
 *  - **Age window** ([RETENTION_WINDOW_DAYS]) — requests received longer ago than
 *    the window are deleted. A collapsed refusal ages from its most recent
 *    occurrence, so a repeating problem stays visible while it is still happening.
 *  - **Hard record cap** ([MAX_RECORDS]) — after the age pass the table is trimmed
 *    to its newest [MAX_RECORDS] rows.
 *
 * The limits mirror the trigger journal's and are domain constants rather than
 * user settings, for the same reason: this is an internal diagnostic surface, not
 * user content. Storage failures are absorbed by the repository per its
 * best-effort contract — a failed pass reports zero deletions and the next
 * scheduled pass retries.
 *
 * @property journal The journal store the retention pass runs against.
 */
class CleanupExternalAutomationJournalUseCase @Inject constructor(
    private val journal: ExternalAutomationJournalRepository,
) {

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

    /** Retention limits for the external-automation journal (see the class KDoc). */
    companion object {
        /** Age window: requests received longer ago than this are deleted. */
        const val RETENTION_WINDOW_DAYS: Long = 30L

        /** Hard cap on retained journal rows after the age pass. */
        const val MAX_RECORDS: Int = 2_000

        /** Milliseconds in one day, for the age-cutoff arithmetic. */
        private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1_000L
    }
}
