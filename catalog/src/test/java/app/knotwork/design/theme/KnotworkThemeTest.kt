package app.knotwork.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.tokens.KnotworkPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies that [KnotworkTheme] wires the Knotwork tokens into the
 * underlying [MaterialTheme] and into the `KnotworkTheme.*`
 * composition-local accessors.
 *
 * Each test renders a single [KnotworkTheme] composition with the
 * `darkTheme` parameter pinned explicitly and reads the contract values
 * from inside the composition. The reads are captured into local variables
 * so the assertions can run outside the Compose tree.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class KnotworkThemeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `given KnotworkTheme light when read MaterialTheme primary then equals Accent500`() {
        val captured = captureInTheme(darkTheme = false) { MaterialTheme.colorScheme.primary }
        assertEquals(KnotworkPalette.Accent500, captured)
    }

    @Test
    fun `given KnotworkTheme dark when read MaterialTheme primary then equals Accent400`() {
        val captured = captureInTheme(darkTheme = true) { MaterialTheme.colorScheme.primary }
        assertEquals(KnotworkPalette.Accent400, captured)
    }

    @Test
    fun `given KnotworkTheme light when read extended nodeIntentRouter then equals expected hue`() {
        val captured = captureInTheme(darkTheme = false) { KnotworkTheme.extended.nodeIntentRouter }
        assertEquals(Color(0xFF9283DC), captured)
    }

    @Test
    fun `given KnotworkTheme dark when read extended nodeIntentRouter then equals expected hue`() {
        val captured = captureInTheme(darkTheme = true) { KnotworkTheme.extended.nodeIntentRouter }
        assertEquals(Color(0xFF9283DC), captured)
    }

    @Test
    fun `given KnotworkTheme when read spacing sp4 then equals 16dp`() {
        val captured = captureInTheme(darkTheme = false) { KnotworkTheme.spacing.sp4 }
        assertEquals(16.dp, captured)
    }

    @Test
    fun `given KnotworkTheme dark when read extended chatBotBg then matches dark surface2`() {
        val captured = captureInTheme(darkTheme = true) { KnotworkTheme.extended.chatBotBg }
        // KnotworkExtendedColors.chatBotBg in dark theme aliases surface2 (#221E1A).
        assertEquals(Color(0xFF221E1A), captured)
    }

    @Test
    fun `given KnotworkTheme light when read MaterialTheme tertiaryContainer then equals light TertiaryContainer`() {
        // Guards the M3 expanded slot wiring — a missing tertiaryContainer
        // assignment falls back to Material baseline #FFD8E2 instead of the
        // Knotwork cool-blue tint.
        val captured = captureInTheme(darkTheme = false) { MaterialTheme.colorScheme.tertiaryContainer }
        assertEquals(Color(0xFFD6E5EB), captured)
    }

    @Test
    fun `given KnotworkTheme dark when read MaterialTheme tertiaryContainer then equals dark TertiaryContainer`() {
        val captured = captureInTheme(darkTheme = true) { MaterialTheme.colorScheme.tertiaryContainer }
        assertEquals(Color(0xFF1A3B47), captured)
    }

    @Test
    fun `given KnotworkTheme light when read MaterialTheme surfaceContainer then equals light Surface2`() {
        // Guards the expanded surface ramp wiring (M3 1.1+) — without an
        // explicit assignment, surfaceContainer would inherit the Material
        // baseline tint instead of Knotwork Surface2.
        val captured = captureInTheme(darkTheme = false) { MaterialTheme.colorScheme.surfaceContainer }
        assertEquals(Color(0xFFF6F2ED), captured)
    }

    @Test
    fun `given KnotworkTheme dark when read MaterialTheme surfaceContainerHighest then equals dark Surface4`() {
        val captured = captureInTheme(darkTheme = true) { MaterialTheme.colorScheme.surfaceContainerHighest }
        assertEquals(Color(0xFF39342F), captured)
    }

    /**
     * Renders a single composition wrapped in [KnotworkTheme] and returns the
     * value produced by [reader].
     *
     * Each invocation must be the only call within a single `@Test` body —
     * `setContent` may only be called once per test rule lifetime.
     */
    private inline fun <reified T : Any> captureInTheme(
        darkTheme: Boolean,
        crossinline reader: @Composable () -> T,
    ): T {
        var captured: T? = null
        composeTestRule.setContent {
            KnotworkTheme(darkTheme = darkTheme) {
                captured = reader()
            }
        }
        composeTestRule.waitForIdle()
        return checkNotNull(captured) {
            "Reader did not run — composition was not invoked."
        }
    }
}
