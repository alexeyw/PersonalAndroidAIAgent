package app.knotwork.android.domain.models

/**
 * Per-tool risk classification used by the Human-in-the-Loop (HITL) gate to decide
 * whether a tool invocation requires explicit user confirmation.
 *
 * The classification is **policy, not capability**: it expresses the assumed
 * side-effect profile of the tool, not what the tool literally does. The single
 * source of truth for resolving a given tool name to a [ToolRisk] is
 * `ToolRepository.getRisk` (built-in defaults + per-AppFunction overrides + MCP
 * blanket policy).
 *
 * Consumption by the HITL gate is wired up in a follow-up task; this enum ships
 * the data model so that the gate has something to read against.
 */
enum class ToolRisk {
    /**
     * Pure read-only tool with no externally observable side effects (no writes,
     * no network mutations, no user-data changes). The HITL gate is expected to
     * allow these to run without prompting unless the user has explicitly opted
     * into "ask on every tool call" via the global confirmation flag.
     */
    READ_ONLY,

    /**
     * Tool whose effects are reversible or limited in scope but still
     * user-observable (e.g. scheduling a background task, delegating to a cloud
     * model, writing to local app state). HITL prompt is required by default.
     */
    SENSITIVE,

    /**
     * Tool whose effects are hard or impossible to reverse (sending messages,
     * deleting files, purchases, system-level mutations). HITL prompt is always
     * required and cannot be silenced by the global flag.
     */
    DESTRUCTIVE,
}
