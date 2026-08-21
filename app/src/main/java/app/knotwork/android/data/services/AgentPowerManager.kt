package app.knotwork.android.data.services

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.repositories.PowerStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages the power-saving logic for the AI Agent.
 * Listens to the [PowerStateRepository] and forcefully unloads the [LlmInferenceEngine]
 * if the battery is low and the device is not charging.
 *
 * It deliberately does **not** touch scheduled background work — see
 * [enforcePowerSavingMode] for why cancelling it would be destructive rather
 * than thrifty.
 *
 * @property scope The [CoroutineScope] used for observing the power state.
 * @property powerStateRepository The repository providing the current power state.
 * @property engine The [LlmInferenceEngine] to unload to save memory and battery.
 */
class AgentPowerManager(
    private val scope: CoroutineScope,
    private val powerStateRepository: PowerStateRepository,
    private val engine: LlmInferenceEngine,
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

    private suspend fun enforcePowerSavingMode() {
        // Unload the LLM engine from memory to save battery. [unload] serialises
        // on the engine's native-session mutex, so an in-flight generation is
        // allowed to finish before the model is freed.
        if (engine.isInitialized) {
            Timber.i("Unloading LlmInferenceEngine due to low battery.")
            engine.unload()
        }

        // Scheduled background work is deliberately left alone. Cancelling it
        // (`WorkManager.cancelAllWork()`) would permanently delete the user's
        // scheduled tasks rather than defer them; WorkManager constraints such as
        // `setRequiresBatteryNotLow(true)`, applied in `ScheduleTaskUseCase`, are
        // what pause execution without losing the task definitions. That is why
        // this class holds no `WorkManager` reference at all.
        Timber.i("Power saving mode enforced. Scheduled tasks will pause if they have battery constraints.")
    }
}
