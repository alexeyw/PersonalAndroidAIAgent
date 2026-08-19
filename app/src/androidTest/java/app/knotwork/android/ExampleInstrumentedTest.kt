package app.knotwork.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke check that the instrumentation is wired to the app under test at all —
 * if this fails, every other instrumented test is failing for the same reason and
 * their messages will not say so.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // A prefix, deliberately, not equality against the bare `applicationId`.
        // Debug builds carry `applicationIdSuffix = ".debug"` so a debug install can
        // sit beside a release one, and instrumented tests only ever run on a debug
        // variant — so asserting the unsuffixed id fails on every real run. Pinning
        // the suffixed id instead would just move the breakage to the next variant.
        assertTrue(
            "Unexpected target package: ${appContext.packageName}",
            appContext.packageName.startsWith("app.knotwork.android"),
        )
    }
}
