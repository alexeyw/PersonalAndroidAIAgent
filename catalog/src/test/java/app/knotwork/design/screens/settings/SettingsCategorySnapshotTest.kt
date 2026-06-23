package app.knotwork.design.screens.settings

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
 * Roborazzi baseline for the settings category sub-screens. Memory exercises the
 * full state matrix (Basic / Advanced / validation-error / pending re-embed /
 * provider-mismatch banner); Generation, Tools, Pipelines, Background, Privacy
 * and About cover breadth, with a font-scale 200% Tools board.
 *
 * The destructive typed-confirm dialog is intentionally omitted (its
 * `OutlinedTextField` cursor-blink animation trips Roborazzi's idle guard); that
 * flow is asserted at the VM / instrumented level instead.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class SettingsCategorySnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun generation_basic_light() = snapshot("generation_basic", dark = false) {
        GenerationSettingsContent(state = SettingsPreview.generation())
    }

    @Test
    fun generation_advanced_light() = snapshot("generation_advanced", dark = false) {
        GenerationSettingsContent(state = SettingsPreview.generation(), advancedExpanded = true)
    }

    @Test
    fun models_default_light() = snapshot("models_default", dark = false) {
        ModelsSettingsContent(state = SettingsPreview.models())
    }

    @Test
    fun models_restart_required_light() = snapshot("models_restart", dark = false) {
        ModelsSettingsContent(state = SettingsPreview.modelsRestart())
    }

    @Test
    fun memory_basic_light() = snapshot("memory_basic", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memory())
    }

    @Test
    fun memory_basic_dark() = snapshot("memory_basic", dark = true) {
        MemorySettingsContent(state = SettingsPreview.memory())
    }

    @Test
    fun memory_advanced_light() = snapshot("memory_advanced", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memory(), advancedExpanded = true)
    }

    @Test
    fun memory_validation_error_light() = snapshot("memory_error", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memoryError(), advancedExpanded = true)
    }

    @Test
    fun memory_pending_reembed_light() = snapshot("memory_pending", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memoryPending(), advancedExpanded = true)
    }

    @Test
    fun memory_reembed_banner_light() = snapshot("memory_reembed_banner", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memoryReembedBanner(), advancedExpanded = true)
    }

    @Test
    fun pipelines_advanced_light() = snapshot("pipelines_advanced", dark = false) {
        PipelinesSettingsContent(state = SettingsPreview.pipelines(), advancedExpanded = true)
    }

    @Test
    fun tools_basic_light() = snapshot("tools_basic", dark = false) {
        ToolsSettingsContent(state = SettingsPreview.tools())
    }

    @Test
    fun tools_advanced_font_scale_2x_light() = snapshot("tools_advanced_font_scale_2x", dark = false, fontScale = 2f) {
        ToolsSettingsContent(state = SettingsPreview.tools(), advancedExpanded = true)
    }

    @Test
    fun background_advanced_light() = snapshot("background_advanced", dark = false) {
        BackgroundSettingsContent(state = SettingsPreview.background(), advancedExpanded = true)
    }

    @Test
    fun privacy_advanced_light() = snapshot("privacy_advanced", dark = false) {
        PrivacySettingsContent(state = SettingsPreview.privacy(), advancedExpanded = true)
    }

    @Test
    fun about_advanced_light() = snapshot("about_advanced", dark = false) {
        AboutSettingsContent(state = SettingsPreview.about(), advancedExpanded = true)
    }

    private fun snapshot(name: String, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true, fontScale = fontScale),
                LocalDensity provides Density(density = baseDensity.density, fontScale = fontScale),
            ) {
                KnotworkTheme(darkTheme = dark) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/snapshots/settings_${name}_$themeTag.png")
    }
}
