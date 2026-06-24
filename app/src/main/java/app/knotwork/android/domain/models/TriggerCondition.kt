package app.knotwork.android.domain.models

/**
 * Coarse classification of a [TriggerCondition], used for persistence
 * discrimination, list grouping and icon selection.
 *
 * Derived from the condition (see [TriggerCondition.type]) rather than stored
 * independently, so the discriminator can never drift from the concrete
 * condition payload.
 */
enum class TriggerType {
    /** A repeating interval schedule ([TriggerCondition.IntervalSchedule]). */
    SCHEDULE_INTERVAL,

    /** A once-per-day time-of-day schedule ([TriggerCondition.DailySchedule]). */
    SCHEDULE_DAILY,

    /** A charging-state trigger ([TriggerCondition.Charging]). */
    CHARGING,

    /** A network-connectivity trigger ([TriggerCondition.NetworkConnected]). */
    NETWORK,
}

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
 * are deferred (see `project_docs/decisions.md`).
 *
 * Conditions are persisted through
 * [app.knotwork.android.domain.triggerio.TriggerConditionCodec] as a compact
 * JSON string, so the wire keys there are durable and must never be renamed.
 */
sealed interface TriggerCondition {

    /**
     * The coarse [TriggerType] of this condition, used for persistence and UI
     * classification. Computed from the concrete variant so it stays in lock-step
     * with the payload.
     */
    val type: TriggerType

    /**
     * Fires repeatedly every [intervalMinutes] minutes (e.g. "every 6 hours").
     *
     * The background runtime ([app.knotwork.android.domain.services.TriggerScheduler])
     * clamps the effective interval to the platform periodic-work floor
     * (15 minutes); a smaller value is accepted at the domain layer but will run
     * no more often than the floor allows.
     *
     * @property intervalMinutes The repeat interval in minutes. Must be positive.
     */
    data class IntervalSchedule(val intervalMinutes: Long) : TriggerCondition {
        override val type: TriggerType get() = TriggerType.SCHEDULE_INTERVAL
    }

    /**
     * Fires once per day at the device-local [hour]:[minute] (e.g. "every day at
     * 08:00").
     *
     * @property hour Hour of day in the 24-hour clock, `0..23`.
     * @property minute Minute of the hour, `0..59`.
     */
    data class DailySchedule(val hour: Int, val minute: Int) : TriggerCondition {
        override val type: TriggerType get() = TriggerType.SCHEDULE_DAILY
    }

    /**
     * Fires when the device starts charging.
     *
     * The background runtime only wakes the trigger while the charging
     * constraint is satisfied; the evaluation core re-checks the live charging
     * state and applies a re-arm debounce so a single charge session enqueues at
     * most one run within the debounce window.
     */
    data object Charging : TriggerCondition {
        override val type: TriggerType get() = TriggerType.CHARGING
    }

    /**
     * Fires when the device gains network connectivity.
     *
     * @property wifiOnly When `true`, the condition is satisfied only by a Wi-Fi
     *   (unmetered) connection; when `false`, any connected network satisfies it.
     *   Matching a specific Wi-Fi SSID is intentionally **not** modelled in the
     *   first wave — reading the SSID requires a location permission, which is
     *   deferred (see `project_docs/decisions.md`).
     */
    data class NetworkConnected(val wifiOnly: Boolean) : TriggerCondition {
        override val type: TriggerType get() = TriggerType.NETWORK
    }
}
