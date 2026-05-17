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
 */
data class McpServerRow(
    val id: String,
    val url: String,
    val toolCount: Int,
    val latencyLabel: String,
    val state: McpConnectionState,
)

/**
 * State of the inline "Add new MCP server URL" form. Non-null means the
 * form is visible at the bottom of the surface; the host owns the
 * lifecycle.
 */
data class AddMcpServerForm(val url: String = "", val urlError: String? = null, val submitting: Boolean = false) {
    val canSubmit: Boolean get() = url.isNotBlank() && urlError == null && !submitting
}

/**
 * Top-level immutable input to `ToolsContent`.
 *
 * @property visualState which of the documented states to render.
 * @property builtInTools rows in the "Built-in (AppFunctions)" section.
 * @property mcpServers rows in the "MCP servers" section.
 * @property addServerForm non-null when the inline add-server form is
 * visible; `null` hides the form entirely.
 * @property errorMessage user-visible error rendered in
 * [ToolsVisualState.Error]; `null` otherwise.
 */
data class ToolsViewState(
    val visualState: ToolsVisualState,
    val builtInTools: List<BuiltInToolRow> = emptyList(),
    val mcpServers: List<McpServerRow> = emptyList(),
    val addServerForm: AddMcpServerForm? = null,
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
    val onAddServerOpen: () -> Unit = {},
    val onAddServerUrlChange: (String) -> Unit = {},
    val onAddServerSubmit: () -> Unit = {},
    val onAddServerCancel: () -> Unit = {},
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

// -------------------- AddMcpServerScreen (legacy modal route) --------------------

/**
 * Top-level input to `AddMcpServerContent`. The redesigned tools screen
 * ships the add-server form inline, but the modal route is preserved so
 * deep links keep working until the standalone screen is retired.
 */
data class AddMcpServerViewState(val url: String, val urlError: String? = null, val submitting: Boolean = false) {
    val canSubmit: Boolean get() = url.isNotBlank() && urlError == null && !submitting
}

class AddMcpServerCallbacks(
    val onUrlChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

fun noopAddMcpServerCallbacks(): AddMcpServerCallbacks = AddMcpServerCallbacks()
