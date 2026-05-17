package app.knotwork.design.screens.settings

import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class SettingsContentSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settings_loading_light() = snapshot(name = "loading", dark = false) {
        SettingsContent(state = SettingsPreview.loading())
    }

    @Test
    fun settings_default_light() = snapshot(name = "default", dark = false) {
        SettingsContent(state = SettingsPreview.default(), trailingControl = ::DefaultTrailing)
    }

    @Test
    fun settings_default_dark() = snapshot(name = "default", dark = true) {
        SettingsContent(state = SettingsPreview.default(), trailingControl = ::DefaultTrailing)
    }

    @Test
    fun settings_pending_change_light() = snapshot(name = "pending_change", dark = false) {
        SettingsContent(state = SettingsPreview.pendingChange(), trailingControl = ::DefaultTrailing)
    }

    @Test
    fun settings_validation_error_light() = snapshot(name = "validation_error", dark = false) {
        SettingsContent(state = SettingsPreview.validationError(), trailingControl = ::DefaultTrailing)
    }

    @Test
    fun settings_restart_required_light() = snapshot(name = "restart_required", dark = false) {
        SettingsContent(state = SettingsPreview.restartRequired(), trailingControl = ::DefaultTrailing)
    }

    @Test
    fun settings_destructive_action_light() = snapshot(name = "destructive_action", dark = false) {
        SettingsContent(state = SettingsPreview.destructiveAction(), trailingControl = ::DefaultTrailing)
    }

    @Test
    fun settings_error_light() = snapshot(name = "section_error", dark = false) {
        SettingsContent(state = SettingsPreview.sectionError(), trailingControl = ::DefaultTrailing)
    }

    @Composable
    private fun DefaultTrailing(row: SettingsRowState) {
        when (row.id) {
            "theme", "telemetry", "local_only" -> Switch(checked = true, onCheckedChange = {})
            else -> Text(text = row.subtitle.orEmpty())
        }
    }

    private fun snapshot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                KnotworkTheme(darkTheme = dark) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/settings_${name}_$themeTag.png",
        )
    }
}

/** Internal preview fixtures backing the settings snapshot suite. */
internal object SettingsPreview {

    private fun appearanceBlock(): SettingsSectionBlock = SettingsSectionBlock(
        section = SettingsSection.Appearance,
        rows = listOf(
            SettingsRowState(
                id = "theme",
                title = "Theme",
                subtitle = "System default",
            ),
        ),
    )

    private fun modelsBlock(): SettingsSectionBlock = SettingsSectionBlock(
        section = SettingsSection.Models,
        rows = listOf(
            SettingsRowState(
                id = "default_model",
                title = "Default model",
                subtitle = "Gemma 2 · 2B",
            ),
            SettingsRowState(
                id = "fallback",
                title = "Fallback",
                subtitle = "Cloud (OpenAI · gpt-4o-mini)",
            ),
        ),
    )

    private fun privacyBlock(): SettingsSectionBlock = SettingsSectionBlock(
        section = SettingsSection.Privacy,
        rows = listOf(
            SettingsRowState(
                id = "local_only",
                title = "Local-only mode",
                subtitle = "Never reach the network during a pipeline run.",
            ),
            SettingsRowState(
                id = "telemetry",
                title = "Crash reporting",
                subtitle = "Anonymous opt-in.",
            ),
        ),
    )

    fun loading(): SettingsViewState = SettingsViewState(visualState = SettingsVisualState.Loading)

    fun default(): SettingsViewState = SettingsViewState(
        visualState = SettingsVisualState.Default,
        sections = listOf(appearanceBlock(), modelsBlock(), privacyBlock()),
    )

    fun pendingChange(): SettingsViewState = SettingsViewState(
        visualState = SettingsVisualState.PendingChange,
        sections = listOf(
            appearanceBlock().copy(
                rows = listOf(
                    SettingsRowState(
                        id = "theme",
                        title = "Theme",
                        subtitle = "Persisting…",
                        pendingChange = true,
                    ),
                ),
            ),
        ),
    )

    fun validationError(): SettingsViewState = SettingsViewState(
        visualState = SettingsVisualState.ValidationError,
        sections = listOf(
            modelsBlock().copy(
                rows = listOf(
                    SettingsRowState(
                        id = "openai_key",
                        title = "OpenAI API key",
                        subtitle = "sk-…",
                        validationError = "Key doesn't look like a known provider format.",
                    ),
                ),
            ),
        ),
    )

    fun restartRequired(): SettingsViewState = SettingsViewState(
        visualState = SettingsVisualState.RestartRequired,
        sections = listOf(appearanceBlock(), privacyBlock()),
        restartRequiredMessage = "Changes take effect on restart.",
    )

    fun destructiveAction(): SettingsViewState = SettingsViewState(
        visualState = SettingsVisualState.DestructiveAction,
        sections = listOf(appearanceBlock()),
        destructiveActionMessage = "Clear all long-term memory entries from this device?",
    )

    fun sectionError(): SettingsViewState = SettingsViewState(
        visualState = SettingsVisualState.Error,
        sections = listOf(
            modelsBlock().copy(errorMessage = "Could not reach the local model registry."),
            privacyBlock(),
        ),
    )
}
