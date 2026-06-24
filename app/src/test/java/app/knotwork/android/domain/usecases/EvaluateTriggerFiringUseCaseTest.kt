package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.NetworkState
import app.knotwork.android.domain.models.PowerState
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [EvaluateTriggerFiringUseCase] — the pure fire/skip decision
 * core. Time is pinned to a fixed UTC instant so the daily-schedule and debounce
 * branches are deterministic.
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
        lastFiredAt: Long? = null,
        prompt: String = "do it",
    ): Trigger = Trigger(
        id = "t1",
        name = "T",
        condition = condition,
        pipelineId = pipelineId,
        prompt = prompt,
        enabled = enabled,
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

    // --- Interval schedule ---

    @Test
    fun `given interval never fired when evaluated then fires`() {
        val decision = eval(trigger(TriggerCondition.IntervalSchedule(30)))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given interval fired within window when evaluated then debounced`() {
        val decision = eval(trigger(TriggerCondition.IntervalSchedule(30), lastFiredAt = now - 10 * minuteMs))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.DEBOUNCED), decision)
    }

    @Test
    fun `given interval fired beyond window when evaluated then fires`() {
        val decision = eval(trigger(TriggerCondition.IntervalSchedule(30), lastFiredAt = now - 31 * minuteMs))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    // --- Charging ---

    @Test
    fun `given charging condition when not charging then condition not met`() {
        val decision = eval(trigger(TriggerCondition.Charging), power = PowerState(isCharging = false))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }

    @Test
    fun `given charging and never fired when charging then fires`() {
        val decision = eval(trigger(TriggerCondition.Charging), power = PowerState(isCharging = true))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given charging fired within event window then debounced`() {
        val decision = eval(
            trigger(TriggerCondition.Charging, lastFiredAt = now - 10 * minuteMs),
            power = PowerState(isCharging = true),
        )

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.DEBOUNCED), decision)
    }

    @Test
    fun `given charging fired beyond event window then fires`() {
        val decision = eval(
            trigger(TriggerCondition.Charging, lastFiredAt = now - 16 * minuteMs),
            power = PowerState(isCharging = true),
        )

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    // --- Network ---

    @Test
    fun `given any-network condition when disconnected then condition not met`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = false)),
            network = NetworkState(isConnected = false),
        )

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }

    @Test
    fun `given any-network condition when connected then fires`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = false)),
            network = NetworkState(isConnected = true, isWifiConnected = false),
        )

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given wifi-only condition when on cellular then condition not met`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = true)),
            network = NetworkState(isConnected = true, isWifiConnected = false),
        )

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }

    @Test
    fun `given wifi-only condition when on wifi then fires`() {
        val decision = eval(
            trigger(TriggerCondition.NetworkConnected(wifiOnly = true)),
            network = NetworkState(isConnected = true, isWifiConnected = true),
        )

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
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
    fun `given daily schedule already fired today then debounced`() {
        // Fired at 08:30 today (after today's 08:00 instant).
        val firedToday = Instant.parse("2026-06-23T08:30:00Z").toEpochMilli()
        val decision = eval(trigger(TriggerCondition.DailySchedule(hour = 8, minute = 0), lastFiredAt = firedToday))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.DEBOUNCED), decision)
    }

    @Test
    fun `given daily schedule last fired yesterday then fires again today`() {
        val firedYesterday = Instant.parse("2026-06-22T08:05:00Z").toEpochMilli()
        val decision = eval(trigger(TriggerCondition.DailySchedule(hour = 8, minute = 0), lastFiredAt = firedYesterday))

        assertEquals(TriggerFiringDecision.Fire("pipe-1", "do it"), decision)
    }

    @Test
    fun `given out-of-range daily time when evaluated then coerced without throwing`() {
        // hour 25 / minute 70 coerce to 23:59, which is after now (10:00) → not met, no exception.
        val decision = eval(trigger(TriggerCondition.DailySchedule(hour = 25, minute = 70)))

        assertEquals(TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET), decision)
    }
}
