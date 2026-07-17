package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes the trigger-evaluation journal of a single trigger, newest first.
 *
 * Backs the per-trigger journal surface (a diagnostic timeline of fires,
 * re-arms, and typed skips, plus the outcome of each fired run). The stream is
 * reactive: it re-emits whenever a new evaluation is recorded or a pending run
 * outcome is filled in.
 *
 * @property journal The journal store to read from.
 */
class ObserveTriggerJournalUseCase @Inject constructor(private val journal: TriggerJournalRepository) {

    /**
     * @param triggerId The trigger whose evaluation journal to observe.
     * @return A hot [Flow] of the trigger's evaluations, newest first.
     */
    operator fun invoke(triggerId: String): Flow<List<TriggerEvaluation>> = journal.observeByTrigger(triggerId)
}
