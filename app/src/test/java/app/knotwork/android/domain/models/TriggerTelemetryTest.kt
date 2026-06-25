package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the stable [telemetryKind] strings for every [TriggerCondition] variant. */
class TriggerTelemetryTest {

    @Test
    fun `given each trigger condition when telemetryKind read then it returns the stable key`() {
        assertEquals("INTERVAL", TriggerCondition.IntervalSchedule(intervalMinutes = 30).telemetryKind)
        assertEquals("DAILY", TriggerCondition.DailySchedule(hour = 8, minute = 0).telemetryKind)
        assertEquals("CHARGING", TriggerCondition.Charging.telemetryKind)
        assertEquals("NETWORK", TriggerCondition.NetworkConnected(wifiOnly = true).telemetryKind)
    }
}
