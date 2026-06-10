package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.services.MemorySearchStatsTracker
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClearAllMemoryUseCaseTest {

    @Test
    fun `invoke wipes the repository and resets the search stats`() = runTest {
        val repo = mockk<MemoryRepository>(relaxed = true)
        val tracker = mockk<MemorySearchStatsTracker>(relaxed = true)
        val useCase = ClearAllMemoryUseCase(repo, tracker)
        useCase()
        coVerify { repo.deleteAllMemories() }
        verify { tracker.reset() }
    }
}
