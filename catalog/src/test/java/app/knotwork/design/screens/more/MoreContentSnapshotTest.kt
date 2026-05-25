package app.knotwork.design.screens.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
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
class MoreContentSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun more_default_light() = snapshot(name = "default", dark = false) {
        MoreContent(state = MorePreview.default())
    }

    @Test
    fun more_default_dark() = snapshot(name = "default", dark = true) {
        MoreContent(state = MorePreview.default())
    }

    private fun snapshot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                KnotworkTheme(darkTheme = dark) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/more_${name}_$themeTag.png",
        )
    }
}

internal object MorePreview {
    fun default(): MoreViewState = MoreViewState(
        rows = listOf(
            MoreRow(
                id = "memory",
                title = "Memory",
                subtitle = "1 248 chunks · 14.2 MB",
                icon = Icons.Outlined.Psychology,
                onClick = {},
            ),
            MoreRow(
                id = "models",
                title = "Models",
                subtitle = "gemma-4-E2B · active",
                icon = Icons.Outlined.Memory,
                onClick = {},
            ),
            MoreRow(
                id = "prompts",
                title = "Prompt library",
                subtitle = "8 categories · 24 prompts",
                icon = Icons.Outlined.Tune,
                onClick = {},
            ),
            MoreRow(
                id = "tasks",
                title = "Active tasks",
                subtitle = "2 running · 4 queued",
                icon = Icons.Outlined.History,
                badge = 2,
                onClick = {},
            ),
            MoreRow(
                id = "metrics",
                title = "Live metrics",
                subtitle = "tok/s · latency · battery",
                icon = Icons.Outlined.Bolt,
                onClick = {},
            ),
            MoreRow(
                id = "settings",
                title = "Settings",
                subtitle = "system prompt · LLM params · keys",
                icon = Icons.Outlined.Settings,
                onClick = {},
            ),
            MoreRow(
                id = "about",
                title = "About",
                subtitle = "v0.1 · build 2026.05.23",
                icon = Icons.Outlined.Info,
                onClick = {},
            ),
        ),
        networkStatus = "on-device · no network calls in last 14 m",
        networkStatusOk = true,
    )
}
