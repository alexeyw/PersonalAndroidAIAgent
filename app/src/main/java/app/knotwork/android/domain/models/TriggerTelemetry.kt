package app.knotwork.android.domain.models

/**
 * Stable telemetry key for a [TriggerCondition] kind, used to tally background
 * trigger firings by type in the local usage statistics
 * ([app.knotwork.android.domain.repositories.UsageTelemetryRepository]).
 *
 * The key is **persisted** as a counter row key, so the returned strings are
 * durable and must never be renamed (doing so would silently split a tally
 * across the old and new key). It is deliberately coarse — the firing **kind**,
 * not the parameters — so the statistic reveals usage shape without retaining
 * anything sensitive (a schedule time, a Wi-Fi-only flag).
 */
val TriggerCondition.telemetryKind: String
    get() = when (this) {
        is TriggerCondition.IntervalSchedule -> "INTERVAL"
        is TriggerCondition.DailySchedule -> "DAILY"
        TriggerCondition.Charging -> "CHARGING"
        is TriggerCondition.NetworkConnected -> "NETWORK"
    }
