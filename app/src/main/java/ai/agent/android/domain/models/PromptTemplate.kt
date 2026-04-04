package ai.agent.android.domain.models

/**
 * Predefined categories for Prompt Templates.
 */
object PromptCategory {
    const val DEFAULT = "Default"
    const val SYSTEM = "System"
    const val TASK = "Task"
    const val CHAT = "Chat"
    const val CUSTOM = "Custom"
    
    val all = listOf(DEFAULT, SYSTEM, TASK, CHAT, CUSTOM)
}

/**
 * Domain model representing a prompt template.
 *
 * @property id The unique identifier for the prompt.
 * @property name The display name of the prompt template.
 * @property text The actual prompt content.
 * @property category An optional category or tag for grouping prompts.
 */
data class PromptTemplate(
    val id: Long = 0,
    val name: String,
    val text: String,
    val category: String? = PromptCategory.CUSTOM
)
