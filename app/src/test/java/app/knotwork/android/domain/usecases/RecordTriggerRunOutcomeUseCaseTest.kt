package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerRunOutcome
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [RecordTriggerRunOutcomeUseCase]: it forwards the run outcome to
 * the journal keyed by run id.
 */
class RecordTriggerRunOutcomeUseCaseTest {

    private val journal = mockk<TriggerJournalRepository>(relaxUnitFun = true)
    private val useCase = RecordTriggerRunOutcomeUseCase(journal)

    @Test
    fun `given a success outcome when invoked then it is recorded against the run id`() = runTest {
        useCase("run-1", TriggerRunOutcome.Success)

        coVerify(exactly = 1) { journal.recordRunOutcome("run-1", TriggerRunOutcome.Success) }
    }

    @Test
    fun `given a failure outcome when invoked then it is recorded verbatim`() = runTest {
        val outcome = TriggerRunOutcome.Failure("boom")

        useCase("run-2", outcome)

        coVerify(exactly = 1) { journal.recordRunOutcome("run-2", outcome) }
    }
}
