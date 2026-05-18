package app.knotwork.design.components.chips

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies that every [RiskPill] variant exposes a `Risk level: <level>`
 * `contentDescription` so TalkBack announces the state and not just the
 * decorative colour (`decisions.md §14`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RiskPillTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `Readonly variant exposes risk level content description`() {
        composeTestRule.setContent {
            KnotworkTheme { RiskPill(risk = Risk.Readonly) }
        }
        composeTestRule.onNodeWithContentDescription("Risk level: Read-only").assertIsDisplayed()
    }

    @Test
    fun `Sensitive variant exposes risk level content description`() {
        composeTestRule.setContent {
            KnotworkTheme { RiskPill(risk = Risk.Sensitive) }
        }
        composeTestRule.onNodeWithContentDescription("Risk level: Sensitive").assertIsDisplayed()
    }

    @Test
    fun `Destructive variant exposes risk level content description`() {
        composeTestRule.setContent {
            KnotworkTheme { RiskPill(risk = Risk.Destructive) }
        }
        composeTestRule.onNodeWithContentDescription("Risk level: Destructive").assertIsDisplayed()
    }
}
