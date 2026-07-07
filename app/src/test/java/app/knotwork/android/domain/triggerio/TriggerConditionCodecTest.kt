package app.knotwork.android.domain.triggerio

import app.knotwork.android.domain.models.TriggerCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [TriggerConditionCodec] — the single source of truth for the
 * `TriggerCondition` ↔ JSON wire shape persisted in `triggers.conditionJson`.
 */
class TriggerConditionCodecTest {

    @Test
    fun `given interval schedule when round-tripped then preserved`() {
        val condition = TriggerCondition.IntervalSchedule(intervalMinutes = 90)

        val decoded = TriggerConditionCodec.decode(TriggerConditionCodec.encode(condition))

        assertEquals(condition, decoded)
    }

    @Test
    fun `given daily schedule when round-tripped then preserved`() {
        val condition = TriggerCondition.DailySchedule(hour = 8, minute = 30)

        val decoded = TriggerConditionCodec.decode(TriggerConditionCodec.encode(condition))

        assertEquals(condition, decoded)
    }

    @Test
    fun `given charging when round-tripped then preserved`() {
        val decoded = TriggerConditionCodec.decode(TriggerConditionCodec.encode(TriggerCondition.Charging))

        assertEquals(TriggerCondition.Charging, decoded)
    }

    @Test
    fun `given wifi-only network when round-tripped then preserved`() {
        val condition = TriggerCondition.NetworkConnected(wifiOnly = true)

        val decoded = TriggerConditionCodec.decode(TriggerConditionCodec.encode(condition))

        assertEquals(condition, decoded)
    }

    @Test
    fun `given any-network condition when round-tripped then preserved`() {
        val condition = TriggerCondition.NetworkConnected(wifiOnly = false)

        val decoded = TriggerConditionCodec.decode(TriggerConditionCodec.encode(condition))

        assertEquals(condition, decoded)
    }

    @Test
    fun `given null input when decoded then null`() {
        assertNull(TriggerConditionCodec.decode(null))
    }

    @Test
    fun `given blank input when decoded then null`() {
        assertNull(TriggerConditionCodec.decode("   "))
    }

    @Test
    fun `given malformed json when decoded then null`() {
        assertNull(TriggerConditionCodec.decode("{not json"))
    }

    @Test
    fun `given unknown type when decoded then null`() {
        assertNull(TriggerConditionCodec.decode("""{"type":"GEOFENCE"}"""))
    }

    @Test
    fun `given interval type without payload when decoded then null`() {
        assertNull(TriggerConditionCodec.decode("""{"type":"SCHEDULE_INTERVAL"}"""))
    }

    @Test
    fun `given daily type missing minute when decoded then null`() {
        assertNull(TriggerConditionCodec.decode("""{"type":"SCHEDULE_DAILY","hour":8}"""))
    }

    @Test
    fun `given network type without wifi flag when decoded then defaults to any network`() {
        val decoded = TriggerConditionCodec.decode("""{"type":"NETWORK"}""")

        assertEquals(TriggerCondition.NetworkConnected(wifiOnly = false), decoded)
    }

    @Test
    fun `given ssid-scoped network when round-tripped then names preserved`() {
        val condition = TriggerCondition.NetworkConnected(wifiOnly = true, ssids = listOf("Home", "Office"))

        val decoded = TriggerConditionCodec.decode(TriggerConditionCodec.encode(condition))

        assertEquals(condition, decoded)
    }

    @Test
    fun `given legacy network json without ssids when decoded then ssids default to empty`() {
        val decoded = TriggerConditionCodec.decode("""{"type":"NETWORK","wifiOnly":true}""")

        assertEquals(TriggerCondition.NetworkConnected(wifiOnly = true, ssids = emptyList()), decoded)
    }

    @Test
    fun `given ssids array with blanks when decoded then blanks are dropped and trimmed`() {
        val decoded = TriggerConditionCodec.decode(
            """{"type":"NETWORK","wifiOnly":true,"ssids":["  Home  ","","Office"]}""",
        )

        assertEquals(
            TriggerCondition.NetworkConnected(wifiOnly = true, ssids = listOf("Home", "Office")),
            decoded,
        )
    }
}
