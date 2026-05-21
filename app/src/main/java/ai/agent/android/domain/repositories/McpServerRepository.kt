package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.McpConnectionStatus
import ai.agent.android.domain.models.McpTool
import kotlinx.coroutines.flow.Flow

/**
 * Per-server gateway over the raw `McpClient` transport.
 *
 * Adds three concerns on top of the protocol-level client:
 *
 *  1. **Tool list caching.** `fetchToolList` returns cached `McpTool` entries
 *     for up to [TOOL_LIST_TTL_MS] (5 minutes); `forceRefresh = true` bypasses
 *     the cache (wired to the trailing refresh icon in `McpServerRow`).
 *  2. **Per-server connection status.** Each URL has a hot `Flow<McpConnectionStatus>`
 *     that drives the status pill in the Tools screen. Transitions are emitted
 *     from inside `fetchToolList`: `Connecting → Connected` on success,
 *     `Connecting → Error` on handshake or JSON-RPC failure.
 *  3. **Lifecycle ownership.** The repository owns one `McpClient` per server
 *     URL (lazy connect, kept alive across `fetchToolList` calls); callers
 *     drop the connection through [disconnect] when a URL is removed from
 *     Settings, and the singleton's `dispose()`-equivalent is implicit (the
 *     repository is `@Singleton`).
 *
 * Implementations live in `data/repositories/McpServerRepositoryImpl`.
 */
interface McpServerRepository {

    /**
     * Connects (lazily) to [serverUrl], performs an MCP `tools/list` round-trip,
     * and returns the resulting [McpTool] entries.
     *
     * The status flow returned by [observeConnectionStatus] is updated as a
     * side-effect of this call (`Connecting → Connected` / `Connecting → Error`),
     * so callers do not need to wrap the result in their own error UI — they
     * can simply observe the flow and surface its terminal state.
     *
     * @param serverUrl exact URL persisted in `SettingsRepository.mcpServerUrls`.
     * @param forceRefresh when `true`, ignores the cached tool list (if any)
     * and re-issues the round-trip even if the previous response is within
     * the 5-minute TTL.
     * @return [Result.success] with the tool list on a clean fetch; [Result.failure]
     * wrapping the underlying exception when the handshake or the `tools/list`
     * call throws. The status flow carries the same outcome.
     */
    suspend fun fetchToolList(serverUrl: String, forceRefresh: Boolean = false): Result<List<McpTool>>

    /**
     * Hot stream of the current connection lifecycle for [serverUrl]. The first
     * collector receives the latest cached state (or `Connecting` if no fetch
     * has been attempted yet).
     */
    fun observeConnectionStatus(serverUrl: String): Flow<McpConnectionStatus>

    /**
     * Closes the underlying [ai.agent.android.data.mcp.McpClient] for [serverUrl]
     * (if any) and drops the cached tool list. No-op when the server was never
     * fetched. Called from `ToolsViewModel` when the user removes a server URL.
     */
    suspend fun disconnect(serverUrl: String)

    /** Tunables shared across every implementation of this interface. */
    companion object {
        /** Cache TTL for [fetchToolList] in milliseconds (5 minutes). */
        const val TOOL_LIST_TTL_MS: Long = 5L * 60L * 1000L
    }
}
