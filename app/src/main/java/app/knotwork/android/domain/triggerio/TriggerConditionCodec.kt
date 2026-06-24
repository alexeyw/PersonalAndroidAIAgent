package app.knotwork.android.domain.triggerio

import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.models.TriggerType
import org.json.JSONException
import org.json.JSONObject

/**
 * Single source of truth for the [TriggerCondition] ↔ JSON wire shape.
 *
 * A trigger's condition is persisted as a compact JSON string in the
 * `triggers.conditionJson` column; this object is the only place that encoding
 * is produced or parsed, so the on-disk shape can never drift between the write
 * and read paths.
 *
 * The discriminator lives under [KEY_TYPE] and carries the [TriggerType]
 * [name][Enum.name]; variant payload is added alongside it. The wire keys here
 * are persisted forever — renaming one would orphan every existing trigger row.
 */
object TriggerConditionCodec {

    /** Discriminator key carrying the [TriggerType] name. */
    const val KEY_TYPE: String = "type"

    /** Payload key for [TriggerCondition.IntervalSchedule.intervalMinutes]. */
    const val KEY_INTERVAL_MINUTES: String = "intervalMinutes"

    /** Payload key for [TriggerCondition.DailySchedule.hour]. */
    const val KEY_HOUR: String = "hour"

    /** Payload key for [TriggerCondition.DailySchedule.minute]. */
    const val KEY_MINUTE: String = "minute"

    /** Payload key for [TriggerCondition.NetworkConnected.wifiOnly]. */
    const val KEY_WIFI_ONLY: String = "wifiOnly"

    /**
     * Encodes [condition] into its canonical JSON string form.
     *
     * @param condition The condition to serialise.
     * @return A compact JSON string ready for column storage.
     */
    fun encode(condition: TriggerCondition): String = JSONObject().apply {
        put(KEY_TYPE, condition.type.name)
        when (condition) {
            is TriggerCondition.IntervalSchedule -> put(KEY_INTERVAL_MINUTES, condition.intervalMinutes)
            is TriggerCondition.DailySchedule -> {
                put(KEY_HOUR, condition.hour)
                put(KEY_MINUTE, condition.minute)
            }
            TriggerCondition.Charging -> Unit
            is TriggerCondition.NetworkConnected -> put(KEY_WIFI_ONLY, condition.wifiOnly)
        }
    }.toString()

    /**
     * Decodes a string produced by [encode] back into a [TriggerCondition].
     *
     * Total by design: a `null`/blank input, malformed JSON, an unrecognised
     * [KEY_TYPE], or a missing required payload field all resolve to `null`
     * rather than throwing. A `null` result means "this row is unusable"; the
     * repository skips such rows on read so one corrupt trigger never aborts a
     * whole load.
     *
     * @param json The stored JSON string, or `null`.
     * @return The parsed condition, or `null` on any unrecognised / malformed input.
     */
    fun decode(json: String?): TriggerCondition? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            when (obj.optString(KEY_TYPE)) {
                TriggerType.SCHEDULE_INTERVAL.name ->
                    if (obj.has(KEY_INTERVAL_MINUTES)) {
                        TriggerCondition.IntervalSchedule(obj.getLong(KEY_INTERVAL_MINUTES))
                    } else {
                        null
                    }
                TriggerType.SCHEDULE_DAILY.name ->
                    if (obj.has(KEY_HOUR) && obj.has(KEY_MINUTE)) {
                        TriggerCondition.DailySchedule(obj.getInt(KEY_HOUR), obj.getInt(KEY_MINUTE))
                    } else {
                        null
                    }
                TriggerType.CHARGING.name -> TriggerCondition.Charging
                TriggerType.NETWORK.name -> TriggerCondition.NetworkConnected(obj.optBoolean(KEY_WIFI_ONLY, false))
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }
}
