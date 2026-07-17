package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.models.TriggerEvaluationVerdict
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Records one trigger evaluation into the trigger-evaluation journal.
 *
 * This is the single write path honouring the journal's **no-silent-skips**
 * invariant: every consumer that evaluates a trigger calls this exactly once per
 * evaluated *(trigger × moment)*, so that fire, re-arm and every skip reason are
 * all attributable after the fact (see [TriggerEvaluation]).
 *
 * The record's identity ([id]) and timestamp ([evaluatedAt]) are stamped here,
 * both injectable so the use case stays deterministically unit-testable. A
 * [runId] is meaningful only for a [TriggerEvaluationVerdict.Fired] verdict; for
 * any other verdict it is normalised to `null` so a caller can never accidentally
 * attach a run to a skip.
 *
 * **Fired ⇒ runId invariant.** A [TriggerEvaluationVerdict.Fired] verdict must
 * carry a non-null [runId] — it is the key the later
 * [RecordTriggerRunOutcomeUseCase] matches on. A fired verdict recorded without a
 * run id would produce a row whose run outcome could never be attributed, so this
 * case is logged as a contract violation (rather than thrown — the journal is a
 * best-effort observer that must never abort the run it describes); the row is
 * still persisted so the fire itself is not lost.
 *
 * @property journal The journal store the evaluation is written to.
 */
class RecordTriggerEvaluationUseCase @Inject constructor(private val journal: TriggerJournalRepository) {

    /**
     * Persists one evaluation verdict.
     *
     * @param triggerId Id of the trigger that was evaluated.
     * @param source Where the evaluation originated (poll / event / charging sweep).
     * @param verdict The decision reached.
     * @param runId Id of the enqueued run — kept only when [verdict] is
     *   [TriggerEvaluationVerdict.Fired], otherwise ignored.
     * @param evaluatedAt Evaluation moment, epoch-millis (injectable for tests).
     * @param id Record id (injectable for tests); defaults to a fresh UUID.
     * @return The persisted [TriggerEvaluation], so a firing caller can correlate
     *   the later run-outcome write without re-reading the store.
     */
    suspend operator fun invoke(
        triggerId: String,
        source: TriggerEvaluationSource,
        verdict: TriggerEvaluationVerdict,
        runId: String? = null,
        evaluatedAt: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString(),
    ): TriggerEvaluation {
        val isFired = verdict is TriggerEvaluationVerdict.Fired
        if (isFired && runId == null) {
            Timber.w(
                "Trigger %s recorded a Fired verdict without a runId; its run outcome will be unattributable.",
                triggerId,
            )
        }
        val evaluation = TriggerEvaluation(
            id = id,
            triggerId = triggerId,
            evaluatedAt = evaluatedAt,
            source = source,
            verdict = verdict,
            runId = if (isFired) runId else null,
            outcome = null,
        )
        journal.recordEvaluation(evaluation)
        return evaluation
    }
}
