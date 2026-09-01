package app.knotwork.android.presentation.ui.tools

import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.McpTool
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.models.ToolSource
import app.knotwork.design.screens.tools.BuiltInToolRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one rule the Tools list and the tool-detail screen must agree on:
 * which tools the approval gate resolves from an override, and which it resolves
 * from the code.
 *
 * Getting it wrong is silent in both directions. Applying an override to a
 * built-in would make two screens agree with each other and disagree with the
 * gate — a screen promising a call would not be stopped. Ignoring one for an MCP
 * tool leaves the list saying *Sensitive* about a tool the user has already set
 * to read-only, which is the same defect at lower stakes.
 */
class ToolRiskResolutionTest {

    @Test
    fun `given a built-in tool when an override exists then the declared level still wins`() {
        val builtin = AgentTool(
            name = "search_tool",
            description = "",
            parameters = "{}",
            risk = ToolRisk.READ_ONLY,
            source = ToolSource.BUILT_IN,
        )

        val resolved = ToolRiskResolution.forLocalTool(
            tool = builtin,
            overrides = mapOf("search_tool" to ToolRisk.DESTRUCTIVE),
        )

        // `ToolRepositoryImpl.getRisk` returns on the built-in's own risk before
        // it reads the override map at all, so honouring one here would put the
        // screen and the gate into open disagreement.
        assertEquals(BuiltInToolRisk.ReadOnly, resolved)
        assertFalse(ToolRiskResolution.isOverridable(builtin))
    }

    @Test
    fun `given a discovered AppFunction when an override exists then the user's level wins`() {
        val discovered = AgentTool(
            name = "com.example/DoThing#invoke",
            description = "",
            parameters = "{}",
            risk = ToolRisk.SENSITIVE,
            source = ToolSource.APP_FUNCTION,
        )

        val resolved = ToolRiskResolution.forLocalTool(
            tool = discovered,
            overrides = mapOf("com.example/DoThing#invoke" to ToolRisk.READ_ONLY),
        )

        assertEquals(BuiltInToolRisk.ReadOnly, resolved)
        assertTrue(ToolRiskResolution.isOverridable(discovered))
    }

    @Test
    fun `given no override when resolved then the conservative default applies`() {
        val discovered = AgentTool(
            name = "com.example/DoThing#invoke",
            description = "",
            parameters = "{}",
            risk = ToolRisk.SENSITIVE,
            source = ToolSource.APP_FUNCTION,
        )

        // An absent entry is not "no risk" — the gate falls back to SENSITIVE
        // for anything it cannot inspect, and the screen has to say the same.
        assertEquals(BuiltInToolRisk.Sensitive, ToolRiskResolution.forLocalTool(discovered, emptyMap()))
        assertEquals(BuiltInToolRisk.Sensitive, ToolRiskResolution.forOverridable("anything", emptyMap()))
    }

    @Test
    fun `given an unknown tool id when resolved then it is sensitive and offers no control`() {
        assertEquals(BuiltInToolRisk.Sensitive, ToolRiskResolution.forLocalTool(tool = null, overrides = emptyMap()))
        assertFalse(ToolRiskResolution.isOverridable(null))
    }

    @Test
    fun `given two servers advertising one tool name then their overrides stay separate`() {
        val first = mcpTool(id = "mcp:aaaaaaaa:create_issue")
        val second = mcpTool(id = "mcp:bbbbbbbb:create_issue")
        val overrides = mapOf("mcp:aaaaaaaa:create_issue" to ToolRisk.READ_ONLY)

        // The key carries the server hash, so a decision about one server's
        // `create_issue` must not quietly apply to another's.
        assertEquals(BuiltInToolRisk.ReadOnly, ToolRiskResolution.forMcpTool(first, overrides))
        assertEquals(BuiltInToolRisk.Sensitive, ToolRiskResolution.forMcpTool(second, overrides))
    }

    @Test
    fun `given a level when mapped to the UI and back then it survives the round trip`() = with(ToolRiskResolution) {
        ToolRisk.entries.forEach { risk ->
            assertEquals("round trip lost $risk", risk, risk.toUi().toDomain())
        }
    }

    private fun mcpTool(id: String): McpTool = McpTool(
        id = id,
        serverUrl = "https://server.example/mcp",
        name = "create_issue",
        description = "",
        inputSchemaJson = "{}",
    )
}
