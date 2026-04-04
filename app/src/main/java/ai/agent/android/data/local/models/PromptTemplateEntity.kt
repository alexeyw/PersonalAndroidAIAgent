package ai.agent.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a prompt template.
 * 
 * @property id The unique identifier for the prompt, auto-generated.
 * @property name The display name of the prompt template.
 * @property text The actual prompt content.
 * @property category An optional category or tag for grouping prompts.
 */
@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val text: String,
    val category: String? = null
)
