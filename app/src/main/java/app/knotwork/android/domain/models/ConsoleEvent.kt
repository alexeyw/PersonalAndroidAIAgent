package app.knotwork.android.domain.models

/**
 * Atomic record of a single notable action surfaced through the agent console
 * (collapsed mini-console above the input bar and the expanded full-log
 * bottom sheet).
 *
 * Events are produced by [app.knotwork.android.domain.engine.GraphExecutionEngine]
 * during pipeline execution and forwarded to the UI through
 * [AgentOrchestratorState.ConsoleLog]. They are display-oriented (already
 * formatted human-readable strings). Events of a persisted run are also
 * written through to the run trace
 * ([app.knotwork.android.domain.repositories.RunTraceRepository]) so the
 * console can replay them after the UI reattaches to a background run.
 *
 * @property timestamp Wall-clock time of the event in `System.currentTimeMillis()`
 *   units. Used by the UI to render the leading `HH:mm:ss(.SSS)` timecode.
 * @property type Category of the event. Drives line color and prefix in the
 *   collapsed console and the filter chips in the expanded console.
 * @property message Pre-formatted human-readable text shown verbatim in the
 *   console (e.g. `"▶ LITE_RT"`, `"calendar_create_event"`, `"Memory: 3 chunks
 *   retrieved"`).
 * @property seq Zero-based monotonic position of the event within its pipeline
 *   run. The console replay/live seam deduplicates by this number, so it is
 *   unique within a run; `0` for events emitted outside a persisted run.
 * @property depth Pipeline-nesting level of the run that produced the event:
 *   `0` for the top-level run, `1` for a direct sub-pipeline, and so on. The
 *   console renders the event indented by this level so nested sub-pipeline
 *   output reads as a hierarchy; the event [message] of a nested run is also
 *   already prefixed with `[<sub-pipeline name>]` by the engine.
 */
data class ConsoleEvent(
    val timestamp: Long,
    val type: ConsoleEventType,
    val message: String,
    val seq: Long = 0,
    val depth: Int = 0,
)

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
