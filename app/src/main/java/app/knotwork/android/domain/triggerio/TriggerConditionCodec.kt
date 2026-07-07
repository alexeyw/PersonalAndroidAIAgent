package app.knotwork.android.domain.triggerio

import app.knotwork.android.domain.models.TriggerCondition
import org.json.JSONArray
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
 * The discriminator lives under [KEY_TYPE] and carries one of the `TYPE_*`
 * constants; variant payload is added alongside it. The discriminator strings
 * are owned here (not derived from an enum name) so the wire format is immune to
 * a model rename — they are persisted forever and must never be changed.
 */
object TriggerConditionCodec {

    /** Discriminator key. */
    const val KEY_TYPE: String = "type"

    /** Discriminator value for [TriggerCondition.IntervalSchedule]. */
    const val TYPE_INTERVAL: String = "SCHEDULE_INTERVAL"

    /** Discriminator value for [TriggerCondition.DailySchedule]. */
    const val TYPE_DAILY: String = "SCHEDULE_DAILY"

    /** Discriminator value for [TriggerCondition.Charging]. */
    const val TYPE_CHARGING: String = "CHARGING"

    /** Discriminator value for [TriggerCondition.NetworkConnected]. */
    const val TYPE_NETWORK: String = "NETWORK"

    /** Payload key for [TriggerCondition.IntervalSchedule.intervalMinutes]. */
    const val KEY_INTERVAL_MINUTES: String = "intervalMinutes"

    /** Payload key for [TriggerCondition.DailySchedule.hour]. */
    const val KEY_HOUR: String = "hour"

    /** Payload key for [TriggerCondition.DailySchedule.minute]. */
    const val KEY_MINUTE: String = "minute"

    /** Payload key for [TriggerCondition.NetworkConnected.wifiOnly]. */
    const val KEY_WIFI_ONLY: String = "wifiOnly"

    /** Payload key for [TriggerCondition.NetworkConnected.ssids] (a JSON string array). */
    const val KEY_SSIDS: String = "ssids"

    /**
     * Encodes [condition] into its canonical JSON string form.
     *
     * @param condition The condition to serialise.
     * @return A compact JSON string ready for column storage.
     */
    fun encode(condition: TriggerCondition): String = JSONObject().apply {
        when (condition) {
            is TriggerCondition.IntervalSchedule -> {
                put(KEY_TYPE, TYPE_INTERVAL)
                put(KEY_INTERVAL_MINUTES, condition.intervalMinutes)
            }
            is TriggerCondition.DailySchedule -> {
                put(KEY_TYPE, TYPE_DAILY)
                put(KEY_HOUR, condition.hour)
                put(KEY_MINUTE, condition.minute)
            }
            TriggerCondition.Charging -> put(KEY_TYPE, TYPE_CHARGING)
            is TriggerCondition.NetworkConnected -> {
                put(KEY_TYPE, TYPE_NETWORK)
                put(KEY_WIFI_ONLY, condition.wifiOnly)
                if (condition.ssids.isNotEmpty()) {
                    put(KEY_SSIDS, JSONArray(condition.ssids))
                }
            }
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
                TYPE_INTERVAL ->
                    if (obj.has(KEY_INTERVAL_MINUTES)) {
                        TriggerCondition.IntervalSchedule(obj.getLong(KEY_INTERVAL_MINUTES))
                    } else {
                        null
                    }
                TYPE_DAILY ->
                    if (obj.has(KEY_HOUR) && obj.has(KEY_MINUTE)) {
                        TriggerCondition.DailySchedule(obj.getInt(KEY_HOUR), obj.getInt(KEY_MINUTE))
                    } else {
                        null
                    }
                TYPE_CHARGING -> TriggerCondition.Charging
                TYPE_NETWORK -> TriggerCondition.NetworkConnected(
                    wifiOnly = obj.optBoolean(KEY_WIFI_ONLY, false),
                    // Absent for pre-SSID triggers → empty list preserves the
                    // original any-connection / any-Wi-Fi behaviour.
                    ssids = obj.optJSONArray(KEY_SSIDS).toStringList(),
                )
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

    /**
     * Reads a nullable [JSONArray] into a list of non-blank, trimmed strings.
     *
     * A `null` array (the key was absent) or any non-string / blank element
     * yields an empty / filtered list rather than throwing, keeping [decode]
     * total.
     *
     * @receiver The array to flatten, or `null`.
     * @return The trimmed, non-blank string elements in order.
     */
    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length())
            .mapNotNull { index -> optString(index, "").trim().takeIf(String::isNotEmpty) }
    }
}
