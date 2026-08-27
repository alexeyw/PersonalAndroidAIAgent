package app.knotwork.design.screens.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.R
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the three rules the Tools surface gained from the closed test.
 *
 *  - **One door to adding a server, and it is reachable without scrolling.**
 *    The `+ Add MCP` link used to sit in the MCP section header, i.e. below the
 *    entire built-in list — "Ты её вниз запихнул", "ваще не интуитивно".
 *  - **A collapsed group may not hide a problem.** Folding the MCP servers away
 *    must surface the disconnected one on the header.
 *  - **An empty group is not collapsible.** A chevron that reveals emptiness
 *    teaches the wrong thing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class ToolsGroupAffordanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `the top bar carries the add-server action`() {
        var addOpened = 0
        setContent {
            ToolsContent(
                state = ToolsPreview.default(),
                callbacks = ToolsCallbacks(onAddServerOpen = { addOpened++ }),
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.knotwork_tools_add_mcp_cd)).performClick()

        assertEquals(1, addOpened)
    }

    @Test
    fun `the empty MCP group offers the loud CTA and it opens the same form`() {
        var addOpened = 0
        setContent {
            ToolsContent(
                state = ToolsPreview.noMcpServers(),
                callbacks = ToolsCallbacks(onAddServerOpen = { addOpened++ }),
            )
        }

        composeTestRule.onNodeWithText(string(R.string.knotwork_tools_empty_mcp_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.knotwork_tools_empty_mcp_cta)).performClick()

        assertEquals(1, addOpened)
    }

    @Test
    fun `collapsing a group hides its rows`() {
        setContent { ToolsContent(state = ToolsPreview.default()) }
        val firstTool = ToolsPreview.default().builtInTools.first().name
        composeTestRule.onNodeWithText(firstTool).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.knotwork_tools_section_built_in)).performClick()

        composeTestRule.onNodeWithText(firstTool).assertDoesNotExist()
    }

    @Test
    fun `a collapsed MCP group reports the disconnected server on its header`() {
        setContent { ToolsContent(state = ToolsPreview.defaultDisconnected()) }
        val warning = RuntimeEnvironment.getApplication().resources
            .getQuantityString(R.plurals.knotwork_tools_group_warn_disconnected, 1, 1)
        // Expanded, the row says it itself and the header stays quiet.
        composeTestRule.onNodeWithText(warning).assertDoesNotExist()

        composeTestRule.onNodeWithText(string(R.string.knotwork_tools_section_mcp)).performClick()

        composeTestRule.onNodeWithText(warning).assertIsDisplayed()
    }

    @Test
    fun `an empty group does not collapse`() {
        setContent { ToolsContent(state = ToolsPreview.noMcpServers()) }

        // Tapping the header of the empty MCP group must be inert — there is no
        // chevron and nothing to fold, so the empty-state card stays put.
        composeTestRule.onNodeWithText(string(R.string.knotwork_tools_section_mcp)).performClick()

        composeTestRule.onNodeWithText(string(R.string.knotwork_tools_empty_mcp_title)).assertIsDisplayed()
    }

    @Test
    fun `a disconnected server states it in words, not only in colour`() {
        setContent { ToolsContent(state = ToolsPreview.defaultDisconnected()) }

        composeTestRule.onNodeWithText(string(R.string.knotwork_tools_mcp_disconnected)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.knotwork_tools_mcp_reconnect)).assertIsDisplayed()
    }

    @Test
    fun `reconnect asks the host to refresh that server`() {
        val disconnected = ToolsPreview.defaultDisconnected()
        var refreshed: String? = null
        setContent {
            ToolsContent(
                state = disconnected,
                callbacks = ToolsCallbacks(onServerRefresh = { refreshed = it }),
            )
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.knotwork_tools_mcp_reconnect)).performClick()

        assertEquals(disconnected.mcpServers.first().id, refreshed)
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent { KnotworkTheme { content() } }
    }
}
