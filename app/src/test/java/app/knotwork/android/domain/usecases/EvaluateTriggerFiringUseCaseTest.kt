package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.NetworkState
import app.knotwork.android.domain.models.PowerState
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.models.TriggerSkipReason
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [EvaluateTriggerFiringUseCase] — the pure fire/skip/re-arm
 * decision core. Time is pinned to a fixed UTC instant so the daily-schedule and
 * interval-debounce branches are deterministic.
 */
class EvaluateTriggerFiringUseCaseTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-23T10:00:00Z").toEpochMilli()
    private val evaluate = EvaluateTriggerFiringUseCase()

    private val minuteMs = 60_000L

    private fun trigger(
        condition: TriggerCondition,
        enabled: Boolean = true,
        pipelineId: String? = "pipe-1",
        armed: Boolean = true,
        lastFiredAt: Long? = null,
        prompt: String = "do it",
    ): Trigger = Trigger(
        id = "t1",
        name = "T",
        condition = condition,
        pipelineId = pipelineId,
        prompt = prompt,
        enabled = enabled,
        armed = armed,
        createdAt = 0L,
        lastFiredAt = lastFiredAt,
    )

    private fun eval(
        trigger: Trigger,
        power: PowerState = PowerState(),
        network: NetworkState = NetworkState(),
    ): TriggerFiringDecision = evaluate(trigger, power, network, now, zone)

    @Test
    fun `given disabled trigger when evaluated then skipped as disabled`() {
        val decision = eval(trigger(TriggerCondition.Charging, enabled = false))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.DISABLED), decision)
    }

    @Test
    fun `given unbound trigger when evaluated then skipped as unbound`() {
        val decision = eval(trigger(TriggerCondition.Charging, pipelineId = null))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.UNBOUND), decision)
    }

    @Test
    fun `given fire decision then it carries the bound pipeline and prompt`() {
        val decision = eval(trigger(TriggerCondition.IntervalSchedule(30), prompt = "summarize inbox"))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "summarize inbox"), decision)
    }

    // --- Interval schedule (debounce = half the interval) ---

    @Test
    fun `given interval never fired when evaluated then fires`() {
        val decision = eval(trigger(TriggerCondition.IntervalSchedule(30)))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given interval fired within half the interval when evaluated then already-fired skip`() {
        // interval 30min => debounce 15min; fired 10min ago => within window.
        val decision = eval(trigger(TriggerCondition.IntervalSchedule(30), lastFiredAt = now - 10 * minuteMs))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.ALREADY_FIRED), decision)
    }

    @Test
    fun `given interval fired beyond half the interval when evaluated then fires`() {
        // interval 30min => debounce 15min; fired 16min ago => past window, so an
        // early/jittered wake still fires (no halving of the effective rate).
        val decision = eval(trigger(TriggerCondition.IntervalSchedule(30), lastFiredAt = now - 16 * minuteMs))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given zero interval when fired seconds ago then floor prevents firing every poll`() {
        // intervalMinutes=0 is clamped to a 1-minute floor => debounce 30s.
        // A fire 1s ago must still be debounced (without the floor the window
        // would collapse to 0 and fire on every poll).
        val decision = eval(trigger(TriggerCondition.IntervalSchedule(0), lastFiredAt = now - 1_000L))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.ALREADY_FIRED), decision)
    }

    // --- Charging (edge-triggered via armed) ---

    @Test
    fun `given charging armed and charging when evaluated then fires`() {
        val decision = eval(trigger(TriggerCondition.Charging, armed = true), power = PowerState(isCharging = true))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given charging armed but not charging then condition not met`() {
        val decision = eval(trigger(TriggerCondition.Charging, armed = true), power = PowerState(isCharging = false))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }

    @Test
    fun `given charging disarmed and still charging then already fired (no repeat)`() {
        val decision = eval(trigger(TriggerCondition.Charging, armed = false), power = PowerState(isCharging = true))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.ALREADY_FIRED), decision)
    }

    @Test
    fun `given charging disarmed and charging stopped then re-arm`() {
        val decision = eval(trigger(TriggerCondition.Charging, armed = false), power = PowerState(isCharging = false))

        assertEquals(TriggerFiringDecision.ReArm, decision)
    }

    // --- Network (edge-triggered via armed) ---

    @Test
    fun `given any-network armed and connected then fires`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = false), armed = true),
            network = NetworkState(isConnected = true, isWifiConnected = false),
        )

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given any-network armed but disconnected then condition not met`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = false), armed = true),
            network = NetworkState(isConnected = false),
        )

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }

    @Test
    fun `given wifi-only armed and on wifi then fires`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = true), armed = true),
            network = NetworkState(isConnected = true, isWifiConnected = true),
        )

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given wifi-only armed but on cellular then condition not met`() {
        // Cellular (connected, not wifi) must NOT satisfy a wifi-only trigger —
        // gate and live-check use the same isWifiConnected predicate.
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = true), armed = true),
            network = NetworkState(isConnected = true, isWifiConnected = false),
        )

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }

    @Test
    fun `given network disarmed and disconnected then re-arm`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = false), armed = false),
            network = NetworkState(isConnected = false),
        )

        assertEquals(TriggerFiringDecision.ReArm, decision)
    }

    @Test
    fun `given ssid-scoped armed and on a matching wifi then fires`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = true, ssids = listOf("Home", "Office")), armed = true),
            network = NetworkState(isConnected = true, isWifiConnected = true, wifiSsid = "Office"),
        )

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given ssid-scoped armed and wifi name differs only by case then fires`() {
        // The editor de-dups SSIDs case-insensitively, so matching must be too —
        // a "home" entry has to fire on a "HOME" network.
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = true, ssids = listOf("home")), armed = true),
            network = NetworkState(isConnected = true, isWifiConnected = true, wifiSsid = "HOME"),
        )

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given ssid-scoped armed but on a non-matching wifi then condition not met`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = true, ssids = listOf("Home")), armed = true),
            network = NetworkState(isConnected = true, isWifiConnected = true, wifiSsid = "Cafe"),
        )

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }

    @Test
    fun `given ssid-scoped armed but ssid unreadable then condition not met`() {
        // No location permission → SSID reads back null → an SSID-scoped trigger
        // must never match (fail-safe), even though Wi-Fi is connected.
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = true, ssids = listOf("Home")), armed = true),
            network = NetworkState(isConnected = true, isWifiConnected = true, wifiSsid = null),
        )

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }

    // --- Daily schedule ---

    @Test
    fun `given daily schedule before the time today then condition not met`() {
        // now = 10:00 UTC, scheduled 12:00 today is still ahead.
        val decision = eval(trigger(TriggerCondition.DailySchedule(hour = 12, minute = 0)))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }

    @Test
    fun `given daily schedule after the time and not fired today then fires`() {
        // now = 10:00 UTC, scheduled 08:00 today has passed.
        val decision = eval(trigger(TriggerCondition.DailySchedule(hour = 8, minute = 0)))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given daily schedule already fired today then already fired skip`() {
        val firedToday = Instant.parse("2026-06-23T08:30:00Z").toEpochMilli()
        val decision = eval(trigger(TriggerCondition.DailySchedule(hour = 8, minute = 0), lastFiredAt = firedToday))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.ALREADY_FIRED), decision)
    }

    @Test
    fun `given daily schedule last fired yesterday then fires again today`() {
        val firedYesterday = Instant.parse("2026-06-22T08:05:00Z").toEpochMilli()
        val decision = eval(trigger(TriggerCondition.DailySchedule(hour = 8, minute = 0), lastFiredAt = firedYesterday))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given out-of-range daily time when evaluated then coerced without throwing`() {
        // hour 25 / minute 70 coerce to 23:59, which is after now (10:00) => not met, no exception.
        val decision = eval(trigger(TriggerCondition.DailySchedule(hour = 25, minute = 70)))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }
}
