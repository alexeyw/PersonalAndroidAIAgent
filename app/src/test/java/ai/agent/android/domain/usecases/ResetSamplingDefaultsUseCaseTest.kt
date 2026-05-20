package ai.agent.android.domain.usecases

import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResetSamplingDefaultsUseCaseTest {

    @Test
    fun `invoke forwards to repository`() = runTest {
        val repo = mockk<SettingsRepository>(relaxed = true)
        val useCase = ResetSamplingDefaultsUseCase(repo)
        useCase()
        coVerify { repo.resetSamplingDefaults() }
    }
}
