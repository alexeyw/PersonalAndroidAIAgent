package ai.agent.android.domain.models

/**
 * Per-server configuration for an MCP connection, persisted by
 * `SettingsRepository.mcpServers`.
 *
 * @property url base URL of the MCP server (acts as the stable identity).
 * @property name optional display label rendered as the row title; the
 * row falls back to [url] when null/blank.
 * @property transport transport protocol (see [McpTransport]). Only
 * [McpTransport.SSE] is end-to-end wired today; the field is persisted
 * so the user's pick survives future client upgrades.
 * @property headers extra request headers (key → value). The typical
 * use case is an `Authorization: Bearer ...` token for protected
 * servers, but any header is accepted. Keys are case-insensitive on
 * the wire; persistence preserves the user's casing.
 */
data class McpServerConfig(
    val url: String,
    val name: String? = null,
    val transport: McpTransport = McpTransport.SSE,
    val headers: Map<String, String> = emptyMap(),
) {
    /** Display label used by the UI: explicit [name] if set, URL otherwise. */
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: url
}
