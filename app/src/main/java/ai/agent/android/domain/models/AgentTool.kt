package ai.agent.android.domain.models

/**
 * Where a discovered [AgentTool] came from. Informational provenance used by
 * UI surfaces to decide what to show — the agent's execution / routing paths
 * are source-agnostic and do not read this field.
 */
enum class ToolSource {
    /** A first-party tool hand-written in the app (e.g. `get_system_time`). */
    BUILT_IN,

    /** A tool discovered on-device via the AppFunctions framework. */
    APP_FUNCTION,

    /** A tool advertised by a connected MCP server. */
    MCP,
}

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
 * @property source Provenance of the tool ([ToolSource.BUILT_IN] by default).
 * UI surfaces use it to filter what they list (e.g. the Tools screen hides
 * [ToolSource.APP_FUNCTION] tools); execution is source-agnostic.
 */
data class AgentTool(
    val name: String,
    val description: String,
    val parameters: String,
    val risk: ToolRisk? = null,
    val source: ToolSource = ToolSource.BUILT_IN,
)
