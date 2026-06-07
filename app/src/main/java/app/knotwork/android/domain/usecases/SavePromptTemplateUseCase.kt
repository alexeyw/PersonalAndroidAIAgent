package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PromptTemplate
import app.knotwork.android.domain.repositories.PromptRepository
import javax.inject.Inject

/**
 * UseCase for saving a prompt template.
 *
 * @property repository The repository for managing prompt templates.
 */
class SavePromptTemplateUseCase @Inject constructor(private val repository: PromptRepository) {
    /**
     * Invokes the use case to save a prompt template.
     *
     * @param prompt The prompt template to save.
     */
    suspend operator fun invoke(prompt: PromptTemplate) {
        repository.savePrompt(prompt)
    }
}
