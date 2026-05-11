package ai.agent.android.presentation.ui.tools

import ai.agent.android.domain.models.AgentTool
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolsViewModelTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val toolRepository: ToolRepository = mockk(relaxed = true)
    private lateinit var viewModel: ToolsViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val mcpServersFlow = MutableStateFlow<Set<String>>(emptySet())
    private val disabledAppFunctionsFlow = MutableStateFlow<Set<String>>(emptySet())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.mcpServerUrls } returns mcpServersFlow
        every { settingsRepository.disabledAppFunctions } returns disabledAppFunctionsFlow
        coEvery { toolRepository.getAllLocalTools() } returns listOf(AgentTool("get_system_time", "desc", "{}"))
        viewModel = ToolsViewModel(settingsRepository, toolRepository)
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

        assertEquals(listOf("http://test.com"), viewModel.uiState.value.mcpServers)
        assertEquals(setOf("get_system_time"), viewModel.uiState.value.disabledAppFunctions)
        assertEquals("get_system_time", viewModel.uiState.value.localTools.first().name)
    }

    @Test
    fun `addMcpServer calls repository and clears input`() = runTest {
        viewModel.onMcpUrlInputChanged("http://new.com")
        assertEquals("http://new.com", viewModel.uiState.value.newMcpUrlInput)

        viewModel.addMcpServer()
        advanceUntilIdle()

        coVerify { settingsRepository.addMcpServerUrl("http://new.com") }
        assertEquals("", viewModel.uiState.value.newMcpUrlInput)
    }

    @Test
    fun `removeMcpServer calls repository`() = runTest {
        viewModel.removeMcpServer("http://old.com")
        advanceUntilIdle()

        coVerify { settingsRepository.removeMcpServerUrl("http://old.com") }
    }

    @Test
    fun `toggleLocalTool updates repository`() = runTest {
        disabledAppFunctionsFlow.value = setOf("get_system_time")
        advanceUntilIdle()

        // Enable it
        viewModel.toggleLocalTool("get_system_time", true)
        advanceUntilIdle()

        coVerify { settingsRepository.setDisabledAppFunctions(emptySet()) }
    }
}
