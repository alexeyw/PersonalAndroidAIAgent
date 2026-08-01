package app.knotwork.android.data.mcp

import app.knotwork.android.domain.models.McpServerConfig
import app.knotwork.android.domain.models.McpTransport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.pluginOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Session-lifecycle regression tests for [KoogMcpClient], covering finding F3
 * of the phase-40 directed MCP test: on the device a tool call failed with
 * `-32000 No valid session ID provided`, and the wire capture showed sessions
 * being opened and abandoned faster than anything was using them.
 *
 * These drive the real [KoogMcpClient.connect] / [KoogMcpClient.executeTool]
 * against a minimal Streamable-HTTP stub, so they assert what actually crosses
 * the wire rather than what the implementation claims.
 *
 * The pre-existing [KoogMcpClientTest] injects a mock registry by reflection
 * and never exercises `connect` at all — which is why none of this was caught
 * before the directed run.
 */
class KoogMcpClientSessionTest {

    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<String>()

    @After
    fun tearDown() {
        server.close()
    }

    /** Minimal Streamable-HTTP MCP server: answers `initialize` and `tools/list`. */
    private fun start() {
        var sessionCounter = 0
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "DELETE") {
                    received += "DELETE sid=${request.headers["mcp-session-id"]}"
                    return MockResponse.Builder().code(200).build()
                }
                if (request.method == "GET") {
                    received += "GET(sse) sid=${request.headers["mcp-session-id"]}"
                    // Keep the server-initiated stream empty but open-ended.
                    return MockResponse.Builder()
                        .code(200)
                        .headers(Headers.headersOf("Content-Type", "text/event-stream"))
                        .body(": keep-alive\n\n")
                        .build()
                }
                val body = request.body?.utf8().orEmpty()
                val method = Regex("\"method\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                val id = Regex("\"id\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1) ?: "1"
                received += "${method ?: "?"} sid=${request.headers["mcp-session-id"]}"

                return when (method) {
                    "initialize" -> {
                        sessionCounter += 1
                        val payload = """
                            {"jsonrpc":"2.0","id":$id,"result":{
                              "protocolVersion":"2025-11-25",
                              "capabilities":{"tools":{"listChanged":true}},
                              "serverInfo":{"name":"stub","version":"1"}}}
                        """.trimIndent()
                        sse(payload, sessionId = "session-$sessionCounter")
                    }
                    "notifications/initialized" -> MockResponse.Builder().code(202).build()
                    "tools/list" -> sse(
                        """
                        {"jsonrpc":"2.0","id":$id,"result":{"tools":[
                          {"name":"echo","description":"Echoes back",
                           "inputSchema":{"type":"object",
                             "properties":{"message":{"type":"string"}},
                             "required":["message"]}}]}}
                        """.trimIndent(),
                    )
                    else -> sse("""{"jsonrpc":"2.0","id":$id,"result":{}}""")
                }
            }
        }
        server.start()
    }

    private fun sse(payload: String, sessionId: String? = null): MockResponse {
        val headers = mutableListOf("Content-Type", "text/event-stream")
        if (sessionId != null) {
            headers += listOf("mcp-session-id", sessionId)
        }
        return MockResponse.Builder()
            .code(200)
            .headers(Headers.headersOf(*headers.toTypedArray()))
            .body("event: message\ndata: ${payload.replace("\n", "")}\n\n")
            .build()
    }

    @Test
    fun `given a fresh client when connect then exactly one session is opened`() = runTest {
        start()
        val client = KoogMcpClient()

        client.connect(
            McpServerConfig(
                url = server.url("/mcp").toString(),
                transport = McpTransport.STREAMABLE_HTTP,
            ),
        )
        val tools = client.getTools()

        assertEquals(listOf("echo"), tools.map { it.name })
        assertEquals(1, received.count { it.startsWith("initialize") })

        client.disconnect()
    }

    @Test
    fun `given a connected client then an explicit request timeout is installed`() = runTest {
        start()
        val client = KoogMcpClient()
        client.connect(
            McpServerConfig(
                url = server.url("/mcp").toString(),
                transport = McpTransport.STREAMABLE_HTTP,
            ),
        )

        // The deadline used to be whatever the transitively-resolved Ktor engine
        // defaulted to — measured at 10 s on device, too short for real MCP tools
        // and liable to change under us on any Ktor/Koog bump (phase-40 F12).
        // Asserting the plugin is attached to the live client, not just that a
        // constant exists: the constant alone would pass with the install missing.
        val session = KoogMcpClient::class.java.getDeclaredField("session")
            .apply { isAccessible = true }
            .get(client)!!
        val httpClient = session.javaClass.getDeclaredField("httpClient")
            .apply { isAccessible = true }
            .get(session) as HttpClient

        assertTrue(
            "MCP client must not inherit the engine's default request deadline",
            httpClient.pluginOrNull(HttpTimeout) != null,
        )
        client.disconnect()
    }

    @Test
    fun `given a live connection when disconnect then the session is terminated server-side`() = runTest {
        start()
        val client = KoogMcpClient()
        client.connect(
            McpServerConfig(
                url = server.url("/mcp").toString(),
                transport = McpTransport.STREAMABLE_HTTP,
            ),
        )
        client.getTools()

        client.disconnect()

        // Closing the socket is not enough: an MCP server keeps a Streamable-HTTP
        // session until it is explicitly terminated, so a client that only closes
        // strands one session per connect.
        assertTrue(
            "expected a session-terminating DELETE, wire was: $received",
            received.any { it.startsWith("DELETE") },
        )
    }

    @Test
    fun `given a tool call in flight when a concurrent reconnect lands then the call still resolves`() = runTest {
        start()
        val client = KoogMcpClient()
        val config = McpServerConfig(
            url = server.url("/mcp").toString(),
            transport = McpTransport.STREAMABLE_HTTP,
        )
        client.connect(config)
        client.getTools()

        // The call runs in a scope of its own so its failure does not tear down
        // the test scope — the whole point is to observe what the caller sees.
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val call = scope.async {
            client.executeTool(name = "echo", arguments = """{"message":"hi"}""")
        }
        // A concurrent Refresh (McpServerRepositoryImpl.fetchToolList calls
        // connect() unconditionally) tears the transport down underneath it.
        client.connect(config)

        // Deliberately catching without re-throwing: the type the caller observes
        // IS the assertion here. Nothing suspends afterwards, so cooperative
        // cancellation is not compromised.
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        val observed: Throwable? = try {
            call.await()
            null
        } catch (e: Throwable) {
            e
        }

        // Before the session snapshot was atomic, the reconnect blanked the
        // registry mid-lookup and the caller was told `Tool echo not found` —
        // i.e. the agent was informed a tool it has does not exist.
        assertFalse(
            "a concurrent reconnect must not surface as a missing tool: $observed",
            observed is IllegalArgumentException,
        )

        scope.cancel()
        client.disconnect()
    }
}
