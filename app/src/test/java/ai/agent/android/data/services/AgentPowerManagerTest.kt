package ai.agent.android.data.services

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.PowerState
import ai.agent.android.domain.repositories.PowerStateRepository
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentPowerManagerTest {

    private lateinit var powerStateRepository: PowerStateRepository
    private lateinit var engine: LlmInferenceEngine
    private lateinit var workManager: WorkManager
    private lateinit var powerStateFlow: MutableStateFlow<PowerState>

    @Before
    fun setup() {
        powerStateRepository = mockk()
        engine = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        
        powerStateFlow = MutableStateFlow(PowerState(isBatteryLow = false, isCharging = true))
        every { powerStateRepository.powerState } returns powerStateFlow
    }

    @Test
    fun `when battery is low and not charging, should unload engine and cancel work`() = runTest {
        val powerManager = AgentPowerManager(
            scope = backgroundScope,
            powerStateRepository = powerStateRepository,
            engine = engine,
            workManager = workManager
        )
        
        every { engine.isInitialized } returns true

        powerManager.startObserving()
        kotlinx.coroutines.yield()

        // Trigger the condition
        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = false)
        kotlinx.coroutines.yield()

        verify(exactly = 1) { engine.unload() }
        verify(exactly = 1) { workManager.cancelAllWork() }
    }

    @Test
    fun `when battery is low but charging, should NOT unload engine and cancel work`() = runTest {
        val powerManager = AgentPowerManager(
            scope = backgroundScope,
            powerStateRepository = powerStateRepository,
            engine = engine,
            workManager = workManager
        )
        
        every { engine.isInitialized } returns true

        powerManager.startObserving()
        advanceUntilIdle()

        // Battery low but charging
        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = true)
        advanceUntilIdle()

        verify(exactly = 0) { engine.unload() }
        verify(exactly = 0) { workManager.cancelAllWork() }
    }

    @Test
    fun `when battery is ok and not charging, should NOT unload engine and cancel work`() = runTest {
        val powerManager = AgentPowerManager(
            scope = backgroundScope,
            powerStateRepository = powerStateRepository,
            engine = engine,
            workManager = workManager
        )
        
        every { engine.isInitialized } returns true

        powerManager.startObserving()
        advanceUntilIdle()

        // Battery OK and not charging
        powerStateFlow.value = PowerState(isBatteryLow = false, isCharging = false)
        advanceUntilIdle()

        verify(exactly = 0) { engine.unload() }
        verify(exactly = 0) { workManager.cancelAllWork() }
    }
}
