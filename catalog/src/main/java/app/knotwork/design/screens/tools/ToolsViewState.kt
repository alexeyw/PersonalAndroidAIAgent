package app.knotwork.design.screens.tools

import app.knotwork.design.components.chips.Status

/**
 * Visual variant of the tools surface. Mirrors
 * `compose/screens/README.md §C4 · Tools / MCP`.
 */
enum class ToolsVisualState {
    /** No MCP servers configured (built-in tools still rendered as their own section). */
    Empty,

    /** Initial discovery handshake (≤1.5 s); section skeletons rendered. */
    Loading,

    /** Default state — one or more sections render their tools normally. */
    Default,

    /** Failed to load any tool list. Full-screen error state. */
    Error,
}

/** Connection state of one MCP server. */
enum class McpConnectionState {
    Connected,
    Disconnected,
    Syncing,
    Error,
}

/**
 * Lightweight projection of one tool surfaced inside a section row.
 */
data class ToolRowState(
    val id: String,
    val name: String,
    val description: String,
    val serverId: String,
    val enabled: Boolean,
)

/**
 * Section block surfaced in the tools surface — one per MCP server plus
 * one synthetic block for the device's local tools.
 *
 * @property serverId stable identity (`"local"` for the built-in section).
 * @property displayName human-readable server name.
 * @property subtitle optional secondary line under the header (URL, etc.).
 * @property connectionState live connection state for the chip.
 * @property tools tool rows surfaced inside the block.
 * @property errorMessage optional inline error tile inside the block.
 */
data class ToolsSectionBlock(
    val serverId: String,
    val displayName: String,
    val subtitle: String?,
    val connectionState: McpConnectionState,
    val tools: List<ToolRowState>,
    val errorMessage: String? = null,
) {
    /** Maps the connection state to the catalog `Status` enum for `StatusPill`. */
    val statusPill: Status
        get() = when (connectionState) {
            McpConnectionState.Connected -> Status.Success
            McpConnectionState.Disconnected -> Status.Warning
            McpConnectionState.Syncing -> Status.Idle
            McpConnectionState.Error -> Status.Error
        }

    companion object {
        /** Reserved server id used for the built-in local tools section. */
        const val LOCAL_SERVER_ID: String = "local"
    }
}

/**
 * Top-level immutable input to `ToolsContent`. Mirrors
 * `compose/screens/README.md §C4`.
 */
data class ToolsViewState(
    val visualState: ToolsVisualState,
    val sections: List<ToolsSectionBlock> = emptyList(),
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
    val onServerReconnect: (serverId: String) -> Unit = {},
    val onServerRemove: (serverId: String) -> Unit = {},
    val onAddMcpServer: () -> Unit = {},
    val onLearnAboutMcp: () -> Unit = {},
    val onErrorRetry: () -> Unit = {},
    val onDebugCycleConnection: (serverId: String) -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopToolsCallbacks(): ToolsCallbacks = ToolsCallbacks()

// -------------------- ToolDetailScreen --------------------

/**
 * Visual variant of the per-tool detail surface.
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

// -------------------- AddMcpServerScreen --------------------

/**
 * Top-level input to `AddMcpServerContent`. The host owns URL validation;
 * the catalog only reflects [urlError].
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
