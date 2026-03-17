package ai.agent.android.data.services

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.repositories.PowerStateRepository
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages the power-saving logic for the AI Agent.
 * Listens to the [PowerStateRepository] and forcefully unloads the [LlmInferenceEngine]
 * and cancels all background tasks in [WorkManager] if the battery is low and the device
 * is not charging.
 *
 * @property scope The [CoroutineScope] used for observing the power state.
 * @property powerStateRepository The repository providing the current power state.
 * @property engine The [LlmInferenceEngine] to unload to save memory and battery.
 * @property workManager The [WorkManager] instance to cancel background jobs.
 */
class AgentPowerManager(
    private val scope: CoroutineScope,
    private val powerStateRepository: PowerStateRepository,
    private val engine: LlmInferenceEngine,
    private val workManager: WorkManager
) {

    /**
     * Starts observing the power state to enforce power-saving rules.
     */
    fun startObserving() {
        scope.launch {
            powerStateRepository.powerState.collectLatest { state ->
                Timber.d("Power state updated: isBatteryLow=${state.isBatteryLow}, isCharging=${state.isCharging}")
                
                if (state.isBatteryLow && !state.isCharging) {
                    Timber.w("Power saving mode activated! Battery is low and not charging.")
                    enforcePowerSavingMode()
                }
            }
        }
    }

    private fun enforcePowerSavingMode() {
        // Unload the LLM engine from memory to save battery
        if (engine.isInitialized) {
            Timber.i("Unloading LlmInferenceEngine due to low battery.")
            engine.unload()
        }

        // We avoid calling workManager.cancelAllWork() here because it would
        // permanently delete scheduled tasks. WorkManager constraints (like 
        // setRequiresBatteryNotLow(true)) should be used in ScheduleTaskUseCase 
        // to naturally pause execution without losing the task definitions.
        Timber.i("Power saving mode enforced. Scheduled tasks will pause if they have battery constraints.")
    }
}
