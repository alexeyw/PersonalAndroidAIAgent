package app.knotwork.android.domain.models

/**
 * One tool advertised by a remote MCP server.
 *
 * Distinct from [AgentTool] (the protocol-neutral registry entry used by
 * `ToolRepository` and the agent loop) — [McpTool] keeps the per-server
 * affiliation needed to render the Tools surface and to route a
 * `ToolDetailScreen` argument back to the originating server.
 *
 * @property id stable, route-safe identifier in the form
 * `mcp:<sha8(serverUrl)>:<toolName>`. The hash prefix isolates the
 * server URL from `{toolId}` path-segment encoding concerns (`/`, `:`,
 * `?` characters in the URL would otherwise leak into Navigation
 * Compose's argument parsing).
 * @property serverUrl raw URL of the originating MCP server — surfaced
 * verbatim in the detail screen header as the "server affiliation".
 * @property name tool name as advertised by the server (key into
 * `McpClient.executeTool`).
 * @property description human-readable description from the server's
 * `tools/list` response.
 * @property inputSchemaJson JSON-Schema string for the tool's input
 * parameters, exactly as produced by `McpClient.getTools()`.
 * @property risk per-tool risk tier, when the server's MCP manifest
 * supplies one. `null` means "fall back to the blanket MCP policy"
 * — see `ToolRepository.getRisk`, which currently treats unannotated
 * MCP tools as [ToolRisk.SENSITIVE].
 */
data class McpTool(
    val id: String,
    val serverUrl: String,
    val name: String,
    val description: String,
    val inputSchemaJson: String,
    val risk: ToolRisk? = null,
)
