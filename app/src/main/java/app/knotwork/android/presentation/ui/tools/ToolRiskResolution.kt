package app.knotwork.android.presentation.ui.tools

import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.McpTool
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.models.ToolSource
import app.knotwork.design.screens.tools.BuiltInToolRisk

/**
 * Resolves the risk level the Tools surfaces show, the way
 * `ToolRepositoryImpl.getRisk` resolves the one the approval gate enforces.
 *
 * There is one rule worth stating, because getting it wrong is invisible: a
 * built-in tool's risk comes from the code and the gate returns on it **before**
 * reading any override, while a discovered AppFunction or an MCP tool resolves
 * `override ?: SENSITIVE`. Applying an override to a built-in here would make
 * the list row and the detail screen agree with each other and disagree with the
 * gate — the worse of the two failures, since it would be a screen quietly
 * promising a call would not be stopped.
 */
internal object ToolRiskResolution {

    /**
     * The level shown for a local tool — built-in or discovered AppFunction.
     *
     * A `null` [tool] is an id the catalogue does not contain (a stale deep
     * link). It resolves to `SENSITIVE` for the same reason the gate does: an
     * unknown tool is not a safe one.
     *
     * @param tool The tool, whose [AgentTool.source] decides whether an override
     *   applies at all; `null` when the id matched none.
     * @param overrides The user's persisted decisions, keyed by tool name.
     * @return The level the gate will enforce, as the catalog's UI enum.
     */
    fun forLocalTool(tool: AgentTool?, overrides: Map<String, ToolRisk>): BuiltInToolRisk = when {
        tool == null -> ToolRisk.SENSITIVE.toUi()
        tool.source == ToolSource.APP_FUNCTION -> forOverridable(tool.name, overrides)
        else -> (tool.risk ?: ToolRisk.SENSITIVE).toUi()
    }

    /**
     * The level shown for an MCP tool, keyed by its server-scoped id so the same
     * tool name on two servers stays two decisions.
     *
     * @param tool The advertised tool.
     * @param overrides The user's persisted decisions.
     * @return The level the gate will enforce, as the catalog's UI enum.
     */
    fun forMcpTool(tool: McpTool, overrides: Map<String, ToolRisk>): BuiltInToolRisk =
        forOverridable(tool.id, overrides)

    /**
     * The level for a tool whose risk the user owns. An absent entry is not "no
     * risk": it is `SENSITIVE`, the conservative default the gate itself falls
     * back to for anything the app cannot inspect.
     *
     * @param toolKey The override key the gate will look up.
     * @param overrides The user's persisted decisions.
     * @return The level the gate will enforce, as the catalog's UI enum.
     */
    fun forOverridable(toolKey: String, overrides: Map<String, ToolRisk>): BuiltInToolRisk =
        (overrides[toolKey] ?: ToolRisk.SENSITIVE).toUi()

    /**
     * Whether the detail screen should offer a control for a **local** tool
     * rather than state its level. True exactly when the gate consults the
     * override — which a tool the catalogue does not contain never does, so an
     * id that resolves to nothing gets no control either.
     *
     * @param tool The local tool, or `null` when the id matched none.
     * @return `true` when the user's choice would be read.
     */
    fun isOverridable(tool: AgentTool?): Boolean = tool != null && tool.source == ToolSource.APP_FUNCTION

    /** Maps a domain risk level onto the catalog's UI enum. */
    fun ToolRisk.toUi(): BuiltInToolRisk = when (this) {
        ToolRisk.READ_ONLY -> BuiltInToolRisk.ReadOnly
        ToolRisk.SENSITIVE -> BuiltInToolRisk.Sensitive
        ToolRisk.DESTRUCTIVE -> BuiltInToolRisk.Destructive
    }

    /** Maps the catalog's UI enum back onto the domain risk level. */
    fun BuiltInToolRisk.toDomain(): ToolRisk = when (this) {
        BuiltInToolRisk.ReadOnly -> ToolRisk.READ_ONLY
        BuiltInToolRisk.Sensitive -> ToolRisk.SENSITIVE
        BuiltInToolRisk.Destructive -> ToolRisk.DESTRUCTIVE
    }
}
