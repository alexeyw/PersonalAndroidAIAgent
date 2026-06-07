package app.knotwork.android.domain.models

/**
 * Atomic record of a single notable action surfaced through the agent console
 * (collapsed mini-console above the input bar and the expanded full-log
 * bottom sheet).
 *
 * Events are produced by [app.knotwork.android.domain.engine.GraphExecutionEngine]
 * during pipeline execution and forwarded to the UI through
 * [AgentOrchestratorState.ConsoleLog]. They are display-oriented (already
 * formatted human-readable strings) and intentionally not persisted — the log
 * resets on every new send / session switch.
 *
 * @property timestamp Wall-clock time of the event in `System.currentTimeMillis()`
 *   units. Used by the UI to render the leading `HH:mm:ss(.SSS)` timecode.
 * @property type Category of the event. Drives line color and prefix in the
 *   collapsed console and the filter chips in the expanded console.
 * @property message Pre-formatted human-readable text shown verbatim in the
 *   console (e.g. `"▶ LITE_RT"`, `"calendar_create_event"`, `"Memory: 3 chunks
 *   retrieved"`).
 */
data class ConsoleEvent(val timestamp: Long, val type: ConsoleEventType, val message: String)

/**
 * Category of a [ConsoleEvent]. Modelled as a sealed interface with `data
 * object` variants so the renderer can pattern-match exhaustively and the
 * filter chips map 1-to-1 to types without a magic-string lookup.
 */
sealed interface ConsoleEventType {
    /** Lifecycle of a pipeline node — start (`▶`) or completion (`✓`). */
    data object NodeExecution : ConsoleEventType

    /** A TOOL node successfully resolved and invoked an agent tool. */
    data object ToolCall : ConsoleEventType

    /** Long-term memory was queried for relevant chunks. */
    data object MemoryAccess : ConsoleEventType

    /** Pipeline-scoped lifecycle message (started, completed). Rendered muted. */
    data object SystemMessage : ConsoleEventType

    /** A failure surfaced from any pipeline subsystem. Rendered in error color. */
    data object Error : ConsoleEventType
}
