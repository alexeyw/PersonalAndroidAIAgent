package app.knotwork.design.components.console

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Discrete snap points for the [ConsolePane] sheet. The screen tweens
 * between heights via `motionLg`; the catalog composable simply renders at
 * the given snap so previews and snapshots remain deterministic.
 */
enum class ConsoleSnap(val height: Dp) {
    /** Drag handle + header only — last log line ticker visible. */
    Peek(height = 44.dp),

    /** ≈ 40 % of screen — active monitoring during a pipeline run. */
    Partial(height = 360.dp),

    /** ≈ 90 % of screen — triage; chat barely visible underneath. */
    Full(height = 720.dp),
}

/** Top-level tabs inside the [ConsolePane] header strip. */
enum class ConsoleTab {
    /** Chronological log of runtime events. */
    Logs,

    /** Per-node key/value pairs captured during the active run. */
    Vars,

    /** Flat list of [ConsoleTraceSpan]s with relative duration bars. */
    Traces,
}

/** Source attribution shown next to each [ConsoleLine] and used by [ConsoleFilter]. */
enum class ConsoleSource {
    /** Output emitted from a pipeline node executor. */
    NODE,

    /** Output emitted from a tool invocation. */
    TOOL,

    /** Output emitted by the engine runtime (orchestrator, scheduler). */
    RUNTIME,

    /** Output attributed to a user interaction (e.g. clarification reply). */
    USER,
}

/** Severity of a [ConsoleLine]. Drives the leading colour bar in the row. */
enum class ConsoleLevel {
    /** Verbose log line. Default colour. */
    Trace,

    /** Informational log line. Default colour. */
    Info,

    /** Warning. Renders the line with the `signalWarn` accent strip. */
    Warn,

    /** Error. Renders the line with the `signalError` accent strip. */
    Error,
}

/**
 * Source-side filter applied to the [ConsolePane] log list. Empty
 * [sources] yields zero rows; the default set in [allOn] preserves the
 * historical "show everything" behaviour.
 *
 * @property sources sources currently enabled.
 */
data class ConsoleFilter(val sources: Set<ConsoleSource>) {

    /** Predicate used by the LazyColumn to drop filtered-out lines. */
    fun matches(line: ConsoleLine): Boolean = line.source in sources

    companion object {
        /** Filter that lets every source through — the default. */
        val allOn: ConsoleFilter = ConsoleFilter(sources = ConsoleSource.entries.toSet())
    }
}

/**
 * One log entry surfaced by the [ConsolePane] Logs tab.
 *
 * @property timestamp pre-formatted local time string with millisecond
 * precision (`HH:mm:ss.SSS`). Formatting is the caller's responsibility —
 * the catalog never formats dates.
 * @property source attribution; drives the source tag rendered before the
 * message.
 * @property level severity; drives the accent strip colour.
 * @property text plain-text log body.
 */
data class ConsoleLine(val timestamp: String, val source: ConsoleSource, val level: ConsoleLevel, val text: String)

/**
 * One key/value pair surfaced inside the Vars tab, grouped by [node].
 *
 * @property node id of the producing node (rendered as a section header).
 * @property key variable name.
 * @property valueJson already-serialised value (the catalog never parses
 * JSON; the screen passes a rendered fragment).
 */
data class ConsoleVarRow(val node: String, val key: String, val valueJson: String)

/** Outcome of a [ConsoleTraceSpan]. Drives the trailing chip in the trace row. */
enum class SpanStatus {
    /** Span completed successfully — `signalSuccess` chip. */
    Ok,

    /** Span failed — `signalError` chip. */
    Error,
}

/**
 * One span rendered inside the Traces tab.
 *
 * @property name human-readable span name (e.g. node id or tool name).
 * @property durationMs span duration in milliseconds.
 * @property startedAt pre-formatted start timestamp (`HH:mm:ss.SSS`).
 * @property status terminal status — `Ok` / `Error`.
 */
data class ConsoleTraceSpan(val name: String, val durationMs: Long, val startedAt: String, val status: SpanStatus)
