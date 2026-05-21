package app.knotwork.design.screens.tools

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
class ToolsContentSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tools_empty_light() = snapshot(name = "empty", dark = false) {
        ToolsContent(state = ToolsPreview.empty())
    }

    @Test
    fun tools_default_light() = snapshot(name = "default", dark = false) {
        ToolsContent(state = ToolsPreview.default())
    }

    @Test
    fun tools_default_dark() = snapshot(name = "default", dark = true) {
        ToolsContent(state = ToolsPreview.default())
    }

    @Test
    fun tools_loading_light() = snapshot(name = "loading", dark = false) {
        ToolsContent(state = ToolsPreview.loading())
    }

    @Test
    fun tools_error_light() = snapshot(name = "error", dark = false) {
        ToolsContent(state = ToolsPreview.error())
    }

    @Test
    fun tool_detail_default_light() = snapshot(name = "tool_detail_default", dark = false) {
        ToolDetailContent(state = ToolsPreview.toolDetailDefault())
    }

    @Test
    fun tool_detail_schema_error_light() = snapshot(name = "tool_detail_schema_error", dark = false) {
        ToolDetailContent(state = ToolsPreview.toolDetailSchemaError())
    }

    @Test
    fun add_mcp_default_light() = snapshot(name = "add_mcp_default", dark = false) {
        McpServerConfigContent(form = ToolsPreview.addMcpDefault())
    }

    @Test
    fun add_mcp_invalid_light() = snapshot(name = "add_mcp_invalid", dark = false) {
        McpServerConfigContent(form = ToolsPreview.addMcpInvalid())
    }

    @Test
    fun edit_mcp_with_headers_light() = snapshot(name = "edit_mcp_with_headers", dark = false) {
        McpServerConfigContent(form = ToolsPreview.editMcpWithHeaders())
    }

    private fun snapshot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                KnotworkTheme(darkTheme = dark) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/tools_${name}_$themeTag.png",
        )
    }
}

internal object ToolsPreview {

    private fun builtIns(): List<BuiltInToolRow> = listOf(
        BuiltInToolRow(
            id = "search_tool",
            name = "search_tool",
            description = "Wikipedia lookup · returns concise summary",
            risk = BuiltInToolRisk.ReadOnly,
            enabled = true,
        ),
        BuiltInToolRow(
            id = "schedule_task",
            name = "schedule_task",
            description = "Run a task later · WorkManager · one-off or recurring",
            risk = BuiltInToolRisk.Sensitive,
            enabled = true,
        ),
        BuiltInToolRow(
            id = "delegate_task",
            name = "delegate_task",
            description = "Hand subtask to cloud LLM · stores result in memory",
            risk = BuiltInToolRisk.Destructive,
            enabled = false,
        ),
    )

    private fun servers(): List<McpServerRow> = listOf(
        McpServerRow(
            id = "mcp://arxiv-search.local:7411",
            url = "mcp://arxiv-search.local:7411",
            toolCount = 3,
            latencyLabel = "42 ms",
            state = McpConnectionState.Connected,
        ),
        McpServerRow(
            id = "https://mcp.example.com/agents",
            url = "https://mcp.example.com/agents",
            toolCount = 8,
            latencyLabel = "318 ms",
            state = McpConnectionState.Connected,
        ),
        McpServerRow(
            id = "mcp://files.local:7410",
            url = "mcp://files.local:7410",
            toolCount = 2,
            latencyLabel = "disabled",
            state = McpConnectionState.Disabled,
        ),
    )

    fun empty(): ToolsViewState = ToolsViewState(visualState = ToolsVisualState.Empty)

    fun loading(): ToolsViewState = ToolsViewState(visualState = ToolsVisualState.Loading)

    fun default(): ToolsViewState = ToolsViewState(
        visualState = ToolsVisualState.Default,
        builtInTools = builtIns(),
        mcpServers = servers(),
    )

    fun error(): ToolsViewState = ToolsViewState(
        visualState = ToolsVisualState.Error,
        errorMessage = "Tool discovery handshake failed: connection refused.",
    )

    fun toolDetailDefault(): ToolDetailViewState = ToolDetailViewState(
        visualState = ToolDetailVisualState.Default,
        toolName = "shell.run",
        description = "Run a shell command on the MCP server.",
        serverDisplayName = "example.com",
        schemaJson = """{
  "name": "shell.run",
  "type": "object",
  "properties": {
    "command": { "type": "string" }
  }
}""",
        lastUsed = "2 hours ago",
        enabled = true,
    )

    fun toolDetailSchemaError(): ToolDetailViewState = ToolDetailViewState(
        visualState = ToolDetailVisualState.SchemaError,
        toolName = "broken.tool",
        description = "Server returned malformed JSON-Schema.",
        serverDisplayName = "example.com",
        schemaJson = null,
        lastUsed = null,
        enabled = false,
    )

    fun addMcpDefault(): AddMcpServerForm = AddMcpServerForm(url = "https://server.example.com/mcp")

    fun addMcpInvalid(): AddMcpServerForm = AddMcpServerForm(
        url = "server.example.com",
        urlError = "URL must start with http:// or https://.",
    )

    fun editMcpWithHeaders(): AddMcpServerForm = AddMcpServerForm(
        url = "https://hf.example/mcp",
        name = "HuggingFace MCP",
        transport = McpTransportOption.StreamableHttp,
        headers = listOf(McpHeaderRow(key = "Authorization", value = "Bearer hf_secret")),
        editingUrl = "https://hf.example/mcp",
    )
}
