package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.models.TriggerEvaluationVerdict
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ObserveTriggerJournalUseCase]: it forwards the reactive journal
 * stream for the requested trigger.
 */
class ObserveTriggerJournalUseCaseTest {

    private val journal = mockk<TriggerJournalRepository>()
    private val useCase = ObserveTriggerJournalUseCase(journal)

    @Test
    fun `given a trigger id when invoked then emits the repository stream for that trigger`() = runTest {
        val evaluations = listOf(
            TriggerEvaluation(
                id = "eval-1",
                triggerId = "trig-1",
                evaluatedAt = 10L,
                source = TriggerEvaluationSource.POLL,
                verdict = TriggerEvaluationVerdict.Fired,
                runId = "run-1",
            ),
        )
        every { journal.observeByTrigger("trig-1") } returns flowOf(evaluations)

        val emitted = useCase("trig-1").first()

        assertEquals(evaluations, emitted)
    }
}
