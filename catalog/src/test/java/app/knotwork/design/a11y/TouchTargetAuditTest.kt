package app.knotwork.design.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.components.buttons.KnotworkIconButton
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.screens.memory.MemoryContent
import app.knotwork.design.screens.memory.MemoryPreview
import app.knotwork.design.screens.skills.SkillLibraryContent
import app.knotwork.design.screens.skills.SkillLibraryPreview
import app.knotwork.design.screens.triggers.TriggersContent
import app.knotwork.design.screens.triggers.TriggersPreview
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cross-screen audit of the per-row controls that had shrunk below
 * [MinTouchTarget].
 *
 * Material's `IconButton` reserves 48 dp on its own; an explicit
 * `Modifier.size(…)` on the button silently takes it away while the glyph
 * still looks right, which is how six of these regressed independently across
 * the catalog. A screenshot cannot catch it — the button looks *better*
 * smaller — so it needs an assertion.
 *
 * Each case renders the real screen fixture and measures the row control by the
 * `contentDescription` a user's finger is aiming at.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class TouchTargetAuditTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun triggerRow_overflowMeetsTheFloor() {
        render { TriggersContent(state = TriggersPreview.populated()) }

        assertEveryTargetMeetsTheFloor("More actions")
    }

    @Test
    fun skillRow_overflowMeetsTheFloor() {
        render { SkillLibraryContent(state = SkillLibraryPreview.bundledPopulated()) }

        assertEveryTargetMeetsTheFloor("More actions")
    }

    /**
     * The shared `KnotworkIconButton` — the top-bar button on most screens.
     * Its KDoc promised a 48 dp target while an explicit `.size(40.dp)` removed
     * it, so every icon button in the app was 8 dp short.
     */
    @Test
    fun sharedIconButton_meetsTheFloor() {
        render {
            KnotworkIconButton(
                onClick = {},
                contentDescription = SHARED_BUTTON_CD,
                icon = AppIcons.More,
            )
        }

        assertEveryTargetMeetsTheFloor(SHARED_BUTTON_CD)
    }

    @Test
    fun memoryRow_pinToggleMeetsTheFloor() {
        render { MemoryContent(state = MemoryPreview.populated()) }

        assertEveryTargetMeetsTheFloor("Pin")
    }

    private companion object {
        /** Label used to find the shared icon button under test. */
        const val SHARED_BUTTON_CD = "Shared icon button"
    }

    private fun render(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            // Inside the theme — the placement that survives the theme ever
            // provisioning `LocalKnotworkA11y` again.
            KnotworkTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true),
                ) {
                    content()
                }
            }
        }
    }

    /** Asserts every node carrying [contentDescription] is at least [MinTouchTarget] square. */
    private fun assertEveryTargetMeetsTheFloor(contentDescription: String) {
        val nodes = composeTestRule.onAllNodesWithContentDescription(
            contentDescription,
            substring = true,
        )
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("Expected at least one \"$contentDescription\" control to measure", count > 0)
        repeat(count) { i ->
            nodes[i]
                .assertWidthIsAtLeast(MinTouchTarget)
                .assertHeightIsAtLeast(MinTouchTarget)
        }
    }
}
