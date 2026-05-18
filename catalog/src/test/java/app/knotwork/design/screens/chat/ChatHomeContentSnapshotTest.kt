package app.knotwork.design.screens.chat

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

/**
 * Roborazzi snapshot baseline for `ChatHomeContent` across all 9 documented
 * states (`compose/screens/README.md §C1`) in both themes — 18 PNGs.
 *
 * Reduced-motion is pinned via [FixedKnotworkA11y] so the `KnotworkLoader`
 * and any other looping animation collapse to a deterministic steady-state
 * per `decisions.md §14`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class ChatHomeContentSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chat_home_empty_light() = snapshot(name = "empty", dark = false) {
        ChatHomeContent(state = ChatHomePreview.empty())
    }

    @Test
    fun chat_home_empty_dark() = snapshot(name = "empty", dark = true) {
        ChatHomeContent(state = ChatHomePreview.empty())
    }

    @Test
    fun chat_home_idle_light() = snapshot(name = "idle", dark = false) {
        ChatHomeContent(state = ChatHomePreview.idle())
    }

    @Test
    fun chat_home_idle_dark() = snapshot(name = "idle", dark = true) {
        ChatHomeContent(state = ChatHomePreview.idle())
    }

    @Test
    fun chat_home_generating_light() = snapshot(name = "generating", dark = false) {
        ChatHomeContent(state = ChatHomePreview.generating())
    }

    @Test
    fun chat_home_generating_dark() = snapshot(name = "generating", dark = true) {
        ChatHomeContent(state = ChatHomePreview.generating())
    }

    @Test
    fun chat_home_hitl_confirm_light() = snapshot(name = "hitl_confirm", dark = false) {
        ChatHomeContent(state = ChatHomePreview.hitlConfirm())
    }

    @Test
    fun chat_home_hitl_confirm_dark() = snapshot(name = "hitl_confirm", dark = true) {
        ChatHomeContent(state = ChatHomePreview.hitlConfirm())
    }

    @Test
    fun chat_home_clarification_light() = snapshot(name = "clarification", dark = false) {
        ChatHomeContent(state = ChatHomePreview.clarification())
    }

    @Test
    fun chat_home_clarification_dark() = snapshot(name = "clarification", dark = true) {
        ChatHomeContent(state = ChatHomePreview.clarification())
    }

    @Test
    fun chat_home_error_light() = snapshot(name = "error", dark = false) {
        ChatHomeContent(state = ChatHomePreview.error())
    }

    @Test
    fun chat_home_error_dark() = snapshot(name = "error", dark = true) {
        ChatHomeContent(state = ChatHomePreview.error())
    }

    @Test
    fun chat_home_drawer_open_light() = snapshot(name = "drawer_open", dark = false) {
        ChatHomeContent(state = ChatHomePreview.drawerOpen())
    }

    @Test
    fun chat_home_drawer_open_dark() = snapshot(name = "drawer_open", dark = true) {
        ChatHomeContent(state = ChatHomePreview.drawerOpen())
    }

    @Test
    fun chat_home_console_expanded_light() = snapshot(name = "console_expanded", dark = false) {
        ChatHomeContent(state = ChatHomePreview.consoleExpanded())
    }

    @Test
    fun chat_home_console_expanded_dark() = snapshot(name = "console_expanded", dark = true) {
        ChatHomeContent(state = ChatHomePreview.consoleExpanded())
    }

    /**
     * Wraps the content under the standard test rule, pins reduced-motion so
     * looping animations don't randomise the snapshot, and writes the PNG to
     * `src/test/snapshots/chat_home_<name>_<theme>.png`.
     */
    private fun snapshot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                KnotworkTheme(darkTheme = dark) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/chat_home_${name}_$themeTag.png",
        )
    }
}
