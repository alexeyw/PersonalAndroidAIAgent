package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.repositories.PromptRepository
import kotlinx.coroutines.flow.Flow
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
     * 
     * @return A Flow emitting a list of [PromptTemplate] objects.
     */
    operator fun invoke(): Flow<List<PromptTemplate>> {
        return repository.getAllPrompts()
    }
}
