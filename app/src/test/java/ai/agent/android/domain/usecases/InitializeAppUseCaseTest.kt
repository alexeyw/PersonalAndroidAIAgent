package ai.agent.android.domain.usecases

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
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

    @Test
    fun `invoke seeds a pipeline that passes PipelineGraph validate with zero errors`() = runTest {
        // Given — first launch fires the seed-default-pipeline branch.
        every { settingsRepository.isFirstLaunch } returns flowOf(true)
        val savedPipeline = slot<PipelineGraph>()

        // When
        useCase()

        // Then — the seeded pipeline is structurally valid (exactly one INPUT /
        // OUTPUT, no isolated nodes, no cycles), so the first-launch user lands
        // on a graph that opens in the editor and is runnable end-to-end.
        coVerify { pipelineRepository.savePipeline(capture(savedPipeline)) }
        assertEquals(emptyList<Any>(), savedPipeline.captured.validate())
    }

    @Test
    fun `invoke saves pipeline whose nodes use recommended contextConfig per type`() = runTest {
        // Given — first launch fires the seed-default-pipeline branch.
        every { settingsRepository.isFirstLaunch } returns flowOf(true)
        val savedPipeline = slot<PipelineGraph>()
        coVerify(exactly = 0) { pipelineRepository.savePipeline(any()) }

        // When
        useCase()

        // Then — capture the pipeline written into the repository and assert
        // every node's contextConfig matches NodeContextConfig.defaultForType.
        coVerify { pipelineRepository.savePipeline(capture(savedPipeline)) }
        val pipeline = savedPipeline.captured
        assert(pipeline.nodes.isNotEmpty()) { "Default pipeline must have at least one node" }
        pipeline.nodes.forEach { node ->
            assertEquals(
                "Default pipeline node '${node.label}' (${node.type}) must use defaultForType()",
                NodeContextConfig.defaultForType(node.type),
                node.contextConfig,
            )
        }
    }
}
