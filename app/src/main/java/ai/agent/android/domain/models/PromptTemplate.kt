package ai.agent.android.domain.models

/**
 * Domain model representing a prompt template.
 *
 * @property id The unique identifier for the prompt.
 * @property name The display name of the prompt template.
 * @property text The actual prompt content.
 * @property category The category corresponding to a NodeType name.
 */
data class PromptTemplate(val id: Long = 0, val name: String, val text: String, val category: String)
