package app.knotwork.android.presentation.ui.triggers

import app.knotwork.android.domain.models.TriggerCondition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TriggerConditionFormatter] — the pure mapping from a domain
 * [TriggerCondition] to a [TriggerConditionLabel]. Covers the hours-vs-minutes
 * threshold, the Wi-Fi/any split, the daily passthrough, and the non-positive
 * interval floor.
 */
class TriggerConditionFormatterTest {

    @Test
    fun `given a sub-hour interval when formatting then renders minutes`() {
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.IntervalSchedule(intervalMinutes = 15L))

        assertEquals(TriggerConditionLabel.IntervalMinutes(15), label)
    }

    @Test
    fun `given a thirty minute interval when formatting then renders minutes`() {
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.IntervalSchedule(intervalMinutes = 30L))

        assertEquals(TriggerConditionLabel.IntervalMinutes(30), label)
    }

    @Test
    fun `given a whole-hour interval when formatting then renders hours`() {
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.IntervalSchedule(intervalMinutes = 60L))

        assertEquals(TriggerConditionLabel.IntervalHours(1), label)
    }

    @Test
    fun `given a six-hour interval when formatting then renders hours`() {
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.IntervalSchedule(intervalMinutes = 360L))

        assertEquals(TriggerConditionLabel.IntervalHours(6), label)
    }

    @Test
    fun `given a non-round multi-hour interval when formatting then renders minutes`() {
        // 90 is not an exact multiple of 60, so it stays in minutes rather than "1.5 hours".
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.IntervalSchedule(intervalMinutes = 90L))

        assertEquals(TriggerConditionLabel.IntervalMinutes(90), label)
    }

    @Test
    fun `given a non-positive interval when formatting then floors to one minute`() {
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.IntervalSchedule(intervalMinutes = 0L))

        assertEquals(TriggerConditionLabel.IntervalMinutes(1), label)
    }

    @Test
    fun `given a daily schedule when formatting then passes through hour and minute`() {
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.DailySchedule(hour = 8, minute = 5))

        assertEquals(TriggerConditionLabel.Daily(hour = 8, minute = 5), label)
    }

    @Test
    fun `given charging when formatting then maps to charging label`() {
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.Charging)

        assertEquals(TriggerConditionLabel.Charging, label)
    }

    @Test
    fun `given a wifi-only network condition when formatting then maps to wifi label`() {
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.NetworkConnected(wifiOnly = true))

        assertEquals(TriggerConditionLabel.NetworkWifi, label)
    }

    @Test
    fun `given an any-network condition when formatting then maps to any label`() {
        val label = TriggerConditionFormatter.toLabel(TriggerCondition.NetworkConnected(wifiOnly = false))

        assertEquals(TriggerConditionLabel.NetworkAny, label)
    }

    @Test
    fun `given an ssid-scoped network condition when formatting then maps to named label`() {
        val label = TriggerConditionFormatter.toLabel(
            TriggerCondition.NetworkConnected(wifiOnly = true, ssids = listOf("Home", "Office")),
        )

        assertEquals(TriggerConditionLabel.NetworkNamed(listOf("Home", "Office")), label)
    }
}
