package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.MemoryRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClearAllMemoryUseCaseTest {

    @Test
    fun `invoke forwards to repository`() = runTest {
        val repo = mockk<MemoryRepository>(relaxed = true)
        val useCase = ClearAllMemoryUseCase(repo)
        useCase()
        coVerify { repo.deleteAllMemories() }
    }
}
