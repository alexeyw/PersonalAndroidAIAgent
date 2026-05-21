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
 * @property auth typed authentication scheme. The runtime composes the
 * matching request header at connect time; [McpAuth.None] keeps the
 * request anonymous.
 * @property headers extra request headers (key → value). Use this only
 * for non-auth metadata (`X-Trace-Id`, custom user agents, etc.). The
 * common Authorization / API-key flows belong in [auth] so the form can
 * round-trip them as typed fields on edit.
 */
data class McpServerConfig(
    val url: String,
    val name: String? = null,
    val transport: McpTransport = McpTransport.SSE,
    val auth: McpAuth = McpAuth.None,
    val headers: Map<String, String> = emptyMap(),
) {
    /** Display label used by the UI: explicit [name] if set, URL otherwise. */
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: url
}
