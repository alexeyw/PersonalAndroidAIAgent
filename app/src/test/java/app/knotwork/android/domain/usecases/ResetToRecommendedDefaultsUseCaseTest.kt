package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResetToRecommendedDefaultsUseCaseTest {

    @Test
    fun `invoke forwards to repository`() = runTest {
        val repo = mockk<SettingsRepository>(relaxed = true)
        val useCase = ResetToRecommendedDefaultsUseCase(repo)
        useCase()
        coVerify { repo.resetToRecommendedDefaults() }
    }
}
