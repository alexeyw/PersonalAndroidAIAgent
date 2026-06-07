package app.knotwork.android.data.services

import androidx.work.WorkManager
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.PowerState
import app.knotwork.android.domain.repositories.PowerStateRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `when battery is low and not charging, should unload engine`() = runTest {
        val powerManager = AgentPowerManager(
            scope = backgroundScope,
            powerStateRepository = powerStateRepository,
            engine = engine,
            workManager = workManager,
        )

        every { engine.isInitialized } returns true

        powerManager.startObserving()
        kotlinx.coroutines.yield()

        // Trigger the condition
        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = false)
        kotlinx.coroutines.yield()

        verify(exactly = 1) { engine.unload() }
        verify(exactly = 0) { workManager.cancelAllWork() }
    }

    @Test
    fun `when battery is low but charging, should NOT unload engine`() = runTest {
        val powerManager = AgentPowerManager(
            scope = backgroundScope,
            powerStateRepository = powerStateRepository,
            engine = engine,
            workManager = workManager,
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
    fun `when low-battery state then charging transition occurs, should unload only once`() = runTest {
        val powerManager = AgentPowerManager(
            scope = backgroundScope,
            powerStateRepository = powerStateRepository,
            engine = engine,
            workManager = workManager,
        )

        every { engine.isInitialized } returns true

        powerManager.startObserving()
        kotlinx.coroutines.yield()

        // First trigger: low battery + not charging.
        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = false)
        kotlinx.coroutines.yield()
        // Charging starts; `isBatteryLow && !isCharging` is false so the policy
        // branch is skipped. `engine.unload()` must NOT be called a second time.
        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = true)
        kotlinx.coroutines.yield()

        verify(exactly = 1) { engine.unload() }
        verify(exactly = 0) { workManager.cancelAllWork() }
    }

    @Test
    fun `when same low-battery state is re-emitted, StateFlow dedupes and unload still fires only once`() = runTest {
        val powerManager = AgentPowerManager(
            scope = backgroundScope,
            powerStateRepository = powerStateRepository,
            engine = engine,
            workManager = workManager,
        )

        every { engine.isInitialized } returns true

        powerManager.startObserving()
        kotlinx.coroutines.yield()

        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = false)
        kotlinx.coroutines.yield()
        // Identical value — StateFlow filters duplicates by `equals`, so the collector
        // does not re-run. Guards against accidental policy re-triggers.
        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = false)
        kotlinx.coroutines.yield()

        verify(exactly = 1) { engine.unload() }
    }

    @Test
    fun `given low battery but engine never initialized then unload is never invoked`() = runTest {
        val powerManager = AgentPowerManager(
            scope = backgroundScope,
            powerStateRepository = powerStateRepository,
            engine = engine,
            workManager = workManager,
        )

        every { engine.isInitialized } returns false

        powerManager.startObserving()
        kotlinx.coroutines.yield()
        powerStateFlow.value = PowerState(isBatteryLow = true, isCharging = false)
        kotlinx.coroutines.yield()

        verify(exactly = 0) { engine.unload() }
    }

    @Test
    fun `when battery is ok and not charging, should NOT unload engine`() = runTest {
        val powerManager = AgentPowerManager(
            scope = backgroundScope,
            powerStateRepository = powerStateRepository,
            engine = engine,
            workManager = workManager,
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
