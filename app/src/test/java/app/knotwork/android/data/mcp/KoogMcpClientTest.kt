package app.knotwork.android.data.mcp

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import app.knotwork.android.domain.models.McpAuth
import app.knotwork.android.domain.models.McpServerConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogMcpClientTest {

    private fun makeClient(tools: List<Tool<*, *>>): KoogMcpClient {
        val client = KoogMcpClient()
        val field = client.javaClass.getDeclaredField("registry")
        field.isAccessible = true
        val mockRegistry = mockk<ToolRegistry>()
        every { mockRegistry.tools } returns tools
        field.set(client, mockRegistry)
        return client
    }

    @Test
    fun `getTools maps ToolRegistry to AgentTool with valid JSON Schema`() = runTest {
        val mockDescriptor = mockk<ToolDescriptor>()
        every { mockDescriptor.description } returns "test description"
        every { mockDescriptor.requiredParameters } returns emptyList()
        every { mockDescriptor.optionalParameters } returns emptyList()

        val mockTool = mockk<Tool<Any, Any>>()
        every { mockTool.name } returns "testTool"
        every { mockTool.descriptor } returns mockDescriptor

        val tools = makeClient(listOf(mockTool)).getTools()

        assertEquals(1, tools.size)
        assertEquals("testTool", tools[0].name)
        assertEquals("test description", tools[0].description)
        val schema = JSONObject(tools[0].parameters)
        assertEquals("object", schema.getString("type"))
    }

    @Test
    fun `getTools builds required array for required parameters`() = runTest {
        val requiredParam = mockk<ToolParameterDescriptor>()
        every { requiredParam.name } returns "query"

        val mockDescriptor = mockk<ToolDescriptor>()
        every { mockDescriptor.description } returns "search tool"
        every { mockDescriptor.requiredParameters } returns listOf(requiredParam)
        every { mockDescriptor.optionalParameters } returns emptyList()

        val mockTool = mockk<Tool<Any, Any>>()
        every { mockTool.name } returns "search"
        every { mockTool.descriptor } returns mockDescriptor

        val tools = makeClient(listOf(mockTool)).getTools()

        val schema = JSONObject(tools[0].parameters)
        assertTrue(schema.has("required"))
        assertEquals("query", schema.getJSONArray("required").getString(0))
        assertTrue(schema.getJSONObject("properties").has("query"))
    }

    @Test
    fun `connect that fails during transport attachment leaves httpClient field null`() = runTest {
        // Leak-on-failure regression guard: when SSE transport attachment to a
        // non-routable address surfaces an exception, the freshly-created HttpClient
        // must be closed locally. The field stays at its previous value (null on a
        // first attempt), so a retry does not accumulate leaked Ktor engines.
        val client = KoogMcpClient()
        val httpClientField = client.javaClass.getDeclaredField("httpClient")
        httpClientField.isAccessible = true

        val outcome = runCatching { client.connect(McpServerConfig(url = "http://127.0.0.1:1")) }

        assertTrue("Expected connect against unreachable host to fail", outcome.isFailure)
        assertEquals(
            "Failed connect must clean up the new HttpClient — leaked engine = bug",
            null,
            httpClientField.get(client),
        )
    }

    @Test
    fun `repeated failed connects do not accumulate leaked HttpClient instances`() = runTest {
        // Same invariant under repeated failure: the field must remain clean so callers
        // can keep retrying without building up open Ktor engines.
        val client = KoogMcpClient()
        val httpClientField = client.javaClass.getDeclaredField("httpClient")
        httpClientField.isAccessible = true

        repeat(3) {
            runCatching { client.connect(McpServerConfig(url = "http://127.0.0.1:1")) }
            assertEquals(
                "After failed connect #${it + 1} the httpClient field must be null",
                null,
                httpClientField.get(client),
            )
        }
    }

    @Test
    fun `disconnect after a failed connect keeps httpClient field null`() = runTest {
        // Defect 5 regression guard: a disconnect after a failed connect is harmless
        // (no double-close) and leaves the field in the documented "no client" state.
        val client = KoogMcpClient()
        val httpClientField = client.javaClass.getDeclaredField("httpClient")
        httpClientField.isAccessible = true

        runCatching { client.connect(McpServerConfig(url = "http://127.0.0.1:1")) }
        client.disconnect()

        assertEquals(null, httpClientField.get(client))
    }

    @Test
    fun `getTools optional parameters appear in properties but not in required`() = runTest {
        val optionalParam = mockk<ToolParameterDescriptor>()
        every { optionalParam.name } returns "lang"

        val mockDescriptor = mockk<ToolDescriptor>()
        every { mockDescriptor.description } returns "search tool"
        every { mockDescriptor.requiredParameters } returns emptyList()
        every { mockDescriptor.optionalParameters } returns listOf(optionalParam)

        val mockTool = mockk<Tool<Any, Any>>()
        every { mockTool.name } returns "search"
        every { mockTool.descriptor } returns mockDescriptor

        val tools = makeClient(listOf(mockTool)).getTools()

        val schema = JSONObject(tools[0].parameters)
        assertTrue(schema.getJSONObject("properties").has("lang"))
        assertTrue(!schema.has("required"))
    }

    @Test
    fun `composeHeaders renders Bearer auth as Authorization header`() {
        val headers = KoogMcpClient.composeHeaders(
            config = McpServerConfig(url = "https://x/", auth = McpAuth.Bearer(token = "abc")),
        )
        assertEquals("Bearer abc", headers["Authorization"])
    }

    @Test
    fun `composeHeaders renders Basic auth as base64-encoded Authorization header`() {
        val headers = KoogMcpClient.composeHeaders(
            config = McpServerConfig(url = "https://x/", auth = McpAuth.Basic(username = "user", password = "pw")),
        )
        // Base64("user:pw") = dXNlcjpwdw==
        assertEquals("Basic dXNlcjpwdw==", headers["Authorization"])
    }

    @Test
    fun `composeHeaders puts ApiKey auth under the requested header name`() {
        val headers = KoogMcpClient.composeHeaders(
            config = McpServerConfig(
                url = "https://x/",
                auth = McpAuth.ApiKey(headerName = "X-API-Key", value = "secret"),
            ),
        )
        assertEquals("secret", headers["X-API-Key"])
    }

    @Test
    fun `composeHeaders lets custom headers override the typed auth`() {
        // Power-user contract: if you take the trouble to set an explicit
        // Authorization row in the headers section, it wins over the typed
        // Bearer above. This allows oddball Authorization schemes the typed
        // selector doesn't cover (DPoP, MAC, etc.) without a code change.
        val headers = KoogMcpClient.composeHeaders(
            config = McpServerConfig(
                url = "https://x/",
                auth = McpAuth.Bearer(token = "typed"),
                headers = mapOf("Authorization" to "Custom override"),
            ),
        )
        assertEquals("Custom override", headers["Authorization"])
    }

    @Test
    fun `composeHeaders skips empty Bearer and ApiKey entries`() {
        val emptyBearer = KoogMcpClient.composeHeaders(
            config = McpServerConfig(url = "https://x/", auth = McpAuth.Bearer(token = "")),
        )
        val emptyApiKey = KoogMcpClient.composeHeaders(
            config = McpServerConfig(url = "https://x/", auth = McpAuth.ApiKey(headerName = "X-K", value = "")),
        )
        assertTrue(emptyBearer.isEmpty())
        assertTrue(emptyApiKey.isEmpty())
    }
}
