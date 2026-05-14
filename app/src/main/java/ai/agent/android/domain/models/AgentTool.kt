package ai.agent.android.domain.models

/**
 * Represents a tool that the AI agent can use.
 *
 * @property name The unique name of the tool.
 * @property description A human-readable description of what the tool does.
 * @property parameters Schema of the parameters the tool accepts (e.g. JSON schema).
 * @property risk Optional risk classification of the tool. `null` means
 * "source-defined default" — the canonical resolution path is
 * `ToolRepository.getRisk(name)`, which merges built-in defaults, per-AppFunction
 * user overrides and the MCP blanket policy. The field is informational on the
 * model itself; the HITL gate must never read it directly and must always go
 * through the repository, so that user overrides take precedence over whatever
 * the discovery source declared.
 */
data class AgentTool(val name: String, val description: String, val parameters: String, val risk: ToolRisk? = null)
