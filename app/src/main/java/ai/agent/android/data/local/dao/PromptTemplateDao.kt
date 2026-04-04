package ai.agent.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ai.agent.android.data.local.models.PromptTemplateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for prompt templates.
 */
@Dao
interface PromptTemplateDao {
    /**
     * Retrieves all prompt templates as a flow.
     *
     * @return A flow of all [PromptTemplateEntity] records.
     */
    @Query("SELECT * FROM prompt_templates ORDER BY category, name ASC")
    fun getAllPrompts(): Flow<List<PromptTemplateEntity>>

    /**
     * Inserts a new prompt template or replaces an existing one with the same ID.
     *
     * @param prompt The prompt template to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: PromptTemplateEntity)

    /**
     * Deletes a prompt template by its ID.
     *
     * @param id The ID of the prompt to delete.
     */
    @Query("DELETE FROM prompt_templates WHERE id = :id")
    suspend fun deletePrompt(id: Long)
    
    /**
     * Checks if any prompts exist in the database.
     * 
     * @return Number of prompts.
     */
    @Query("SELECT COUNT(id) FROM prompt_templates")
    suspend fun getPromptsCount(): Int
}
