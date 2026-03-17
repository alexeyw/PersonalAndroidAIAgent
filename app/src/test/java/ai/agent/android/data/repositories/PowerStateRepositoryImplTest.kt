package ai.agent.android.data.repositories

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PowerStateRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var repository: PowerStateRepositoryImpl
    private val receiverSlot = slot<BroadcastReceiver>()

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        
        val initialIntent = mockk<Intent>(relaxed = true)
        every { initialIntent.getIntExtra(any(), any()) } returns 2
        
        // Use an answers block to return different mocks depending on arguments
        every { context.registerReceiver(any(), any<IntentFilter>()) } answers {
            val receiver = firstArg<BroadcastReceiver?>()
            if (receiver == null) {
                initialIntent
            } else {
                receiverSlot.captured = receiver
                mockk<Intent>(relaxed = true)
            }
        }
        
        // Also handle the specific case where receiver is explicitly null in mockk
        every { context.registerReceiver(isNull(), any<IntentFilter>()) } returns initialIntent

        repository = PowerStateRepositoryImpl(context)
    }

    @Test
    fun `initial state is correct`() {
        val state = repository.powerState.value
        assertEquals(false, state.isBatteryLow)
        assertEquals(true, state.isCharging)
    }

    @Test
    fun `ACTION_BATTERY_LOW updates state`() {
        val receiver = receiverSlot.captured
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_BATTERY_LOW
        
        receiver.onReceive(context, intent)
        
        val state = repository.powerState.value
        assertEquals(true, state.isBatteryLow)
    }

    @Test
    fun `ACTION_BATTERY_OKAY updates state`() {
        val receiver = receiverSlot.captured
        
        // First set to low
        val intentLow = mockk<Intent>()
        every { intentLow.action } returns Intent.ACTION_BATTERY_LOW
        receiver.onReceive(context, intentLow)
        assertEquals(true, repository.powerState.value.isBatteryLow)

        // Then set back to okay
        val intentOkay = mockk<Intent>()
        every { intentOkay.action } returns Intent.ACTION_BATTERY_OKAY
        receiver.onReceive(context, intentOkay)
        assertEquals(false, repository.powerState.value.isBatteryLow)
    }

    @Test
    fun `ACTION_POWER_DISCONNECTED updates state`() {
        val receiver = receiverSlot.captured
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_POWER_DISCONNECTED
        
        receiver.onReceive(context, intent)
        
        val state = repository.powerState.value
        assertEquals(false, state.isCharging)
    }

    @Test
    fun `ACTION_POWER_CONNECTED updates state`() {
        val receiver = receiverSlot.captured
        
        // First disconnect
        val intentDisconnect = mockk<Intent>()
        every { intentDisconnect.action } returns Intent.ACTION_POWER_DISCONNECTED
        receiver.onReceive(context, intentDisconnect)
        assertEquals(false, repository.powerState.value.isCharging)

        // Then connect
        val intentConnect = mockk<Intent>()
        every { intentConnect.action } returns Intent.ACTION_POWER_CONNECTED
        receiver.onReceive(context, intentConnect)
        assertEquals(true, repository.powerState.value.isCharging)
    }
}