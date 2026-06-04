package ai.agent.android.domain.usecases

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.engine.DefaultPipelineFactory
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case executed when the application is launched.
 * It checks if this is the first launch, and if so, initializes default settings,
 * such as saving the default system prompts to the settings repository,
 * and materialises the bundled showcase pipeline as the application default.
 */
class InitializeAppUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pipelineRepository: PipelineRepository,
    private val loadPipelineFromPresetUseCase: LoadPipelineFromPresetUseCase,
) {
    /**
     * Executes the initialization logic.
     *
     * On the very first launch the seed pipeline is materialised from the
     * bundled [SHOWCASE_PRESET_ID] preset via [LoadPipelineFromPresetUseCase]
     * (fresh node / connection ids, validated, persisted). This gives the new
     * user a pipeline that exercises the full breadth of the agent — intent
     * routing, a local/cloud fork, task decomposition, a tool call, a
     * human-in-the-loop clarification, summarisation and evaluation — instead
     * of a minimal stub. If the preset cannot be loaded (e.g. a corrupt or
     * missing asset) the seed falls back to the code-level
     * [DefaultPipelineFactory] so first launch never leaves the library empty.
     */
    suspend operator fun invoke() {
        val isFirstLaunch = settingsRepository.isFirstLaunch.first()

        if (isFirstLaunch) {
            // Save default prompts to settings so they can be modified later by the user
            settingsRepository.setSystemPromptPrefix(DefaultPrompts.SYSTEM_PROMPT_PREFIX)
            settingsRepository.setToolUsageInstruction(DefaultPrompts.TOOL_USAGE_INSTRUCTION)

            // Materialise the bundled showcase pipeline as the seed; fall back
            // to the code-level factory if the preset asset is unavailable.
            val seededPipelineId = loadPipelineFromPresetUseCase(SHOWCASE_PRESET_ID)
                .getOrNull()
                ?: seedFallbackPipeline()

            // Mark the seeded pipeline as the application default so the
            // chat surfaces ("Use default pipeline (…)" label, TopAppBar
            // subtitle) show a concrete name from the very first launch
            // instead of relying on the implicit "first in library" fallback.
            settingsRepository.setDefaultPipelineId(seededPipelineId)

            // Mark first launch as complete
            settingsRepository.setFirstLaunch(false)
        }
    }

    /**
     * Builds and persists the code-level default pipeline, returning its id.
     * Used only when the bundled showcase preset fails to materialise.
     */
    private suspend fun seedFallbackPipeline(): String {
        val defaultPipeline = DefaultPipelineFactory.create("Default System Pipeline")
        pipelineRepository.savePipeline(defaultPipeline)
        return defaultPipeline.id
    }

    private companion object {
        /**
         * Stable id of the bundled showcase preset
         * (`assets/presets/pipelines/showcase_full_agent.json`) materialised as
         * the first-launch seed pipeline.
         */
        const val SHOWCASE_PRESET_ID = "showcase_full_agent"
    }
}
