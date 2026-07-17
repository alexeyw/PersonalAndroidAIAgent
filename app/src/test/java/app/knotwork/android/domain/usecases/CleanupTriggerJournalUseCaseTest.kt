package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.TriggerJournalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [CleanupTriggerJournalUseCase]: it derives the age cutoff from
 * the configured window and delegates the bounded pass to the repository.
 */
class CleanupTriggerJournalUseCaseTest {

    private val journal = mockk<TriggerJournalRepository>()
    private val useCase = CleanupTriggerJournalUseCase(journal)

    @Test
    fun `given a now when invoked then applies retention with a window-aged cutoff and the record cap`() = runTest {
        val now = 1_000_000_000_000L
        val expectedCutoff = now - CleanupTriggerJournalUseCase.RETENTION_WINDOW_DAYS * MILLIS_PER_DAY
        coEvery { journal.applyRetention(expectedCutoff, CleanupTriggerJournalUseCase.MAX_RECORDS) } returns 5

        val deleted = useCase(now)

        assertEquals(5, deleted)
        coVerify(exactly = 1) {
            journal.applyRetention(expectedCutoff, CleanupTriggerJournalUseCase.MAX_RECORDS)
        }
    }

    private companion object {
        const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1_000L
    }
}
