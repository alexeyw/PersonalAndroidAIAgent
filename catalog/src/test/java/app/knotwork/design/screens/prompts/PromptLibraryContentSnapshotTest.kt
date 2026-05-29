package app.knotwork.design.screens.prompts

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
 * Roborazzi baseline for `PromptLibraryContent`. Covers default (cards),
 * empty, and editor-sheet states in both themes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class PromptLibraryContentSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun prompts_default_light() = snapshot(name = "default", dark = false) {
        PromptLibraryContent(state = PromptLibraryPreview.default())
    }

    @Test
    fun prompts_default_dark() = snapshot(name = "default", dark = true) {
        PromptLibraryContent(state = PromptLibraryPreview.default())
    }

    @Test
    fun prompts_empty_light() = snapshot(name = "empty", dark = false) {
        PromptLibraryContent(state = PromptLibraryPreview.empty())
    }

    @Test
    fun prompts_empty_dark() = snapshot(name = "empty", dark = true) {
        PromptLibraryContent(state = PromptLibraryPreview.empty())
    }

    @Test
    fun prompts_editor_light() = snapshot(name = "editor", dark = false) {
        PromptEditorSheetBody(
            state = PromptLibraryPreview.editorState(),
            availableVariables = listOf("\$DATE", "\$DEVICE", "\$LANG", "\$USER", "\$TZ"),
            strings = PromptEditorStrings(),
            callbacks = noopPromptLibraryCallbacks(),
        )
    }

    private fun snapshot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                KnotworkTheme(darkTheme = dark) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/prompts_${name}_$themeTag.png",
        )
    }
}

internal object PromptLibraryPreview {
    private val categories = listOf("DECOMPOSITION", "IF_CONDITION", "INTENT_ROUTER", "QUEUE_PROCESSOR")

    fun default(): PromptLibraryViewState = PromptLibraryViewState(
        visualState = PromptLibraryVisualState.Default,
        categories = categories,
        selectedCategory = "DECOMPOSITION",
        prompts = listOf(
            PromptRow(
                id = "snap-1",
                category = "DECOMPOSITION",
                name = "Decomposer",
                body = "You are a Task Decomposer. Break down the given complex task into a list of simpler, " +
                    "actionable subtasks. Output the result as a JSON array of strings. Today is \$DATE. " +
                    "Device locale: \$LANG.",
                usedByCount = 3,
            ),
            PromptRow(
                id = "snap-2",
                category = "DECOMPOSITION",
                name = "Steps · concise",
                body = "Split the user goal into 3–7 atomic steps. Each step must be independently " +
                    "executable by a tool or another LLM call. Return strictly JSON: {\"steps\": […]}.",
                usedByCount = 3,
            ),
        ),
        availableVariables = listOf("\$DATE", "\$DEVICE", "\$LANG", "\$USER", "\$TZ"),
        subtitle = "5 categories · 24 prompts",
    )

    fun empty(): PromptLibraryViewState = PromptLibraryViewState(
        visualState = PromptLibraryVisualState.Default,
        categories = categories,
        selectedCategory = "QUEUE_PROCESSOR",
        prompts = emptyList(),
        subtitle = "5 categories · 24 prompts",
    )

    fun editorState(): PromptEditorState = PromptEditorState(
        id = "snap-1",
        name = "Decomposer",
        category = "DECOMPOSITION",
        body = "You are a Task Decomposer. Break down the given complex task into a list of simpler, actionable " +
            "subtasks. Output the result as a JSON array of strings. Today is \$DATE.",
        usedByCount = 3,
    )
}
