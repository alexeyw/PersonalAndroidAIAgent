package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerHealthInputs
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes the health-relevant journal facts of every trigger, keyed by trigger
 * id, for the trigger list's health-badge derivation.
 *
 * A thin reactive read over [TriggerJournalRepository.observeHealthInputs]; the
 * pure classification of the facts into a badge state is
 * [TriggerHealthEvaluator]'s job, kept separate so it can be unit-tested against
 * a fixed clock without a journal.
 *
 * @property journal The journal store to read from.
 */
class ObserveTriggerHealthInputsUseCase @Inject constructor(private val journal: TriggerJournalRepository) {

    /**
     * @return A hot [Flow] of `triggerId → latest health inputs`, re-emitting on
     *   every journal change.
     */
    operator fun invoke(): Flow<Map<String, TriggerHealthInputs>> = journal.observeHealthInputs()
}
