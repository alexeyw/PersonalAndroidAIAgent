package ai.agent.android.presentation.ui.monitoring

import ai.agent.android.domain.models.AgentMetrics
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.PowerState
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.PowerStateRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var chatRepository: ChatRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var powerStateRepository: PowerStateRepository
    private lateinit var viewModel: MonitoringViewModel
    private lateinit var metricsFlow: MutableStateFlow<AgentMetrics>
    private lateinit var powerStateFlow: MutableStateFlow<PowerState>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = mockk()
        metricsRepository = mockk()
        powerStateRepository = mockk()
        
        metricsFlow = MutableStateFlow(AgentMetrics())
        every { metricsRepository.metrics } returns metricsFlow

        powerStateFlow = MutableStateFlow(PowerState(isBatteryLow = false, isCharging = true))
        every { powerStateRepository.powerState } returns powerStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init should load recent logs and update uiState`() = runTest {
        val mockLogs = listOf(
            ChatMessage(id = 1L, sessionId = "session1", role = Role.SYSTEM, content = "Observation 1", timestamp = 1000L),
            ChatMessage(id = 2L, sessionId = "session1", role = Role.SYSTEM, content = "Observation 2", timestamp = 2000L)
        )
        
        every { chatRepository.getRecentSystemMessages(any()) } returns flowOf(mockLogs)
        
        viewModel = MonitoringViewModel(chatRepository, metricsRepository, powerStateRepository)
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.recentLogs.size)
        assertEquals("Observation 1", state.recentLogs[0].content)
        verify { chatRepository.getRecentSystemMessages(50) }
        job.cancel()
    }
    
    @Test
    fun `uiState should reflect updates from metrics repository`() = runTest {
        every { chatRepository.getRecentSystemMessages(any()) } returns flowOf(emptyList())
        
        viewModel = MonitoringViewModel(chatRepository, metricsRepository, powerStateRepository)
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        
        // Initial metrics
        assertEquals(0L, viewModel.uiState.value.metrics.lastInferenceTimeMs)
        
        // Update metrics
        val newMetrics = AgentMetrics(lastInferenceTimeMs = 1500L, tokensPerSecond = 20.5f, totalTokensProcessed = 100)
        metricsFlow.value = newMetrics
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(1500L, state.metrics.lastInferenceTimeMs)
        assertEquals(20.5f, state.metrics.tokensPerSecond)
        assertEquals(100, state.metrics.totalTokensProcessed)
        job.cancel()
    }

    @Test
    fun `uiState should correctly reflect power saving state`() = runTest {
        every { chatRepository.getRecentSystemMessages(any()) } returns flowOf(emptyList())

        viewModel = MonitoringViewModel(chatRepository, metricsRepository, powerStateRepository)
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        // Initially not low battery, so not power saving
        assertFalse(viewModel.uiState.value.isPowerSavingActive)

        // Low battery but charging -> still not power saving
        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = true)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isPowerSavingActive)

        // Low battery and NOT charging -> POWER SAVING ACTIVE
        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPowerSavingActive)

        job.cancel()
    }
}
