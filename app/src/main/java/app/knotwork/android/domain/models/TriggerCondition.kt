package app.knotwork.android.domain.models

/**
 * The activation condition of a [Trigger] — the "when" half of a
 * `condition → bound pipeline` automation rule.
 *
 * Modelled as a sealed hierarchy so each first-wave trigger type carries only
 * the parameters it needs, while the evaluation core
 * ([app.knotwork.android.domain.usecases.EvaluateTriggerFiringUseCase]) can
 * switch over the variants exhaustively. The first wave is deliberately limited
 * to **low-sensitivity** conditions (time, charging, network) that need no
 * dangerous Android permission; notification-listener / geofence / SMS triggers
 * are deferred — a design boundary, not an omission (see `SECURITY.md`).
 *
 * Conditions are persisted through
 * [app.knotwork.android.domain.triggerio.TriggerConditionCodec] as a compact
 * JSON string, so the wire keys there are durable and must never be renamed.
 *
 * Conditions split into two firing models:
 * - **Time-scheduled** ([IntervalSchedule], [DailySchedule]) fire on a clock —
 *   the background runtime wakes at a cadence and the evaluator decides from the
 *   clock alone.
 * - **Event** ([Charging], [NetworkConnected], [isEventTriggered] `== true`)
 *   fire on a device-state *edge* (false → true). The runtime polls the live
 *   state and the evaluator fires once per transition, tracked by
 *   [Trigger.armed], not by a time window.
 */
sealed interface TriggerCondition {

    /**
     * Whether this condition fires on a device-state edge (charging started,
     * network connected) rather than on a clock. Event conditions are evaluated
     * against the live [PowerState] / [NetworkState] and use [Trigger.armed] to
     * fire exactly once per transition into the satisfied state.
     */
    val isEventTriggered: Boolean

    /**
     * Fires repeatedly every [intervalMinutes] minutes (e.g. "every 6 hours").
     *
     * The background runtime ([app.knotwork.android.domain.services.TriggerScheduler])
     * clamps the effective interval to the platform periodic-work floor
     * (15 minutes); a smaller value is accepted at the domain layer but will run
     * no more often than the floor allows.
     *
     * @property intervalMinutes The repeat interval in minutes. Must be positive;
     *   a non-positive stored value is clamped to a 1-minute floor by the
     *   evaluator so it can never collapse the debounce window.
     */
    data class IntervalSchedule(val intervalMinutes: Long) : TriggerCondition {
        override val isEventTriggered: Boolean get() = false
    }

    /**
     * Fires once per day at the device-local [hour]:[minute] (e.g. "every day at
     * 08:00").
     *
     * @property hour Hour of day in the 24-hour clock, `0..23`.
     * @property minute Minute of the hour, `0..59`.
     */
    data class DailySchedule(val hour: Int, val minute: Int) : TriggerCondition {
        override val isEventTriggered: Boolean get() = false
    }

    /**
     * Fires when the device transitions into the charging state.
     *
     * The runtime polls the live charging state (it does **not** gate the wakeup
     * on a charging constraint, so it can also observe the un-plug that re-arms
     * the trigger); the evaluator fires once on the false → true edge and re-arms
     * when charging stops.
     */
    data object Charging : TriggerCondition {
        override val isEventTriggered: Boolean get() = true
    }

    /**
     * Fires when the device transitions into a connected-network state.
     *
     * @property wifiOnly When `true`, the condition is satisfied only by a Wi-Fi
     *   connection ([NetworkState.isWifiConnected]); when `false`, any connected
     *   network ([NetworkState.isConnected]) satisfies it. The gate and the live
     *   re-check use this same predicate, so they cannot disagree. Ignored when
     *   [ssids] is non-empty (SSID matching is inherently Wi-Fi-only).
     * @property ssids When non-empty, the condition additionally requires the
     *   device to be connected to a Wi-Fi network whose name (SSID) exactly
     *   matches one of these entries — so an automation can fire only on, say,
     *   "home" or "office" Wi-Fi rather than on any connection. An empty list
     *   (the default) keeps the original any-connection / any-Wi-Fi behaviour.
     *   Reading the current SSID needs a runtime location permission; when it is
     *   not granted the live SSID reads back as `null` and an SSID-scoped trigger
     *   simply never matches (fail-safe).
     */
    data class NetworkConnected(val wifiOnly: Boolean, val ssids: List<String> = emptyList()) : TriggerCondition {
        override val isEventTriggered: Boolean get() = true
    }
}
