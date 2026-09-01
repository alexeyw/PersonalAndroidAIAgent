package app.knotwork.android.presentation.ui.settings

import app.knotwork.android.domain.constants.SettingsDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip guard for the tool / workspace ceiling unit conversions.
 *
 * The failure this exists to stop is silent: a slider that reads a stored value
 * in one unit and writes it back in another does not error, it just quietly
 * multiplies or divides the user's ceiling by 1024 every time the screen is
 * touched. Asserting each direction separately would not catch it — only
 * composing them does, which is why every case below is a round trip.
 */
class ToolCeilingUnitsTest {

    @Test
    fun `given the shipped default when shown and dragged back then the stored value is unchanged`() {
        assertRoundTripMillis(SettingsDefaults.TOOL_CALL_TIMEOUT_MS_DEFAULT)
        assertRoundTripMegabytes(SettingsDefaults.WORKSPACE_MAX_FILE_SIZE_BYTES_DEFAULT)
        assertRoundTripMegabytes(SettingsDefaults.WORKSPACE_MAX_TOTAL_BYTES_DEFAULT)
        assertRoundTripKilobytes(SettingsDefaults.HTTP_TOOL_MAX_RESPONSE_BYTES_DEFAULT)
    }

    @Test
    fun `given either slider bound when shown and dragged back then the stored value is unchanged`() {
        assertRoundTripMillis(SettingsDefaults.TOOL_CALL_TIMEOUT_MS_MIN)
        assertRoundTripMillis(SettingsDefaults.TOOL_CALL_TIMEOUT_MS_MAX)
        assertRoundTripMegabytes(SettingsDefaults.WORKSPACE_MAX_FILE_SIZE_BYTES_MIN)
        assertRoundTripMegabytes(SettingsDefaults.WORKSPACE_MAX_FILE_SIZE_BYTES_MAX)
        assertRoundTripMegabytes(SettingsDefaults.WORKSPACE_MAX_TOTAL_BYTES_MIN)
        assertRoundTripMegabytes(SettingsDefaults.WORKSPACE_MAX_TOTAL_BYTES_MAX)
        assertRoundTripKilobytes(SettingsDefaults.HTTP_TOOL_MAX_RESPONSE_BYTES_MIN)
        assertRoundTripKilobytes(SettingsDefaults.HTTP_TOOL_MAX_RESPONSE_BYTES_MAX)
    }

    @Test
    fun `given a dragged position when persisted then it lands on the value the row displayed`() {
        // The slider hands back a Float mid-drag, so the conversion has to round
        // rather than truncate — otherwise the row shows "42 MB" while the
        // persisted value is 41 MB, and the next recomposition snaps it back.
        assertEquals(42L * 1024 * 1024, ToolCeilingUnits.megabytesToBytes(41.7f))
        assertEquals(90L * 1000, ToolCeilingUnits.secondsToMillis(89.6f))
        assertEquals(512L * 1024, ToolCeilingUnits.kilobytesToBytes(511.5f))
    }

    /** Shows [millis] on a slider and drags it back unchanged. */
    private fun assertRoundTripMillis(millis: Long) = assertEquals(
        millis,
        ToolCeilingUnits.secondsToMillis(ToolCeilingUnits.toSeconds(millis).toFloat()),
    )

    /** Shows [bytes] on a megabyte slider and drags it back unchanged. */
    private fun assertRoundTripMegabytes(bytes: Long) = assertEquals(
        bytes,
        ToolCeilingUnits.megabytesToBytes(ToolCeilingUnits.toMegabytes(bytes).toFloat()),
    )

    /** Shows [bytes] on a kilobyte slider and drags it back unchanged. */
    private fun assertRoundTripKilobytes(bytes: Long) = assertEquals(
        bytes,
        ToolCeilingUnits.kilobytesToBytes(ToolCeilingUnits.toKilobytes(bytes).toFloat()),
    )
}
