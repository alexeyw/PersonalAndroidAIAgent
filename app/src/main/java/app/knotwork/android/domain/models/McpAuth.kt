package app.knotwork.android.domain.models

/**
 * Authentication scheme used when talking to an MCP server.
 *
 * The runtime composes the appropriate request headers from the
 * selected variant at connect time (see `KoogMcpClient.connect`).
 * Persisted in `McpServerConfig.auth`; surfaced in the form as a
 * single-pick chip row with conditional fields underneath.
 *
 * Storage note: tokens / passwords live in the same JSON-encoded
 * `MCP_SERVERS_JSON` DataStore entry as the rest of the config. They
 * are **not** routed through the Keystore-backed encrypted store today
 * — the threat model matches the existing arbitrary-headers field.
 * Hardening this to use the encrypted store is tracked as a follow-up.
 */
sealed interface McpAuth {
    /** No authentication. */
    data object None : McpAuth

    /**
     * `Authorization: Bearer <token>`. The most common scheme for
     * MCP servers issued an opaque access token (e.g. HuggingFace,
     * personal-access-token MCP gateways).
     *
     * @property token raw token; the runtime prepends `Bearer ` itself.
     */
    data class Bearer(val token: String) : McpAuth

    /**
     * `Authorization: Basic <base64(username:password)>`. Used by
     * self-hosted gateways behind HTTP basic auth.
     *
     * @property username basic-auth username.
     * @property password basic-auth password.
     */
    data class Basic(val username: String, val password: String) : McpAuth

    /**
     * Single custom header carrying an API key — e.g.
     * `X-API-Key: <value>`. The header name is user-supplied so the
     * scheme covers vendor-specific names (`Anthropic-Api-Key`,
     * `Asana-Token`, etc.) without forcing them into a Bearer wrapper.
     *
     * @property headerName name of the header the key is sent under.
     * @property value the API key itself.
     */
    data class ApiKey(val headerName: String, val value: String) : McpAuth
}
