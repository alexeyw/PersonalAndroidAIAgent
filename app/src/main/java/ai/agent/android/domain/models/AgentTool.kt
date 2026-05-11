package ai.agent.android.domain.models

/**
 * Represents a tool that the AI agent can use.
 *
 * @property name The unique name of the tool.
 * @property description A human-readable description of what the tool does.
 * @property parameters Schema of the parameters the tool accepts (e.g. JSON schema).
 */
data class AgentTool(val name: String, val description: String, val parameters: String)
