package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.PromptTemplateDao
import ai.agent.android.data.local.models.PromptTemplateEntity
import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.repositories.PromptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of [PromptRepository] using Room database.
 *
 * @param dao The DAO for accessing prompt templates in the database.
 */
class PromptRepositoryImpl @Inject constructor(private val dao: PromptTemplateDao) : PromptRepository {

    override fun getAllPrompts(): Flow<List<PromptTemplate>> = dao.getAllPrompts().map { entities ->
        entities.map { it.toDomainModel() }
    }

    override suspend fun savePrompt(prompt: PromptTemplate) {
        dao.insertPrompt(prompt.toEntity())
    }

    override suspend fun deletePrompt(id: Long) {
        dao.deletePrompt(id)
    }

    override suspend fun getPromptsCount(): Int = dao.getPromptsCount()

    private fun PromptTemplateEntity.toDomainModel(): PromptTemplate = PromptTemplate(
        id = id,
        name = name,
        text = text,
        category = category,
    )

    private fun PromptTemplate.toEntity(): PromptTemplateEntity = PromptTemplateEntity(
        id = id,
        name = name,
        text = text,
        category = category,
    )
}
