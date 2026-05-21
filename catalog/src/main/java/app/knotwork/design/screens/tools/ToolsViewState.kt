package app.knotwork.design.screens.tools

/**
 * Visual variant of the tools surface. Mirrors the second-pass mockup
 * (`compose/screens/<C4-mockup>.png`, Phase 21 / Task 10):
 *  - `Empty` is reserved for the (rare) state where neither built-in
 *    tools nor MCP servers exist.
 *  - `Loading` is rendered during the initial discovery handshake.
 *  - `Default` is the standard surface — built-in section + MCP section
 *    + optional inline add-server form.
 *  - `Error` covers a hard failure where neither section can render.
 */
enum class ToolsVisualState {
    Empty,
    Loading,
    Default,
    Error,
}

/** Risk tier surfaced as the outline pill next to a built-in tool name. */
enum class BuiltInToolRisk { ReadOnly, Sensitive, Destructive }

/** Connection state of one MCP server — drives the leading status dot. */
enum class McpConnectionState {
    Connected,
    Disconnected,
    Syncing,
    Error,
    Disabled,
}

/**
 * One built-in AppFunction row in the "Built-in (AppFunctions)" section.
 *
 * @property id stable identity (matches the AppFunction id).
 * @property name display name; rendered in monospace.
 * @property description body text under the title.
 * @property risk risk tier rendered as the outline pill next to the name.
 * @property enabled toggle state — wired to the trailing Switch.
 */
data class BuiltInToolRow(
    val id: String,
    val name: String,
    val description: String,
    val risk: BuiltInToolRisk,
    val enabled: Boolean,
)

/**
 * One MCP server row.
 *
 * @property id stable identity (the server URL).
 * @property url full server URL; rendered in monospace.
 * @property toolCount number of tools exposed by the server.
 * @property latencyLabel pre-formatted secondary line — e.g. `"42 ms"` for
 * a connected server, `"disabled"` when the user has paused it, or
 * `"…"` while the catalog is still measuring.
 * @property state connection state driving the leading status dot.
 * @property tools tools advertised by the server. Rendered as a nested list
 * underneath the server row when [expanded] is `true`.
 * @property expanded `true` when the user has tapped the server row to
 * reveal [tools]; `false` keeps the row collapsed and only the header
 * visible. Defaults to collapsed so the surface stays scannable.
 */
data class McpServerRow(
    val id: String,
    val url: String,
    val toolCount: Int,
    val latencyLabel: String,
    val state: McpConnectionState,
    val tools: List<McpToolEntry> = emptyList(),
    val expanded: Boolean = false,
)

/**
 * One MCP tool entry rendered underneath its server when the server row
 * is expanded. Mirrors [BuiltInToolRow] visually but is distinct because
 * MCP tools carry a per-server affiliation (id encoded as
 * `mcp:<sha8(serverUrl)>:<toolName>`) and surface a remote JSON-Schema in
 * the detail screen rather than the local AppFunction metadata.
 *
 * @property id stable, route-safe identifier; the host serialises this as
 * the `{toolId}` path argument when navigating to `ToolDetailScreen`.
 * @property name tool name as advertised by the server.
 * @property description body text under the title.
 * @property risk risk tier rendered as the outline pill next to the name.
 * @property enabled toggle state — wired to the trailing Switch.
 */
data class McpToolEntry(
    val id: String,
    val name: String,
    val description: String,
    val risk: BuiltInToolRisk,
    val enabled: Boolean,
)

/**
 * Transport mode selector shown in the MCP server form. Mirrors
 * `domain.models.McpTransport` to keep the catalog free of app
 * dependencies; the host translates between the two.
 */
enum class McpTransportOption(val label: String) {
    SSE(label = "SSE"),
    StreamableHttp(label = "Streamable HTTP"),
}

/** One header key/value pair authored in the MCP server form. */
data class McpHeaderRow(val key: String = "", val value: String = "")

/**
 * State of the inline MCP-server form.
 *
 * Non-null means the form is visible at the bottom of the surface.
 * Carries enough state for both Add (when [editingUrl] is null) and
 * Edit (when [editingUrl] is the original URL of the row being
 * edited) flows.
 *
 * @property url the URL field (required).
 * @property urlError inline validation error for [url]; null when valid.
 * @property name optional display label; rendered alongside the URL.
 * @property transport active transport selection.
 * @property headers repeating list of header rows ("Authorization",
 * "Bearer …"). May contain empty rows the user has not finished
 * filling out.
 * @property submitting suppresses double-submits while the host is
 * persisting the change.
 * @property editingUrl when non-null, the form is in Edit mode; the
 * host will update the server identified by this URL instead of
 * appending a new row.
 */
data class AddMcpServerForm(
    val url: String = "",
    val urlError: String? = null,
    val name: String = "",
    val transport: McpTransportOption = McpTransportOption.SSE,
    val headers: List<McpHeaderRow> = emptyList(),
    val submitting: Boolean = false,
    val editingUrl: String? = null,
) {
    val canSubmit: Boolean get() = url.isNotBlank() && urlError == null && !submitting
    val isEdit: Boolean get() = editingUrl != null
}

/**
 * Top-level immutable input to `ToolsContent`.
 *
 * @property visualState which of the documented states to render.
 * @property builtInTools rows in the "Built-in (AppFunctions)" section.
 * @property mcpServers rows in the "MCP servers" section.
 * @property errorMessage user-visible error rendered in
 * [ToolsVisualState.Error]; `null` otherwise.
 */
data class ToolsViewState(
    val visualState: ToolsVisualState,
    val builtInTools: List<BuiltInToolRow> = emptyList(),
    val mcpServers: List<McpServerRow> = emptyList(),
    val errorMessage: String? = null,
) {
    init {
        require((visualState == ToolsVisualState.Error) == (errorMessage != null)) {
            "errorMessage must be non-null iff visualState == Error"
        }
    }
}

@Suppress("LongParameterList")
class ToolsCallbacks(
    val onToolToggle: (toolId: String, enabled: Boolean) -> Unit = { _, _ -> },
    val onToolClick: (toolId: String) -> Unit = {},
    val onServerRemove: (serverId: String) -> Unit = {},
    val onServerEdit: (serverId: String) -> Unit = {},
    val onServerExpandToggle: (serverId: String) -> Unit = {},
    val onServerRefresh: (serverId: String) -> Unit = {},
    val onMcpToolToggle: (toolId: String, enabled: Boolean) -> Unit = { _, _ -> },
    val onMcpToolClick: (toolId: String) -> Unit = {},
    val onAddServerOpen: () -> Unit = {},
    val onErrorRetry: () -> Unit = {},
    val onOpenDrawer: () -> Unit = {},
    val onTopOverflow: () -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopToolsCallbacks(): ToolsCallbacks = ToolsCallbacks()

// -------------------- ToolDetailScreen --------------------

/**
 * Visual variant of the per-tool detail surface (kept for parity with the
 * existing `ToolDetailScreen` route).
 */
enum class ToolDetailVisualState {
    Loading,
    Default,
    SchemaError,
}

/** Top-level input to `ToolDetailContent`. */
data class ToolDetailViewState(
    val visualState: ToolDetailVisualState,
    val toolName: String,
    val description: String,
    val serverDisplayName: String,
    val schemaJson: String? = null,
    val lastUsed: String? = null,
    val enabled: Boolean,
)

class ToolDetailCallbacks(val onBack: () -> Unit = {}, val onToggle: (Boolean) -> Unit = {})

fun noopToolDetailCallbacks(): ToolDetailCallbacks = ToolDetailCallbacks()

// -------------------- McpServerConfigContent --------------------

/**
 * Callback bundle for the standalone MCP server configuration screen.
 * Mirrors the per-field shape of [AddMcpServerForm].
 */
@Suppress("LongParameterList")
class McpServerConfigCallbacks(
    val onUrlChange: (String) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onTransportSelect: (McpTransportOption) -> Unit = {},
    val onHeaderAdd: () -> Unit = {},
    val onHeaderChange: (index: Int, key: String, value: String) -> Unit = { _, _, _ -> },
    val onHeaderRemove: (index: Int) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

fun noopMcpServerConfigCallbacks(): McpServerConfigCallbacks = McpServerConfigCallbacks()
