package app.knotwork.android

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards the `foss`-flavour value of `BuildConfig.CRASH_REPORTING_AVAILABLE`.
 *
 * The F-Droid build ships no crash collector, so the flag must be `false`; the
 * presentation layer then hides the crash-reporting consent toggle entirely.
 * This pins the `buildConfigField` wiring so the two flavours' values can never
 * be silently swapped.
 */
class CrashReportingFlavourConfigTest {
    @Test
    fun `foss flavour reports crash reporting as unavailable`() {
        assertFalse(BuildConfig.CRASH_REPORTING_AVAILABLE)
    }
}
