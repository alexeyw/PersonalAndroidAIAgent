package app.knotwork.design.screens.pipelines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.a11y.FixedKnotworkA11y
import app.knotwork.design.a11y.LocalKnotworkA11y
import app.knotwork.design.theme.KnotworkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines for the preset manager.
 *
 * The screen had none: its composition lived in `:app`, so nothing could
 * compare it against anything. Both tabs matter — a bundled row is read-only
 * and its overflow carries only Export, while a saved row carries all three —
 * and so do the two dialogs, which are the states a reader reaches only by
 * committing to an action.
 *
 * The long-title row in the saved tab is deliberate, not filler. It is the case
 * `PresetCategoryBadgeLayoutTest` guards in `androidTest`: without
 * `weight(fill = false)` the title takes the whole row and the badge collapses
 * to a one-character-per-line sliver.
 *
 * **The rename dialog has no baseline, and this is not an oversight.** A text
 * field inside an `AlertDialog` never reaches idle here — `setContent` spins for
 * 60 seconds and Espresso gives up with "Compose did not get idle". Measured
 * rather than guessed: it happens with the plain Material `OutlinedTextField`
 * too, so it is the harness and not a component of ours, and no existing
 * baseline in this module captures a field inside a dialog either. Freezing the
 * test clock does not help — the loop is in composition, not in an animation.
 * The delete confirmation, which carries no field, is captured in both themes.
 * Do not re-add the rename capture without first making a field-in-dialog
 * settle; the fixture is kept for `@Preview` use.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class PresetManagerSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun preset_manager_bundled_light() = snapshot("bundled", dark = false) {
        PresetManagerContent(state = PresetManagerPreview.bundled())
    }

    @Test
    fun preset_manager_bundled_dark() = snapshot("bundled", dark = true) {
        PresetManagerContent(state = PresetManagerPreview.bundled())
    }

    @Test
    fun preset_manager_mine_light() = snapshot("mine", dark = false) {
        PresetManagerContent(state = PresetManagerPreview.mine())
    }

    @Test
    fun preset_manager_mine_dark() = snapshot("mine", dark = true) {
        PresetManagerContent(state = PresetManagerPreview.mine())
    }

    @Test
    fun preset_manager_empty_light() = snapshot("empty", dark = false) {
        PresetManagerContent(state = PresetManagerPreview.empty())
    }

    @Test
    fun preset_manager_empty_dark() = snapshot("empty", dark = true) {
        PresetManagerContent(state = PresetManagerPreview.empty())
    }

    @Test
    fun preset_manager_delete_light() = snapshot("delete", dark = false) {
        PresetManagerContent(state = PresetManagerPreview.deleting())
    }

    @Test
    fun preset_manager_delete_dark() = snapshot("delete", dark = true) {
        PresetManagerContent(state = PresetManagerPreview.deleting())
    }

    /**
     * At 200 % the tab labels carry their counts on the same baseline and the
     * category chips wrap. This is the capture that would catch a count clipped
     * off the end of a tab.
     */
    @Test
    fun preset_manager_font_scale_200_light() = snapshot("fontscale200", dark = false, fontScale = FONT_SCALE_200) {
        PresetManagerContent(state = PresetManagerPreview.mine())
    }

    private fun snapshot(name: String, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            KnotworkTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true, fontScale = fontScale),
                    LocalDensity provides Density(density = baseDensity.density, fontScale = fontScale),
                ) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/snapshots/preset_manager_${name}_$themeTag.png")
    }

    private companion object {
        const val FONT_SCALE_200: Float = 2f
    }
}
