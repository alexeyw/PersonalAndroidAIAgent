package ai.agent.android.data.repositories

import ai.agent.android.data.mcp.McpClient
import ai.agent.android.data.mcp.McpClientFactory
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.McpConnectionStatus
import ai.agent.android.domain.models.ToolRisk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [McpServerRepositoryImpl]. The implementation owns four
 * behaviours the rest of the surface depends on:
 *
 *  1. mapping `AgentTool` results from `McpClient.getTools()` to
 *     `McpTool` with a stable, route-safe id;
 *  2. emitting `Connecting → Connected` on the status flow when a fetch
 *     succeeds;
 *  3. emitting `Connecting → Error` (and returning `Result.failure`) when
 *     the client throws;
 *  4. caching the tool list for 5 minutes — and bypassing the cache when
 *     `forceRefresh = true`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class McpServerRepositoryImplTest {

    private val url = "https://example.invalid/mcp"

    @Test
    fun `fetchToolList parsesValidResponse and emits Connected status`() = runTest {
        val client = mockk<McpClient>(relaxed = true)
        coEvery { client.getTools() } returns listOf(
            AgentTool(
                name = "search",
                description = "Web search",
                parameters = "{\"type\":\"object\"}",
                risk = ToolRisk.READ_ONLY,
            ),
            AgentTool(name = "shell", description = "Run shell", parameters = "{}"),
        )
        val factory = mockk<McpClientFactory>()
        coEvery { factory.create() } returns client
        val repo = McpServerRepositoryImpl(clientFactory = factory)

        val result = repo.fetchToolList(serverUrl = url)

        assertTrue(result.isSuccess)
        val tools = result.getOrThrow()
        assertEquals(2, tools.size)
        val first = tools[0]
        assertEquals("search", first.name)
        assertEquals("Web search", first.description)
        assertEquals("{\"type\":\"object\"}", first.inputSchemaJson)
        assertEquals(ToolRisk.READ_ONLY, first.risk)
        assertEquals(url, first.serverUrl)
        assertTrue("id should start with mcp: prefix", first.id.startsWith("mcp:"))
        assertEquals(McpConnectionStatus.Connected, repo.observeConnectionStatus(url).first())
    }

    @Test
    fun `fetchToolList emitsErrorOnFailure and returns Result failure`() = runTest {
        val client = mockk<McpClient>(relaxed = true)
        coEvery { client.connect(any()) } throws IllegalStateException("handshake failed")
        val factory = mockk<McpClientFactory>()
        coEvery { factory.create() } returns client
        val repo = McpServerRepositoryImpl(clientFactory = factory)

        val result = repo.fetchToolList(serverUrl = url)

        assertTrue(result.isFailure)
        val status = repo.observeConnectionStatus(url).first()
        assertTrue("expected Error status, was $status", status is McpConnectionStatus.Error)
        assertEquals("handshake failed", (status as McpConnectionStatus.Error).reason)
    }

    @Test
    fun `fetchToolList returnsCachedWithinTtl`() = runTest {
        val client = mockk<McpClient>(relaxed = true)
        coEvery { client.getTools() } returns listOf(AgentTool("a", "d", "{}"))
        val factory = mockk<McpClientFactory>()
        coEvery { factory.create() } returns client
        val repo = McpServerRepositoryImpl(clientFactory = factory)
        var time = 1_000L
        repo.clockMs = { time }

        repo.fetchToolList(serverUrl = url)
        time += 60_000L // 1 minute later — well inside the 5-minute TTL.
        val cached = repo.fetchToolList(serverUrl = url)

        assertTrue(cached.isSuccess)
        coVerify(exactly = 1) { client.connect(url) }
        coVerify(exactly = 1) { client.getTools() }
    }

    @Test
    fun `fetchToolList forceRefreshBypassesCache`() = runTest {
        val client = mockk<McpClient>(relaxed = true)
        coEvery { client.getTools() } returns listOf(AgentTool("a", "d", "{}"))
        val factory = mockk<McpClientFactory>()
        coEvery { factory.create() } returns client
        val repo = McpServerRepositoryImpl(clientFactory = factory)
        repo.clockMs = { 1_000L }

        repo.fetchToolList(serverUrl = url)
        val refreshed = repo.fetchToolList(serverUrl = url, forceRefresh = true)

        assertTrue(refreshed.isSuccess)
        coVerify(exactly = 2) { client.getTools() }
    }

    @Test
    fun `disconnect drops the cache and closes the client`() = runTest {
        val client = mockk<McpClient>(relaxed = true)
        coEvery { client.getTools() } returns listOf(AgentTool("a", "d", "{}"))
        val factory = mockk<McpClientFactory>()
        coEvery { factory.create() } returns client
        val repo = McpServerRepositoryImpl(clientFactory = factory)

        repo.fetchToolList(serverUrl = url)
        repo.disconnect(serverUrl = url)
        repo.fetchToolList(serverUrl = url)

        coVerify { client.disconnect() }
        // A fresh fetch after disconnect must hit `connect` again because the
        // cache and client entry were dropped.
        coVerify(exactly = 2) { client.connect(url) }
    }

    @Test
    fun `cache fast-path clears a previous Error status`() = runTest {
        // After a failed force-refresh has put the status flow in Error, a follow-up
        // cached-tool fetch must reconcile the flow back to Connected so the UI doesn't
        // leave a stale red pill on a server whose tools are still available from cache.
        val client = mockk<McpClient>(relaxed = true)
        coEvery { client.getTools() } returns listOf(AgentTool("a", "d", "{}"))
        val factory = mockk<McpClientFactory>()
        coEvery { factory.create() } returns client
        val repo = McpServerRepositoryImpl(clientFactory = factory)
        repo.clockMs = { 1_000L }

        // 1) First fetch succeeds → cache populated, status = Connected.
        repo.fetchToolList(serverUrl = url)
        // 2) Force-refresh fails → status flips to Error, but cache remains.
        coEvery { client.getTools() } throws IllegalStateException("transient")
        repo.fetchToolList(serverUrl = url, forceRefresh = true)
        assertTrue(repo.observeConnectionStatus(url).first() is McpConnectionStatus.Error)

        // 3) Non-forced fetch within TTL → cache hit → status must reconcile to Connected.
        val cached = repo.fetchToolList(serverUrl = url, forceRefresh = false)
        assertTrue(cached.isSuccess)
        assertEquals(McpConnectionStatus.Connected, repo.observeConnectionStatus(url).first())
    }

    @Test
    fun `mcpToolId is deterministic and route-safe`() {
        val id1 = McpServerRepositoryImpl.mcpToolId(serverUrl = url, toolName = "search")
        val id2 = McpServerRepositoryImpl.mcpToolId(serverUrl = url, toolName = "search")
        assertEquals(id1, id2)
        assertNotNull(id1)
        assertTrue(id1.startsWith("mcp:"))
        // No raw URL characters that Navigation would mishandle in a path arg.
        assertTrue(!id1.contains("/"))
        assertTrue(!id1.contains("?"))
    }
}
