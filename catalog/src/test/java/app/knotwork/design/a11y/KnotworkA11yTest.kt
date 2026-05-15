package app.knotwork.design.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies the [KnotworkA11y] contract:
 *  - the default `LocalKnotworkA11y` resolves to [DefaultKnotworkA11y];
 *  - tests can override the local with a [FixedKnotworkA11y] to pin
 *    deterministic values for snapshots.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class KnotworkA11yTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `default LocalKnotworkA11y resolves to DefaultKnotworkA11y`() {
        var captured: KnotworkA11y? = null
        composeTestRule.setContent {
            captured = LocalKnotworkA11y.current
            Box {}
        }
        composeTestRule.runOnIdle {
            assertSame(DefaultKnotworkA11y, captured)
        }
    }

    @Test
    fun `FixedKnotworkA11y returns its constructor values`() {
        var reduced = false
        var fontScale = 0f
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true, fontScale = 1.5f),
            ) {
                val a11y = LocalKnotworkA11y.current
                reduced = a11y.reducedMotion()
                fontScale = a11y.fontScale()
                Box {}
            }
        }
        composeTestRule.runOnIdle {
            assertTrue(reduced)
            assertEquals(1.5f, fontScale, 0.0001f)
        }
    }

    @Test
    fun `FixedKnotworkA11y default returns reducedMotion=false`() {
        var reduced = true
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y()) {
                val a11y = LocalKnotworkA11y.current
                reduced = a11y.reducedMotion()
                Box {}
            }
        }
        composeTestRule.runOnIdle {
            assertFalse(reduced)
        }
    }
}
