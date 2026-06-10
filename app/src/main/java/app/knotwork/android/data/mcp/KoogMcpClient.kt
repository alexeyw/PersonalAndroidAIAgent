package app.knotwork.android.data.mcp

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.McpAuth
import app.knotwork.android.domain.models.McpServerConfig
import app.knotwork.android.domain.models.McpTransport
import app.knotwork.android.domain.repositories.NetworkActivityTracker
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpHeaders
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttpTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject

/**
 * Concrete implementation of [McpClient] using the Koog framework's MCP tools.
 * It manages the underlying Ktor HttpClient and the Koog ToolRegistry.
 */
@OptIn(ai.koog.agents.core.tools.annotations.InternalAgentToolsApi::class)
class KoogMcpClient(private val networkActivityTracker: NetworkActivityTracker? = null) : McpClient {
    private var registry: ToolRegistry? = null

    // The HTTP client is recreated on every [connect] so the same client instance can be
    // disconnected and reconnected cleanly. Holding a single client across disconnect would
    // leave it in a closed state, and a subsequent [connect] would immediately fail when the
    // underlying engine is reused.
    private var httpClient: HttpClient? = null
    private val serializer = KotlinxSerializer(Json { ignoreUnknownKeys = true })

    /**
     * Connects to the MCP server described by [config]. Branches on
     * [McpServerConfig.transport]:
     *  - [McpTransport.SSE] — classic server-sent events via Koog's
     *    `defaultSseTransport`.
     *  - [McpTransport.STREAMABLE_HTTP] — the post-2025-03-26 spec
     *    transport via the upstream MCP Kotlin SDK
     *    (`HttpClient.mcpStreamableHttpTransport`). Uses POST for
     *    outbound JSON-RPC and an SSE channel for inbound, so the SSE
     *    Ktor plugin is installed on the shared client unconditionally.
     *
     * Calling [connect] more than once on the same instance is supported
     * (e.g. reconnect or repoint scenarios): any previously-attached
     * `HttpClient` is closed before a fresh one is installed so its
     * socket pool and engine threads do not leak until process teardown.
     *
     * The freshly-created `HttpClient` is published into the [httpClient]
     * field **only after** transport construction succeeds. If transport
     * construction or `fromTransport` throws (network error, malformed
     * URL, server-side rejection), the client is closed locally and the
     * field is left in its previous state, so a leaked Ktor engine
     * cannot accumulate across failed connects.
     */
    override suspend fun connect(config: McpServerConfig) {
        // Outbound HTTP traffic is about to start — surface it to the privacy indicator
        // *before* the network handshake so the timestamp reflects intent even if the
        // call fails downstream.
        networkActivityTracker?.recordOutbound()
        withContext(Dispatchers.IO) {
            // Drop any previous client+registry pair before reattaching. Without this,
            // a second connect() on the same instance would silently leak the prior
            // HttpClient (and its underlying engine threads/sockets).
            httpClient?.close()
            httpClient = null
            registry = null

            // Compose the final header set: typed [McpAuth] becomes its
            // canonical request header, then user-supplied `config.headers`
            // are appended on top (the user wins on conflict — e.g. an
            // explicit `Authorization` row overrides the typed auth).
            val composedHeaders = composeHeaders(config = config)
            // The SSE plugin is required by both transports: classic SSE for the
            // event stream, Streamable HTTP for the inbound notification channel.
            // Installing it unconditionally lets either branch reuse the same
            // HttpClient without juggling `client.config { install(SSE) }` calls.
            val client = HttpClient {
                install(SSE)
                if (composedHeaders.isNotEmpty()) {
                    defaultRequest {
                        composedHeaders.forEach { (key, value) -> headers.append(key, value) }
                    }
                }
            }
            // try/finally (no catch) so every failure — including
            // cancellation — propagates unchanged while the locally created
            // client is still closed; a catch-and-rethrow here would have to
            // special-case CancellationException to keep cancellation
            // cooperative.
            var attached = false
            try {
                val transport: Transport = when (config.transport) {
                    McpTransport.SSE -> McpToolRegistryProvider.defaultSseTransport(
                        url = config.url,
                        baseClient = client,
                    )
                    McpTransport.STREAMABLE_HTTP -> client.mcpStreamableHttpTransport(url = config.url)
                }
                val serverInfo = McpServerInfo(url = config.url, command = "")
                registry = McpToolRegistryProvider.fromTransport(transport, serverInfo)
                // Publish the client into the field only after the transport has been
                // attached successfully — failure paths must close it locally.
                httpClient = client
                attached = true
            } finally {
                if (!attached) {
                    runCatching { client.close() }
                }
            }
        }
    }

    /**
     * Disconnects from the current MCP server, clearing the registry and closing the HTTP client.
     * The client is nulled out so that a subsequent [connect] creates a fresh instance.
     */
    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            registry = null
            httpClient?.close()
            httpClient = null
        }
    }

    /**
     * Retrieves the list of available tools from the connected Koog ToolRegistry.
     * Maps the Koog tool descriptors to the domain-specific [AgentTool] models.
     *
     * @return A list of [AgentTool] objects, or an empty list if not connected.
     */
    override suspend fun getTools(): List<AgentTool> = withContext(Dispatchers.IO) {
        registry?.tools?.map { tool ->
            AgentTool(
                name = tool.name,
                description = tool.descriptor.description,
                parameters = run {
                    val root = JSONObject()
                    root.put("type", "object")
                    val props = JSONObject()
                    val required = JSONArray()
                    tool.descriptor.requiredParameters.forEach { param ->
                        props.put(param.name, JSONObject().put("type", "string"))
                        required.put(param.name)
                    }
                    tool.descriptor.optionalParameters.forEach { param ->
                        props.put(param.name, JSONObject().put("type", "string"))
                    }
                    root.put("properties", props)
                    if (required.length() > 0) root.put("required", required)
                    root.toString()
                },
            )
        } ?: emptyList()
    }

    /**
     * Executes a specific tool by name from the Koog ToolRegistry.
     * Parses the JSON arguments and uses the Koog serializer to execute and format the result.
     *
     * @param name The name of the tool to execute.
     * @param arguments A JSON string representing the arguments.
     * @return A string containing the serialized result of the execution.
     * @throws IllegalArgumentException if the tool is not found.
     */
    override suspend fun executeTool(name: String, arguments: String): String = withContext(Dispatchers.IO) {
        networkActivityTracker?.recordOutbound()
        val tool = registry?.getToolOrNull(name)
            ?: throw IllegalArgumentException("Tool $name not found")

        val kotlinxJsonArgs = Json.parseToJsonElement(arguments).jsonObject
        val koogJsonArgs = kotlinxJsonArgs.toKoogJSONObject()
        val args = tool.decodeArgs(koogJsonArgs, serializer)

        val result = tool.executeUnsafe(args!!)
        tool.encodeResultToStringUnsafe(result!!, serializer)
    }

    /** Header-composition + auth helpers shared across instances. */
    companion object {
        /**
         * Builds the final request-header map for [config]: typed
         * [McpAuth] is rendered first, then user-supplied
         * `config.headers` are overlaid on top (custom rows win on
         * conflict — that's the documented power-user override).
         */
        internal fun composeHeaders(config: McpServerConfig): Map<String, String> {
            val builder = LinkedHashMap<String, String>()
            when (val auth = config.auth) {
                is McpAuth.None -> Unit
                is McpAuth.Bearer -> if (auth.token.isNotBlank()) {
                    builder[HttpHeaders.Authorization] = "Bearer ${auth.token}"
                }
                is McpAuth.Basic -> {
                    val credentials = "${auth.username}:${auth.password}"
                    val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
                    builder[HttpHeaders.Authorization] = "Basic $encoded"
                }
                is McpAuth.ApiKey -> if (auth.headerName.isNotBlank() && auth.value.isNotBlank()) {
                    builder[auth.headerName] = auth.value
                }
            }
            config.headers.forEach { (key, value) -> builder[key] = value }
            return builder
        }
    }
}

/**
 * Factory class for creating [KoogMcpClient] instances.
 * Injected via Hilt for dependency management.
 */
class KoogMcpClientFactory @Inject constructor(private val networkActivityTracker: NetworkActivityTracker) :
    McpClientFactory {
    /**
     * Creates a new instance of [KoogMcpClient]. Each instance carries the
     * shared [NetworkActivityTracker] so MCP traffic surfaces in the More
     * tab's privacy indicator.
     *
     * @return A new [McpClient] implementation.
     */
    override fun create(): McpClient = KoogMcpClient(networkActivityTracker = networkActivityTracker)
}
