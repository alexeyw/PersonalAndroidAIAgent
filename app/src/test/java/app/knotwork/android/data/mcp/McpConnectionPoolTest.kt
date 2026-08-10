package app.knotwork.android.data.mcp

import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.McpAuth
import app.knotwork.android.domain.models.McpServerConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests for [McpConnectionPool] — the single owner of live MCP connections.
 *
 * The behaviours pinned here are the ones the two former per-repository pools each
 * implemented separately (and could therefore drift on): reuse an equal config,
 * reconnect a changed one, swallow connect failures during reconcile, prune servers
 * that left the settings list.
 */
class McpConnectionPoolTest {

    private val factory: McpClientFactory = mockk()
    private val config = McpServerConfig(url = "http://localhost:8080")

    private fun client(): McpClient = mockk<McpClient>().also {
        coEvery { it.connect(any()) } returns Unit
        coEvery { it.disconnect() } returns Unit
        coEvery { it.getTools() } returns emptyList()
    }

    @Test
    fun `given an equal config when asked twice then the same client is reused`() = runTest {
        val only = client()
        every { factory.create() } returns only
        val pool = McpConnectionPool(factory)

        val first = pool.withServer(config.url) { client(config) }
        val second = pool.withServer(config.url) { client(config) }

        assertSame(first, second)
        coVerify(exactly = 1) { only.connect(any()) }
    }

    @Test
    fun `given a changed config when asked again then the stale client is disconnected and replaced`() = runTest {
        // Auth edited in Settings must actually apply — keying by URL alone would keep
        // the old credentials alive until the process restarted.
        val stale = client()
        val fresh = client()
        every { factory.create() } returnsMany listOf(stale, fresh)
        val pool = McpConnectionPool(factory)

        pool.withServer(config.url) { client(config) }
        val second = pool.withServer(config.url) {
            client(config.copy(auth = McpAuth.Bearer(token = "t")))
        }

        assertSame(fresh, second)
        coVerify(exactly = 1) { stale.disconnect() }
    }

    @Test
    fun `given invalidate when peeked then nothing is pooled and the client was disconnected`() = runTest {
        val only = client()
        every { factory.create() } returns only
        val pool = McpConnectionPool(factory)
        pool.withServer(config.url) { client(config) }

        pool.withServer(config.url) { invalidate() }

        assertNull(pool.peek(config.url))
        coVerify(exactly = 1) { only.disconnect() }
    }

    @Test
    fun `given a server that fails to connect when reconcile then the others still connect`() = runTest {
        // A dead server must not abort the reconcile for the healthy ones beside it.
        val dead = mockk<McpClient>().also {
            coEvery { it.connect(any()) } throws IllegalStateException("refused")
            coEvery { it.disconnect() } returns Unit
        }
        val alive = client()
        every { factory.create() } returnsMany listOf(dead, alive)
        val pool = McpConnectionPool(factory)

        pool.reconcile(
            listOf(
                McpServerConfig(url = "http://dead:1"),
                McpServerConfig(url = "http://alive:2"),
            ),
        )

        assertNull(pool.peek("http://dead:1"))
        assertNotNull(pool.peek("http://alive:2"))
    }

    @Test
    fun `given a server removed from settings when reconcile then it is disconnected and dropped`() = runTest {
        val only = client()
        every { factory.create() } returns only
        val pool = McpConnectionPool(factory)
        pool.reconcile(listOf(config))

        pool.reconcile(emptyList())

        assertNull(pool.peek(config.url))
        coVerify(exactly = 1) { only.disconnect() }
    }

    @Test
    fun `given a failed tools-list on the Tools screen then the agent does not reuse the dead session`() = runTest {
        // The finding this pool exists for. `McpServerRepositoryImpl` (health
        // indicator) and `ToolRepositoryImpl` (real calls) used to hold one
        // `@Singleton` pool each, so a session the Tools screen had already written
        // off as broken stayed live on the execution side — the screen could read
        // "ok" while the next tool call went to a dead client.
        //
        // Asserted at the pool, which is now the single source of truth: a failure
        // that invalidates an entry must leave nothing behind for the other
        // consumer to pick up.
        val dead = client()
        val fresh = client()
        every { factory.create() } returnsMany listOf(dead, fresh)
        val pool = McpConnectionPool(factory)

        // The agent side connects first and would happily keep this client.
        pool.reconcile(listOf(config))
        assertSame(dead, pool.peek(config.url))

        // The Tools screen's fetch fails and drops the entry.
        pool.withServer(config.url) { invalidate() }
        assertNull(pool.peek(config.url))

        // The agent's next reconcile therefore builds a new session instead of
        // routing onto the one already known to be broken.
        pool.reconcile(listOf(config))
        assertSame(fresh, pool.peek(config.url))
    }

    @Test
    fun `given a connected server when peeked then the pooled client is returned without connecting`() = runTest {
        val only = client()
        coEvery { only.getTools() } returns listOf(AgentTool("t", "d", "{}"))
        every { factory.create() } returns only
        val pool = McpConnectionPool(factory)
        pool.reconcile(listOf(config))

        assertSame(only, pool.peek(config.url))
        coVerify(exactly = 1) { only.connect(any()) }
    }
}
