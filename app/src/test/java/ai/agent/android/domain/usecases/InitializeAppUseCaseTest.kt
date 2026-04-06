package ai.agent.android.domain.usecases

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InitializeAppUseCaseTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val pipelineRepository: PipelineRepository = mockk(relaxed = true)
    private val useCase = InitializeAppUseCase(settingsRepository, pipelineRepository)

    @Test
    fun `invoke should set default prompts and save pipeline if first launch`() = runTest {
        every { settingsRepository.isFirstLaunch } returns flowOf(true)

        useCase()

        coVerify { settingsRepository.setSystemPromptPrefix(DefaultPrompts.SYSTEM_PROMPT_PREFIX) }
        coVerify { settingsRepository.setToolUsageInstruction(DefaultPrompts.TOOL_USAGE_INSTRUCTION) }
        coVerify { pipelineRepository.savePipeline(any()) }
        coVerify { settingsRepository.setFirstLaunch(false) }
    }

    @Test
    fun `invoke should do nothing if not first launch`() = runTest {
        every { settingsRepository.isFirstLaunch } returns flowOf(false)

        useCase()

        coVerify(exactly = 0) { settingsRepository.setSystemPromptPrefix(any()) }
        coVerify(exactly = 0) { settingsRepository.setToolUsageInstruction(any()) }
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
        coVerify(exactly = 0) { settingsRepository.setFirstLaunch(any()) }
    }
}
