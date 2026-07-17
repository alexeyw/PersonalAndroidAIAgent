package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerRunOutcome
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import javax.inject.Inject

/**
 * Records the terminal fate of a background run started by a firing trigger,
 * completing the two-phase journal row that
 * [RecordTriggerEvaluationUseCase] opened.
 *
 * A firing evaluation writes its journal row up front (run enqueued, outcome
 * unknown); when the run later settles this use case attributes the terminal
 * [TriggerRunOutcome] back onto that row by its run id. It is a no-op when no
 * journal row references the run — the entry may have been aged out by retention,
 * or the run may not have originated from a trigger at all — so callers on the
 * generic run-completion path can invoke it unconditionally.
 *
 * @property journal The journal store the outcome is written to.
 */
class RecordTriggerRunOutcomeUseCase @Inject constructor(private val journal: TriggerJournalRepository) {

    /**
     * Attributes [outcome] to the journal row whose run is [runId].
     *
     * @param runId Id of the settled background run.
     * @param outcome The terminal fate to record.
     */
    suspend operator fun invoke(runId: String, outcome: TriggerRunOutcome) {
        journal.recordRunOutcome(runId, outcome)
    }
}
