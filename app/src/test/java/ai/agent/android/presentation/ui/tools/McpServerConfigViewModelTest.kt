package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.McpAuth
import ai.agent.android.domain.models.McpServerConfig
import ai.agent.android.domain.models.McpTransport
import ai.agent.android.domain.repositories.McpServerRepository
import ai.agent.android.domain.repositories.SettingsRepository
import androidx.lifecycle.SavedStateHandle
import app.knotwork.design.screens.tools.McpAuthSelector
import app.knotwork.design.screens.tools.McpHeaderRow
import app.knotwork.design.screens.tools.McpTransportOption
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpServerConfigViewModelTest {

    private val settings: SettingsRepository = mockk(relaxed = true)
    private val mcp: McpServerRepository = mockk(relaxed = true)
    private val mcpServersFlow = MutableStateFlow<List<McpServerConfig>>(emptyList())

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settings.mcpServers } returns mcpServersFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(originalUrl: String? = null): McpServerConfigViewModel = McpServerConfigViewModel(
        savedStateHandle = SavedStateHandle(
            if (originalUrl != null) mapOf(McpServerConfigViewModel.EXTRA_ORIGINAL_URL to originalUrl) else emptyMap(),
        ),
        settingsRepository = settings,
        mcpServerRepository = mcp,
    )

    @Test
    fun `add mode starts with an empty form and editingUrl null`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        val form = viewModel.form.value
        assertEquals("", form.url)
        assertNull(form.editingUrl)
        assertTrue(form.headers.isEmpty())
    }

    @Test
    fun `edit mode pre-fills the form from settingsRepository`() = runTest {
        val url = "https://prefill.example/mcp"
        mcpServersFlow.value = listOf(
            McpServerConfig(
                url = url,
                name = "Prefilled",
                transport = McpTransport.STREAMABLE_HTTP,
                headers = mapOf("Authorization" to "Bearer t"),
            ),
        )

        val viewModel = vm(originalUrl = url)
        advanceUntilIdle()

        val form = viewModel.form.value
        assertEquals(url, form.url)
        assertEquals("Prefilled", form.name)
        assertEquals(McpTransportOption.StreamableHttp, form.transport)
        assertEquals(McpHeaderRow(key = "Authorization", value = "Bearer t"), form.headers.single())
        assertEquals(url, form.editingUrl)
    }

    @Test
    fun `onSubmit in add mode persists the config and emits Saved`() = runTest {
        val viewModel = vm()
        viewModel.onUrlChange(value = "https://added.example/mcp")
        viewModel.onNameChange(value = "Added")
        viewModel.onHeaderAdd()
        viewModel.onHeaderChange(index = 0, key = "Authorization", value = "Bearer x")
        viewModel.onTransportSelect(option = McpTransportOption.StreamableHttp)

        viewModel.onSubmit()
        advanceUntilIdle()

        coVerify {
            settings.addMcpServer(
                config = McpServerConfig(
                    url = "https://added.example/mcp",
                    name = "Added",
                    transport = McpTransport.STREAMABLE_HTTP,
                    headers = mapOf("Authorization" to "Bearer x"),
                ),
            )
        }
        assertEquals(McpServerConfigViewModel.Event.Saved, viewModel.events.value)
    }

    @Test
    fun `onSubmit in edit mode disconnects old client and updates the config`() = runTest {
        val url = "https://edited.example/mcp"
        mcpServersFlow.value = listOf(McpServerConfig(url = url))
        val viewModel = vm(originalUrl = url)
        advanceUntilIdle()
        viewModel.onNameChange(value = "Renamed")

        viewModel.onSubmit()
        advanceUntilIdle()

        coVerify { mcp.disconnect(serverUrl = url) }
        coVerify {
            settings.updateMcpServer(
                originalUrl = url,
                updated = McpServerConfig(url = url, name = "Renamed"),
            )
        }
        assertEquals(McpServerConfigViewModel.Event.Saved, viewModel.events.value)
    }

    @Test
    fun `onSubmit with invalid URL keeps the form open and surfaces the error`() = runTest {
        val viewModel = vm()
        viewModel.onUrlChange(value = "not a url")

        viewModel.onSubmit()
        advanceUntilIdle()

        coVerify(exactly = 0) { settings.addMcpServer(any()) }
        assertNull(viewModel.events.value)
        assertNotNull(viewModel.form.value.urlError)
    }

    @Test
    fun `bearer auth round-trips through edit mode`() = runTest {
        val url = "https://hf.example/mcp"
        mcpServersFlow.value = listOf(
            McpServerConfig(url = url, auth = McpAuth.Bearer(token = "secret")),
        )

        val viewModel = vm(originalUrl = url)
        advanceUntilIdle()

        val form = viewModel.form.value
        assertEquals(McpAuthSelector.BEARER, form.authType)
        assertEquals("secret", form.bearerToken)

        viewModel.onSubmit()
        advanceUntilIdle()

        coVerify {
            settings.updateMcpServer(
                originalUrl = url,
                updated = McpServerConfig(url = url, auth = McpAuth.Bearer(token = "secret")),
            )
        }
    }

    @Test
    fun `api key auth survives add-mode submission`() = runTest {
        val viewModel = vm()
        viewModel.onUrlChange(value = "https://api.example/mcp")
        viewModel.onAuthTypeSelect(option = McpAuthSelector.API_KEY)
        viewModel.onApiKeyHeaderNameChange(value = "X-API-Key")
        viewModel.onApiKeyValueChange(value = "v1")

        viewModel.onSubmit()
        advanceUntilIdle()

        coVerify {
            settings.addMcpServer(
                config = McpServerConfig(
                    url = "https://api.example/mcp",
                    auth = McpAuth.ApiKey(headerName = "X-API-Key", value = "v1"),
                ),
            )
        }
    }

    @Test
    fun `onHeaderRemove drops the row at the given index`() = runTest {
        val viewModel = vm()
        viewModel.onHeaderAdd()
        viewModel.onHeaderAdd()
        viewModel.onHeaderChange(index = 0, key = "K1", value = "V1")
        viewModel.onHeaderChange(index = 1, key = "K2", value = "V2")

        viewModel.onHeaderRemove(index = 0)

        val rows = viewModel.form.value.headers
        assertEquals(1, rows.size)
        assertEquals(McpHeaderRow(key = "K2", value = "V2"), rows[0])
    }
}
