package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerHitlEvent
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import javax.inject.Inject

/**
 * Reports one human-in-the-loop transition of a running pipeline to the
 * trigger-evaluation journal.
 *
 * This is the single seam through which every HITL gate — raised, parked or
 * resolved — reaches the journal, so the gate owners (the tool-invocation gate,
 * the clarification executor, the parked-run resumer) stay free of both the
 * journal's storage concerns and the run-tree lookup below.
 *
 * **Root attribution.** A journal row references the run id minted when the
 * trigger fired, which is always the **root** of the run tree; a gate, however,
 * may be raised deep inside a child run started by a `PIPELINE` node. Reporting
 * the executing run id verbatim would silently miss those gates (the journal
 * update simply matches nothing), so the executing id is resolved to its root
 * first — the same normalisation [ParkedRunResumer.failPark] applies when it
 * settles a park. An unknown run (no record, e.g. a non-persisted editor run)
 * falls back to the id as given, where it harmlessly matches no row.
 *
 * **Best-effort, like the rest of the journal.** The call is a pure observer of
 * the run it describes: the journal implementation absorbs its own storage
 * failures, and nothing here throws on a missing row — a run must never fail
 * because its diagnostics could not be written.
 *
 * @property journal The journal store the event is folded onto.
 * @property pipelineRunRepository Used to resolve the executing run to its root.
 */
class RecordTriggerHitlEventUseCase @Inject constructor(
    private val journal: TriggerJournalRepository,
    private val pipelineRunRepository: PipelineRunRepository,
) {

    /**
     * Reports [event] for the run [runId].
     *
     * @param runId Id of the run whose gate this is, as known to the caller —
     *   the executing (possibly child) run. `null` for non-persisted runs, which
     *   own no journal row and are skipped without a lookup.
     * @param event The transition to report.
     */
    suspend operator fun invoke(runId: String?, event: TriggerHitlEvent) {
        if (runId == null) return
        val rootId = pipelineRunRepository.getRootRunId(runId) ?: runId
        journal.recordHitlEvent(rootId, event)
    }
}
