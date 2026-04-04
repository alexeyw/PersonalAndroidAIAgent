package ai.agent.android.domain.usecases

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.repositories.PromptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * UseCase for retrieving all prompt templates.
 * 
 * @property repository The repository for managing prompt templates.
 */
class GetPromptTemplatesUseCase @Inject constructor(
    private val repository: PromptRepository
) {
    /**
     * Invokes the use case to get all prompt templates.
     * Initializes default prompts if the database is empty.
     * 
     * @return A Flow emitting a list of [PromptTemplate] objects.
     */
    operator fun invoke(): Flow<List<PromptTemplate>> {
        return repository.getAllPrompts().onStart {
            initializeDefaultsIfNeeded()
        }
    }

    private suspend fun initializeDefaultsIfNeeded() {
        try {
            if (repository.getPromptsCount() == 0) {
                val defaults = listOf(
                    PromptTemplate(name = "Classifier", text = DefaultPrompts.INTENT_ROUTER_PROMPT, category = "Default"),
                    PromptTemplate(name = "Decomposer", text = DefaultPrompts.DECOMPOSITION_PROMPT, category = "Default"),
                    PromptTemplate(name = "Summarizer", text = DefaultPrompts.SUMMARY_PROMPT, category = "Default"),
                    PromptTemplate(name = "Tool Picker", text = DefaultPrompts.TOOL_USAGE_INSTRUCTION, category = "Default")
                )
                defaults.forEach { repository.savePrompt(it) }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
