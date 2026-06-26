package app.knotwork.android

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the `full`-flavour value of `BuildConfig.CRASH_REPORTING_AVAILABLE`.
 *
 * The presentation layer hides the crash-reporting consent toggle when this
 * flag is `false`; the full distribution ships a live Firebase collector and
 * must therefore surface the toggle. This pins the `buildConfigField` wiring so
 * the two flavours' values can never be silently swapped.
 */
class CrashReportingFlavourConfigTest {
    @Test
    fun `full flavour reports crash reporting as available`() {
        assertTrue(BuildConfig.CRASH_REPORTING_AVAILABLE)
    }
}
