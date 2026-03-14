package ai.agent.android.presentation.ui.monitoring

import ai.agent.android.domain.models.AgentMetrics
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MetricsRepository
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
    private lateinit var viewModel: MonitoringViewModel
    private lateinit var metricsFlow: MutableStateFlow<AgentMetrics>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        chatRepository = mockk()
        metricsRepository = mockk()
        
        metricsFlow = MutableStateFlow(AgentMetrics())
        every { metricsRepository.metrics } returns metricsFlow
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
        
        viewModel = MonitoringViewModel(chatRepository, metricsRepository)
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
        
        viewModel = MonitoringViewModel(chatRepository, metricsRepository)
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
}
