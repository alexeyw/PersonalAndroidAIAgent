package app.knotwork.android.domain.models

/**
 * Connection lifecycle of an MCP server as observed by `McpServerRepository`.
 *
 * The status flow is hot: it transitions `Connecting → Connected` on a successful
 * `tools/list` fetch and `Connecting → Error` when the handshake or the JSON-RPC
 * call throws. UI surfaces (`ToolsScreen`) render the current value as a status
 * pill in the trailing slot of the server row, including the transition animation.
 */
sealed interface McpConnectionStatus {
    /** Initial state — emitted before the first fetch attempt and on force-refresh. */
    data object Connecting : McpConnectionStatus

    /** Steady state after a successful `tools/list` response. */
    data object Connected : McpConnectionStatus

    /**
     * Terminal state for the current attempt. The repository surfaces the
     * exception's localized message in [reason] so the UI can render it
     * without re-throwing.
     */
    data class Error(val reason: String) : McpConnectionStatus
}
