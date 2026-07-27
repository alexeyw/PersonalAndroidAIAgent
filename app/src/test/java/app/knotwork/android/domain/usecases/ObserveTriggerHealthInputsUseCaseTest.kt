package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerHealthInputs
import app.knotwork.android.domain.models.TriggerRunOutcome
import app.knotwork.android.domain.repositories.TriggerJournalRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ObserveTriggerHealthInputsUseCase]: it forwards the reactive
 * per-trigger health-inputs stream from the journal repository.
 */
class ObserveTriggerHealthInputsUseCaseTest {

    private val journal = mockk<TriggerJournalRepository>()
    private val useCase = ObserveTriggerHealthInputsUseCase(journal)

    @Test
    fun `given health inputs when invoked then emits the repository stream`() = runTest {
        val inputs = mapOf(
            "trig-1" to TriggerHealthInputs(latestEvaluatedAt = 100L, latestFiredOutcome = TriggerRunOutcome.Success),
            "trig-2" to TriggerHealthInputs(latestEvaluatedAt = 200L),
        )
        every { journal.observeHealthInputs() } returns flowOf(inputs)

        val emitted = useCase().first()

        assertEquals(inputs, emitted)
    }
}
