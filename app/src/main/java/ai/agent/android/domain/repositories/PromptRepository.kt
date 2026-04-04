package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.PromptTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing prompt templates.
 */
interface PromptRepository {
    /**
     * Retrieves all saved prompt templates.
     *
     * @return A Flow emitting lists of [PromptTemplate].
     */
    fun getAllPrompts(): Flow<List<PromptTemplate>>

    /**
     * Saves a new prompt template or updates an existing one.
     *
     * @param prompt The prompt template to save.
     */
    suspend fun savePrompt(prompt: PromptTemplate)

    /**
     * Deletes a prompt template.
     *
     * @param id The ID of the prompt template to delete.
     */
    suspend fun deletePrompt(id: Long)
}
