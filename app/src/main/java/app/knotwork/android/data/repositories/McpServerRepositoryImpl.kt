package app.knotwork.android.data.repositories

import androidx.annotation.VisibleForTesting
import app.knotwork.android.data.mcp.McpClient
import app.knotwork.android.data.mcp.McpClientFactory
import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.McpConnectionStatus
import app.knotwork.android.domain.models.McpServerConfig
import app.knotwork.android.domain.models.McpTool
import app.knotwork.android.domain.repositories.McpServerRepository
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

    private val clients = ConcurrentHashMap<String, McpClient>()
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
            flow.value = McpConnectionStatus.Connecting

            runCatching {
                val client = clients.getOrPut(serverUrl) { clientFactory.create() }
                client.connect(config)
                client.getTools().map { agentTool -> agentTool.toMcpTool(serverUrl) }
            }.onSuccess { tools ->
                caches[serverUrl] = CachedToolList(tools = tools, fetchedAtMs = clockMs())
                flow.value = McpConnectionStatus.Connected
            }.onFailure { error ->
                Timber.e(error, "MCP tools/list failed for %s", serverUrl)
                flow.value = McpConnectionStatus.Error(reason = error.localizedMessage ?: error.javaClass.simpleName)
            }
        }
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
            val client = clients.remove(serverUrl)
            runCatching { client?.disconnect() }
                .onFailure { Timber.w(it, "MCP disconnect failed for %s", serverUrl) }
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
