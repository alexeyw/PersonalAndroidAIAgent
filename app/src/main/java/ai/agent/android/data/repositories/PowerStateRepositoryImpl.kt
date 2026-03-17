package ai.agent.android.data.repositories

import ai.agent.android.domain.models.PowerState
import ai.agent.android.domain.repositories.PowerStateRepository
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [PowerStateRepository] that listens to system broadcast intents
 * to determine the current battery and charging status.
 *
 * @property context The application context used to register broadcast receivers.
 */
@Singleton
class PowerStateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PowerStateRepository {

    private val _powerState = MutableStateFlow(getInitialPowerState())
    override val powerState: StateFlow<PowerState> = _powerState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                when (action) {
                    Intent.ACTION_BATTERY_LOW -> {
                        _powerState.update { it.copy(isBatteryLow = true) }
                    }
                    Intent.ACTION_BATTERY_OKAY -> {
                        _powerState.update { it.copy(isBatteryLow = false) }
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        _powerState.update { it.copy(isCharging = true) }
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        _powerState.update { it.copy(isCharging = false) }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
    }

    /**
     * Reads the current sticky intent to determine the initial battery and charging state.
     */
    private fun getInitialPowerState(): PowerState {
        // Since we are mocking context, registerReceiver might return null in tests
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)

        if (batteryStatus == null) {
            return PowerState(isBatteryLow = false, isCharging = true)
        }

        val status: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                  status == BatteryManager.BATTERY_STATUS_FULL

        val level: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        
        val batteryPct = if (level != -1 && scale != -1) {
            level * 100 / scale.toFloat()
        } else {
            100f
        }
        
        // Android typically broadcasts ACTION_BATTERY_LOW at 15%
        val isBatteryLow = batteryPct <= 15f

        return PowerState(
            isBatteryLow = isBatteryLow,
            isCharging = isCharging
        )
    }
}
