package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.TriggerHitlEvent
import app.knotwork.android.domain.models.TriggerHitlResolution
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [RecordTriggerHitlEventUseCase]: it normalises the reporting run
 * to the root of its run tree — the id a journal row actually carries — and
 * forwards the event unchanged.
 */
class RecordTriggerHitlEventUseCaseTest {

    private val journal = mockk<TriggerJournalRepository>(relaxUnitFun = true)
    private val pipelineRunRepository = mockk<PipelineRunRepository>()
    private val useCase = RecordTriggerHitlEventUseCase(journal, pipelineRunRepository)

    @Test
    fun `given a child run when a gate is raised then it is recorded against the root run`() = runTest {
        // A gate raised inside a PIPELINE-node child run belongs, for the
        // journal, to the run the trigger actually enqueued.
        coEvery { pipelineRunRepository.getRootRunId("child-run") } returns "root-run"
        val event = TriggerHitlEvent.Raised(PendingInteractionKind.APPROVAL)

        useCase("child-run", event)

        coVerify(exactly = 1) { journal.recordHitlEvent("root-run", event) }
    }

    @Test
    fun `given a top-level run when a gate is raised then the run id is used as is`() = runTest {
        coEvery { pipelineRunRepository.getRootRunId("run-1") } returns "run-1"
        val event = TriggerHitlEvent.Raised(PendingInteractionKind.CLARIFICATION)

        useCase("run-1", event)

        coVerify(exactly = 1) { journal.recordHitlEvent("run-1", event) }
    }

    @Test
    fun `given an unknown run when an event is reported then it falls back to the given id`() = runTest {
        // No run record (already reaped, or never persisted under this id): the
        // journal update then simply matches no row, which is a no-op — reporting
        // must not be dropped on the floor before it gets there.
        coEvery { pipelineRunRepository.getRootRunId("ghost-run") } returns null

        useCase("ghost-run", TriggerHitlEvent.Parked)

        coVerify(exactly = 1) { journal.recordHitlEvent("ghost-run", TriggerHitlEvent.Parked) }
    }

    @Test
    fun `given a non-persisted run when an event is reported then nothing is looked up or written`() = runTest {
        useCase(null, TriggerHitlEvent.Resolved(TriggerHitlResolution.ABANDONED))

        coVerify(exactly = 0) { pipelineRunRepository.getRootRunId(any()) }
        coVerify(exactly = 0) { journal.recordHitlEvent(any(), any()) }
    }

    @Test
    fun `given a resolution event when reported then it is forwarded verbatim`() = runTest {
        coEvery { pipelineRunRepository.getRootRunId("run-2") } returns "run-2"
        val event = TriggerHitlEvent.Resolved(TriggerHitlResolution.TIMED_OUT)

        useCase("run-2", event)

        coVerify(exactly = 1) { journal.recordHitlEvent("run-2", event) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given PENDING as a resolution when constructing the event then it is rejected`() {
        // PENDING describes a gate that has NOT been resolved; accepting it as a
        // resolution would let a settled gate be re-opened by a stray report.
        TriggerHitlEvent.Resolved(TriggerHitlResolution.PENDING)
    }
}
