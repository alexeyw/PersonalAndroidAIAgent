package app.knotwork.design.components.chips

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Behavioural tests for [KnotworkChip]. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class KnotworkChipTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `clicking enabled chip invokes the click handler`() {
        var clickCount = 0
        composeTestRule.setContent {
            KnotworkTheme {
                KnotworkChip(label = "Filter", onClick = { clickCount++ })
            }
        }
        composeTestRule.onNodeWithText("Filter").assertIsEnabled().performClick()
        assertEquals(1, clickCount)
    }

    @Test
    fun `disabled chip is not clickable`() {
        var clickCount = 0
        composeTestRule.setContent {
            KnotworkTheme {
                KnotworkChip(label = "Filter", onClick = { clickCount++ }, enabled = false)
            }
        }
        composeTestRule.onNodeWithText("Filter").assertIsNotEnabled()
        assertEquals(0, clickCount)
    }

    @Test
    fun `chip with null onClick still renders the label as decorative content`() {
        composeTestRule.setContent {
            KnotworkTheme { KnotworkChip(label = "Tag") }
        }
        composeTestRule.onNodeWithText("Tag").assertExists()
    }
}
