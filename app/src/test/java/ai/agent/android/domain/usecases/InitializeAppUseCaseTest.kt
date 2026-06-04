package ai.agent.android.domain.usecases

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InitializeAppUseCaseTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val pipelineRepository: PipelineRepository = mockk(relaxed = true)
    private val loadPipelineFromPresetUseCase: LoadPipelineFromPresetUseCase = mockk(relaxed = true)
    private val useCase = InitializeAppUseCase(
        settingsRepository,
        pipelineRepository,
        loadPipelineFromPresetUseCase,
    )

    @Test
    fun `invoke seeds from showcase preset and sets it as default on first launch`() = runTest {
        // Given — first launch, and the bundled showcase preset materialises cleanly.
        every { settingsRepository.isFirstLaunch } returns flowOf(true)
        coEvery { loadPipelineFromPresetUseCase(SHOWCASE_PRESET_ID) } returns Result.success(SEEDED_ID)

        // When
        useCase()

        // Then — default prompts are persisted, the seed is materialised from
        // the showcase preset (not the code-level factory), the returned id
        // becomes the application default, and first launch is cleared.
        coVerify { settingsRepository.setSystemPromptPrefix(DefaultPrompts.SYSTEM_PROMPT_PREFIX) }
        coVerify { settingsRepository.setToolUsageInstruction(DefaultPrompts.TOOL_USAGE_INSTRUCTION) }
        coVerify { loadPipelineFromPresetUseCase(SHOWCASE_PRESET_ID) }
        coVerify { settingsRepository.setDefaultPipelineId(SEEDED_ID) }
        coVerify { settingsRepository.setFirstLaunch(false) }
        // The factory fallback must not run when the preset loads.
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
    }

    @Test
    fun `invoke falls back to factory pipeline when showcase preset fails`() = runTest {
        // Given — first launch, but the showcase preset cannot be materialised.
        every { settingsRepository.isFirstLaunch } returns flowOf(true)
        coEvery { loadPipelineFromPresetUseCase(SHOWCASE_PRESET_ID) } returns
            Result.failure(IllegalStateException("missing asset"))
        val savedPipeline = slot<PipelineGraph>()

        // When
        useCase()

        // Then — the code-level factory pipeline is persisted, is structurally
        // valid, and its id becomes the application default so the user still
        // lands on a runnable pipeline.
        coVerify { pipelineRepository.savePipeline(capture(savedPipeline)) }
        assertEquals(emptyList<Any>(), savedPipeline.captured.validate())
        coVerify { settingsRepository.setDefaultPipelineId(savedPipeline.captured.id) }
        coVerify { settingsRepository.setFirstLaunch(false) }
    }

    @Test
    fun `invoke should do nothing if not first launch`() = runTest {
        every { settingsRepository.isFirstLaunch } returns flowOf(false)

        useCase()

        coVerify(exactly = 0) { settingsRepository.setSystemPromptPrefix(any()) }
        coVerify(exactly = 0) { settingsRepository.setToolUsageInstruction(any()) }
        coVerify(exactly = 0) { loadPipelineFromPresetUseCase(any()) }
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }
        coVerify(exactly = 0) { settingsRepository.setDefaultPipelineId(any()) }
        coVerify(exactly = 0) { settingsRepository.setFirstLaunch(any()) }
    }

    private companion object {
        const val SHOWCASE_PRESET_ID = "showcase_full_agent"
        const val SEEDED_ID = "seeded-pipeline-id"
    }
}
