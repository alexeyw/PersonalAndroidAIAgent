package app.knotwork.design.screens.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behavioural tests for the settings-hub search surface: result rows render and
 * route on tap, and the no-match empty state offers a working clear action.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class SettingsHubSearchTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tapping a search result routes with its row`() {
        var clicked: HubSearchResultRow? = null
        composeTestRule.setContent {
            KnotworkTheme {
                SettingsHubContent(
                    state = SettingsPreview.hubSearchResults(),
                    callbacks = SettingsCallbacks(onSearchResultClick = { clicked = it }),
                )
            }
        }
        composeTestRule.onNodeWithText("Max context length").assertIsDisplayed().performClick()
        assertEquals("MAX_CONTEXT_LENGTH", clicked?.anchorKey)
    }

    @Test
    fun `a basic-tier hit is tagged Basic`() {
        // The tier tag has two branches and, for a while, only one of them was
        // depicted: every entry matching the "max" fixture's query is Advanced.
        // Asserted here rather than in a snapshot because the snapshot suite is
        // inert under `./gradlew check` — it verifies only when a Roborazzi task
        // is in the graph.
        composeTestRule.setContent {
            KnotworkTheme { SettingsHubContent(state = SettingsPreview.hubSearchBasicTier()) }
        }
        composeTestRule.onNodeWithText("Run limits").assertIsDisplayed()
        composeTestRule.onNodeWithText("Basic").assertIsDisplayed()
    }

    @Test
    fun `no-match state shows the query and clears on action`() {
        var cleared = false
        composeTestRule.setContent {
            KnotworkTheme {
                SettingsHubContent(
                    state = SettingsPreview.hubSearchEmpty(),
                    callbacks = SettingsCallbacks(onClearSearch = { cleared = true }),
                )
            }
        }
        composeTestRule.onNodeWithTag(SETTINGS_SEARCH_EMPTY_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear search").performClick()
        assertEquals(true, cleared)
    }

    @Test
    fun `blank query renders the default body, not search results`() {
        composeTestRule.setContent {
            KnotworkTheme {
                SettingsHubContent(state = SettingsPreview.hubDefault())
            }
        }
        composeTestRule.onNodeWithTag(SETTINGS_HUB_BODY_TEST_TAG).assertIsDisplayed()
    }
}
