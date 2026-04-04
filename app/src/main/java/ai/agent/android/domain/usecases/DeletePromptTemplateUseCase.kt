package ai.agent.android.domain.usecases

import ai.agent.android.domain.repositories.PromptRepository
import javax.inject.Inject

/**
 * UseCase for deleting a prompt template.
 * 
 * @property repository The repository for managing prompt templates.
 */
class DeletePromptTemplateUseCase @Inject constructor(
    private val repository: PromptRepository
) {
    /**
     * Invokes the use case to delete a prompt template by its ID.
     * 
     * @param id The ID of the prompt template to delete.
     */
    suspend operator fun invoke(id: Long) {
        repository.deletePrompt(id)
    }
}
