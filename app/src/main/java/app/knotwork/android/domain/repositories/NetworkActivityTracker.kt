package app.knotwork.android.domain.repositories

import kotlinx.coroutines.flow.StateFlow

/**
 * Records and exposes the **outbound** network activity of the agent for
 * privacy-status surfaces (the More tab's footer pill).
 *
 * Distinct from [NetworkStateRepository], which reflects connectivity
 * (is there Wi-Fi / cellular?). This tracker answers the orthogonal
 * question "when did this app last actually *use* the network?".
 *
 * Recorded sources (call sites that invoke [recordOutbound]):
 *  - Cloud LLM streaming (`CloudLlmNodeExecutor` — every call to a
 *    provider client).
 *  - MCP server traffic (`KoogMcpClient.connect` / `getTools` /
 *    `executeTool`).
 *
 * Not recorded: local model downloads (initiated explicitly by the user
 * and surfaced on the Models screen), DNS / system network probes.
 */
interface NetworkActivityTracker {
    /**
     * Latest outbound-call timestamp in epoch milliseconds, or `null` if
     * no call has been recorded since the process started.
     */
    val lastOutboundAt: StateFlow<Long?>

    /** Mark "right now" as the moment of an outbound call. */
    fun recordOutbound()
}
