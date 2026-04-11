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
 * and creates a default complex execution pipeline.
 */
class InitializeAppUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pipelineRepository: PipelineRepository
) {
    /**
     * Executes the initialization logic.
     */
    suspend operator fun invoke() {
        val isFirstLaunch = settingsRepository.isFirstLaunch.first()
        
        if (isFirstLaunch) {
            // Save default prompts to settings so they can be modified later by the user
            settingsRepository.setSystemPromptPrefix(DefaultPrompts.SYSTEM_PROMPT_PREFIX)
            settingsRepository.setToolUsageInstruction(DefaultPrompts.TOOL_USAGE_INSTRUCTION)
            
            // Generate and save the default complex pipeline
            val defaultPipeline = DefaultPipelineFactory.create("Default System Pipeline")
            pipelineRepository.savePipeline(defaultPipeline)

            // Mark first launch as complete
            settingsRepository.setFirstLaunch(false)
        }
    }
}
