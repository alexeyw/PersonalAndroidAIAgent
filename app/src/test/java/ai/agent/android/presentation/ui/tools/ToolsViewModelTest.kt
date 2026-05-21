package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.McpConnectionStatus
import ai.agent.android.domain.models.McpTool
import ai.agent.android.domain.repositories.McpServerRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ToolsViewModel]. The VM owns three concerns:
 *
 *  - reconciling `SettingsRepository.mcpServerUrls` against the in-memory
 *    snapshot list (with per-URL status observers),
 *  - persisting toggle changes through `SettingsRepository.disabledAppFunctions`
 *    and `SettingsRepository.disabledMcpTools`,
 *  - exposing a synchronous `findMcpTool(id)` lookup for the detail screen.
 *
 * The tests cover one happy path per concern plus the two main failure
 * states (connection error, attempted fetch on add).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolsViewModelTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val toolRepository: ToolRepository = mockk(relaxed = true)
    private val mcpServerRepository: McpServerRepository = mockk(relaxed = true)

    private lateinit var viewModel: ToolsViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val mcpServersFlow = MutableStateFlow<Set<String>>(emptySet())
    private val disabledAppFunctionsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val disabledMcpToolsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val statusFlows = mutableMapOf<String, MutableStateFlow<McpConnectionStatus>>()

    private fun statusFlowFor(url: String): MutableStateFlow<McpConnectionStatus> =
        statusFlows.getOrPut(url) { MutableStateFlow(McpConnectionStatus.Connecting) }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.mcpServerUrls } returns mcpServersFlow
        every { settingsRepository.disabledAppFunctions } returns disabledAppFunctionsFlow
        every { settingsRepository.disabledMcpTools } returns disabledMcpToolsFlow
        coEvery { toolRepository.getAllLocalTools() } returns listOf(
            AgentTool(name = "get_system_time", description = "desc", parameters = "{}"),
        )
        every { mcpServerRepository.observeConnectionStatus(any()) } answers { statusFlowFor(firstArg()) }
        coEvery { mcpServerRepository.fetchToolList(any(), any()) } returns Result.success(emptyList())
        viewModel = ToolsViewModel(
            settingsRepository = settingsRepository,
            toolRepository = toolRepository,
            mcpServerRepository = mcpServerRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects repository values`() = runTest {
        mcpServersFlow.value = setOf("http://test.com")
        disabledAppFunctionsFlow.value = setOf("get_system_time")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("http://test.com"), state.mcpServers.map { it.url })
        assertEquals(setOf("get_system_time"), state.disabledAppFunctions)
        assertEquals("get_system_time", state.localTools.first().name)
    }

    @Test
    fun `addMcpServer trims input persists URL and clears the input`() = runTest {
        viewModel.onMcpUrlInputChanged("  http://new.com  ")
        viewModel.addMcpServer()
        advanceUntilIdle()

        coVerify { settingsRepository.addMcpServerUrl(url = "http://new.com") }
        assertEquals("", viewModel.uiState.value.newMcpUrlInput)
    }

    @Test
    fun `appearing server snapshot exists before observers and fetches launch`() = runTest {
        // Regression guard for the reconcile-order race: the snapshot list MUST be
        // rewritten BEFORE observers / fetches launch, otherwise the status flow's
        // initial Connecting emission and the fetchToolList result update can race
        // against the snapshot insertion and be silently overwritten.
        val url = "https://racy.example/mcp"
        val sample = McpTool(
            id = "mcp:cafebabe:tool",
            serverUrl = url,
            name = "tool",
            description = "",
            inputSchemaJson = "{}",
        )
        coEvery {
            mcpServerRepository.fetchToolList(serverUrl = url, forceRefresh = false)
        } returns Result.success(listOf(sample))

        mcpServersFlow.value = setOf(url)
        advanceUntilIdle()

        val snapshot = viewModel.uiState.value.mcpServers.firstOrNull { it.url == url }
        assertTrue("snapshot for $url must exist", snapshot != null)
        assertEquals(listOf(sample), snapshot!!.tools)
    }

    @Test
    fun `appearing server triggers fetchToolList and surfaces tools`() = runTest {
        val url = "https://example.com/mcp"
        val sample = McpTool(
            id = "mcp:abcd1234:search",
            serverUrl = url,
            name = "search",
            description = "Web search",
            inputSchemaJson = "{}",
        )
        coEvery {
            mcpServerRepository.fetchToolList(serverUrl = url, forceRefresh = false)
        } returns Result.success(listOf(sample))

        mcpServersFlow.value = setOf(url)
        advanceUntilIdle()

        coVerify { mcpServerRepository.fetchToolList(serverUrl = url, forceRefresh = false) }
        val snapshot = viewModel.uiState.value.mcpServers.first()
        assertEquals(url, snapshot.url)
        assertEquals(listOf(sample), snapshot.tools)
    }

    @Test
    fun `connection error surfaces in the snapshot status`() = runTest {
        val url = "https://broken.example/mcp"
        mcpServersFlow.value = setOf(url)
        advanceUntilIdle()

        statusFlowFor(url).value = McpConnectionStatus.Error(reason = "boom")
        advanceUntilIdle()

        val snapshot = viewModel.uiState.value.mcpServers.first()
        assertTrue(snapshot.status is McpConnectionStatus.Error)
        assertEquals("boom", (snapshot.status as McpConnectionStatus.Error).reason)
    }

    @Test
    fun `removeMcpServer disconnects repository and drops the snapshot`() = runTest {
        val url = "http://old.com"
        mcpServersFlow.value = setOf(url)
        advanceUntilIdle()

        viewModel.removeMcpServer(url = url)
        mcpServersFlow.value = emptySet()
        advanceUntilIdle()

        coVerify { settingsRepository.removeMcpServerUrl(url = url) }
        coVerify { mcpServerRepository.disconnect(serverUrl = url) }
        assertTrue(viewModel.uiState.value.mcpServers.isEmpty())
    }

    @Test
    fun `refreshServer forces a fresh fetchToolList`() = runTest {
        val url = "https://refresh.example/mcp"
        mcpServersFlow.value = setOf(url)
        advanceUntilIdle()

        viewModel.refreshServer(serverUrl = url)
        advanceUntilIdle()

        coVerify { mcpServerRepository.fetchToolList(serverUrl = url, forceRefresh = true) }
    }

    @Test
    fun `toggleServerExpanded flips the membership in expandedServerUrls`() = runTest {
        val url = "https://expand.example/mcp"
        mcpServersFlow.value = setOf(url)
        advanceUntilIdle()

        viewModel.toggleServerExpanded(serverUrl = url)
        assertEquals(setOf(url), viewModel.uiState.value.expandedServerUrls)
        viewModel.toggleServerExpanded(serverUrl = url)
        assertTrue(viewModel.uiState.value.expandedServerUrls.isEmpty())
    }

    @Test
    fun `toggleLocalTool enables previously-disabled function`() = runTest {
        disabledAppFunctionsFlow.value = setOf("get_system_time")
        advanceUntilIdle()

        viewModel.toggleLocalTool(toolName = "get_system_time", isEnabled = true)
        advanceUntilIdle()

        coVerify { settingsRepository.setDisabledAppFunctions(functions = emptySet()) }
    }

    @Test
    fun `toggleMcpTool writes to disabledMcpTools`() = runTest {
        val id = "mcp:abcd1234:search"
        viewModel.toggleMcpTool(toolId = id, isEnabled = false)
        advanceUntilIdle()

        coVerify { settingsRepository.setDisabledMcpTools(toolIds = setOf(id)) }
    }

    @Test
    fun `findMcpTool returns the matching tool or null`() = runTest {
        val url = "https://lookup.example/mcp"
        val tool = McpTool(
            id = "mcp:deadbeef:search",
            serverUrl = url,
            name = "search",
            description = "Web search",
            inputSchemaJson = "{}",
        )
        coEvery {
            mcpServerRepository.fetchToolList(serverUrl = url, forceRefresh = false)
        } returns Result.success(listOf(tool))

        mcpServersFlow.value = setOf(url)
        advanceUntilIdle()

        assertEquals(tool, viewModel.findMcpTool(toolId = tool.id))
        assertNull(viewModel.findMcpTool(toolId = "mcp:deadbeef:missing"))
    }
}
