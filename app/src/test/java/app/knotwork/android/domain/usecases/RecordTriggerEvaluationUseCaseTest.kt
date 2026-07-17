package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.models.TriggerEvaluationVerdict
import app.knotwork.android.domain.models.TriggerSkipReason
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [RecordTriggerEvaluationUseCase]: it stamps the record's id and
 * timestamp, persists exactly one evaluation, and normalises the run id so only a
 * fired verdict retains it.
 */
class RecordTriggerEvaluationUseCaseTest {

    private val journal = mockk<TriggerJournalRepository>(relaxUnitFun = true)
    private val useCase = RecordTriggerEvaluationUseCase(journal)

    @Test
    fun `given a fired verdict when invoked then persists it with the run id and stamped fields`() = runTest {
        val captured = slot<TriggerEvaluation>()

        val returned = useCase(
            triggerId = "trig-1",
            source = TriggerEvaluationSource.POLL,
            verdict = TriggerEvaluationVerdict.Fired,
            runId = "run-9",
            evaluatedAt = 1_000L,
            id = "eval-1",
        )

        coVerify(exactly = 1) { journal.recordEvaluation(capture(captured)) }
        val expected = TriggerEvaluation(
            id = "eval-1",
            triggerId = "trig-1",
            evaluatedAt = 1_000L,
            source = TriggerEvaluationSource.POLL,
            verdict = TriggerEvaluationVerdict.Fired,
            runId = "run-9",
            outcome = null,
        )
        assertEquals(expected, captured.captured)
        assertEquals(expected, returned)
    }

    @Test
    fun `given a skipped verdict with a run id when invoked then the run id is dropped`() = runTest {
        val captured = slot<TriggerEvaluation>()

        useCase(
            triggerId = "trig-1",
            source = TriggerEvaluationSource.EVENT,
            verdict = TriggerEvaluationVerdict.Skipped(TriggerSkipReason.CONDITION_NOT_MET),
            runId = "run-should-be-ignored",
            evaluatedAt = 2_000L,
            id = "eval-2",
        )

        coVerify(exactly = 1) { journal.recordEvaluation(capture(captured)) }
        assertNull("A non-fired verdict must never retain a run id", captured.captured.runId)
    }

    @Test
    fun `given a re-armed verdict when invoked then it is persisted with no run and no outcome`() = runTest {
        val captured = slot<TriggerEvaluation>()

        useCase(
            triggerId = "trig-1",
            source = TriggerEvaluationSource.CHARGING_SWEEP,
            verdict = TriggerEvaluationVerdict.ReArmed,
            evaluatedAt = 3_000L,
            id = "eval-3",
        )

        coVerify(exactly = 1) { journal.recordEvaluation(capture(captured)) }
        assertNull(captured.captured.runId)
        assertNull(captured.captured.outcome)
        assertEquals(TriggerEvaluationVerdict.ReArmed, captured.captured.verdict)
    }
}
