package app.knotwork.android.data.repositories

import androidx.annotation.VisibleForTesting
import app.knotwork.android.data.mcp.McpClient
import app.knotwork.android.data.mcp.McpClientFactory
import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.McpConnectionStatus
import app.knotwork.android.domain.models.McpServerConfig
import app.knotwork.android.domain.models.McpTool
import app.knotwork.android.domain.repositories.McpServerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [McpServerRepository] implementation backed by per-server [McpClient]
 * instances spun up through the injected [McpClientFactory].
 *
 * The class is `@Singleton` so the same set of caches, status flows, and live
 * clients is reused across every ViewModel observer of the Tools surface. Per-URL
 * locking via [Mutex] collapses concurrent `fetchToolList` calls into a single
 * round-trip (e.g. two collectors subscribing at once on a cold start would
 * otherwise both connect — wasteful and racy for status emissions).
 */
@Singleton
class McpServerRepositoryImpl @Inject constructor(private val clientFactory: McpClientFactory) : McpServerRepository {

    /**
     * A pooled connection plus the [McpServerConfig] it was established with.
     * The config is the equality unit that decides whether a cached connection
     * is still valid or has to be torn down and rebuilt.
     *
     * @property client live client for the server.
     * @property config configuration this connection was opened with.
     */
    private data class PooledClient(val client: McpClient, val config: McpServerConfig)

    private val clients = ConcurrentHashMap<String, PooledClient>()
    private val statusFlows = ConcurrentHashMap<String, MutableStateFlow<McpConnectionStatus>>()
    private val caches = ConcurrentHashMap<String, CachedToolList>()
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    /**
     * Time source. Overridable from unit tests via a direct assignment so that
     * the TTL behaviour can be exercised without sleeping. Production code
     * leaves the field at its [System.currentTimeMillis] default.
     */
    @VisibleForTesting
    internal var clockMs: () -> Long = { System.currentTimeMillis() }

    override suspend fun fetchToolList(config: McpServerConfig, forceRefresh: Boolean): Result<List<McpTool>> {
        val serverUrl = config.url
        val mutex = mutexes.getOrPut(serverUrl) { Mutex() }
        val flow = statusFlows.getOrPut(serverUrl) { MutableStateFlow(McpConnectionStatus.Connecting) }
        return mutex.withLock {
            val now = clockMs()
            if (!forceRefresh) {
                val cached = caches[serverUrl]
                if (cached != null && now - cached.fetchedAtMs <= McpServerRepository.TOOL_LIST_TTL_MS) {
                    // A previously-failed refresh may have left the status flow in `Error`.
                    // Serving cached tools while still telling the UI we're broken would
                    // surface a stale red pill on a perfectly usable server — so reconcile
                    // the status to `Connected` whenever we successfully return cached data.
                    flow.value = McpConnectionStatus.Connected
                    return@withLock Result.success(cached.tools)
                }
            }
            val statusBeforeFetch = flow.value
            flow.value = McpConnectionStatus.Connecting

            // try/catch instead of runCatching: the block suspends
            // (connect / getTools), and runCatching would trap the
            // CancellationException that must propagate for cooperative
            // cancellation. `Throwable` keeps runCatching's catch surface.
            try {
                val client = connectedClient(serverUrl = serverUrl, config = config)
                val tools = client.getTools().map { agentTool -> agentTool.toMcpTool(serverUrl) }
                caches[serverUrl] = CachedToolList(tools = tools, fetchedAtMs = clockMs())
                flow.value = McpConnectionStatus.Connected
                Result.success(tools)
            } catch (e: CancellationException) {
                // Success and failure both resolve the status; cancellation used
                // to resolve nothing, so a fetch abandoned mid-flight (the caller's
                // scope going away — a settings edit that renavigates, a screen
                // leaving) pinned the row on "Connecting…" until the user hit
                // Refresh by hand (phase-40 finding F8).
                //
                // Restoring the previous value is not enough: a flow starts life
                // at `Connecting`, so the very first fetch has nothing better to
                // fall back to. An abandoned attempt genuinely leaves the server
                // in an unknown state, and the honest rendering of that is "not
                // connected, try again" rather than a spinner that never stops.
                // Assignment does not suspend, so this is safe while cancelling.
                flow.value = when {
                    statusBeforeFetch != McpConnectionStatus.Connecting -> statusBeforeFetch
                    caches[serverUrl] != null -> McpConnectionStatus.Connected
                    else -> McpConnectionStatus.Error(reason = INTERRUPTED_REASON)
                }
                throw e
            } catch (e: Throwable) {
                Timber.e(e, "MCP tools/list failed for %s", serverUrl)
                // Drop the pooled entry so the next attempt reconnects instead of
                // reusing a connection that has already proven unusable.
                clients.remove(serverUrl)
                flow.value = McpConnectionStatus.Error(reason = e.localizedMessage ?: e.javaClass.simpleName)
                Result.failure(e)
            }
        }
    }

    /**
     * Returns a connected client for [serverUrl], connecting only when there is
     * no usable pooled entry — i.e. on first use, after a failure dropped the
     * entry, or when [config] differs from the one the pooled connection was
     * established with (changed auth / transport / headers must actually apply).
     *
     * Reconnecting on **every** call, as this used to, opened a fresh MCP
     * session per manual Refresh: the server kept the abandoned ones (nothing
     * terminated them) and the in-place transport swap could break a tool call
     * running concurrently on the same client. Both were observed in the
     * phase-40 directed MCP test (finding F3).
     *
     * Callers hold the per-URL [Mutex], so the check-then-connect sequence here
     * cannot interleave with another fetch for the same server.
     *
     * @param serverUrl pool key and connection identity.
     * @param config configuration to connect with, compared against the config
     *   the pooled entry was built from.
     * @return a connected [McpClient] ready for `getTools`.
     */
    private suspend fun connectedClient(serverUrl: String, config: McpServerConfig): McpClient {
        val pooled = clients[serverUrl]
        if (pooled != null && pooled.config == config) {
            return pooled.client
        }
        if (pooled != null) {
            try {
                pooled.client.disconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.w(e, "MCP disconnect of stale config for %s failed; reconnecting anyway", serverUrl)
            }
            clients.remove(serverUrl)
        }
        val client = clientFactory.create()
        client.connect(config)
        clients[serverUrl] = PooledClient(client = client, config = config)
        return client
    }

    override fun observeConnectionStatus(serverUrl: String): Flow<McpConnectionStatus> =
        statusFlows.getOrPut(serverUrl) {
            MutableStateFlow(McpConnectionStatus.Connecting)
        }.asStateFlow()

    override suspend fun disconnect(serverUrl: String) {
        // Re-use whatever Mutex an in-flight fetchToolList may already hold so we
        // serialise with it instead of racing past it. The Mutex entry is intentionally
        // NOT removed from the map: removing it while another coroutine is suspended
        // waiting on it would silently drop the lock contract, and a follow-up fetch
        // would `getOrPut` a fresh Mutex that runs in parallel with the in-flight
        // disconnect.
        val mutex = mutexes.getOrPut(serverUrl) { Mutex() }
        mutex.withLock {
            val pooled = clients.remove(serverUrl)
            try {
                pooled?.client?.disconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.w(e, "MCP disconnect failed for %s", serverUrl)
            }
            caches.remove(serverUrl)
            // Status flow entry stays in the map so any active observer keeps a
            // live handle. Removing it here would orphan the observer's
            // collection — a follow-up `fetchToolList` would `getOrPut` a brand-
            // new MutableStateFlow that the in-flight collector knows nothing
            // about, leaving the UI pinned to whatever value the orphaned flow
            // last held (typically the previous `Error`). Reset to `Connecting`
            // so the UI immediately reflects the "no live session" state until
            // the next fetch resolves it.
            statusFlows[serverUrl]?.value = McpConnectionStatus.Connecting
        }
    }

    /**
     * Maps a protocol-neutral [AgentTool] (as returned by `McpClient.getTools`)
     * to the MCP-affiliated [McpTool] consumed by the Tools surface.
     */
    private fun AgentTool.toMcpTool(serverUrl: String): McpTool = McpTool(
        id = mcpToolId(serverUrl = serverUrl, toolName = name),
        serverUrl = serverUrl,
        name = name,
        description = description,
        inputSchemaJson = parameters,
        risk = risk,
    )

    private data class CachedToolList(val tools: List<McpTool>, val fetchedAtMs: Long)

    /** Stable id builder + format constants shared with `ToolDetailScreen`. */
    companion object {
        /** Recognises [mcpToolId] outputs in route parameters and other callers. */
        const val MCP_ID_PREFIX: String = "mcp:"

        /** Shown when a tool-list fetch was abandoned before it could resolve. */
        private const val INTERRUPTED_REASON: String = "Connection attempt was interrupted"

        /** Number of hex chars taken from the SHA-256 digest for the id prefix. */
        private const val ID_HASH_HEX_LEN: Int = 8

        /** One hex byte is two characters wide; padding helper. */
        private const val HEX_BYTE_WIDTH: Int = 2

        private const val UNSIGNED_BYTE_MASK: Int = 0xFF
        private const val HEX_RADIX: Int = 16

        /**
         * Builds the stable route-safe id used as the tool's argument when
         * navigating to `ToolDetailScreen`. The SHA-256 prefix isolates the
         * server URL from path-segment encoding concerns and keeps the id
         * deterministic across process restarts.
         */
        fun mcpToolId(serverUrl: String, toolName: String): String = "$MCP_ID_PREFIX${sha8(serverUrl)}:$toolName"

        private fun sha8(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return buildString(capacity = ID_HASH_HEX_LEN) {
                val bytesNeeded = ID_HASH_HEX_LEN / HEX_BYTE_WIDTH
                for (i in 0 until bytesNeeded) {
                    val byte = bytes[i].toInt() and UNSIGNED_BYTE_MASK
                    append(byte.toString(radix = HEX_RADIX).padStart(length = HEX_BYTE_WIDTH, padChar = '0'))
                }
            }
        }
    }
}
