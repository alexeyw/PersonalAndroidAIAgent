package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import app.knotwork.design.components.console.ConsoleLevel
import app.knotwork.design.components.console.ConsoleLine
import app.knotwork.design.components.console.ConsoleSource
import app.knotwork.design.components.console.ConsoleTraceSpan
import app.knotwork.design.components.console.ConsoleVarRow
import app.knotwork.design.components.console.SpanStatus
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure-Kotlin mappers that bridge the domain orchestrator output to the
 * catalog console-pane row models consumed by `ChatHomeContent`. Lives in
 * `:app` because the catalog layer cannot reach `ai.agent.android.*` types
 * (Clean Architecture + `decisions.md §3`).
 *
 * All public functions are deterministic and free of Android dependencies
 * beyond `org.json.JSONObject` (already used elsewhere in the chat-home
 * presentation layer), so they remain trivially unit-testable.
 */

/** Pre-formatted clock pattern used in [ConsoleLine.timestamp]. */
internal const val CONSOLE_LINE_TIMESTAMP_PATTERN: String = "HH:mm:ss.SSS"

/**
 * Maps a single domain [ConsoleEvent] to the catalog [ConsoleLine] consumed
 * by the Logs tab of the chat-home console pane. The mapping is total —
 * every value of [ConsoleEventType] is covered, so the renderer never has
 * to fall back to a default branch.
 *
 * @param event Domain event accumulated by `GraphExecutionEngine`.
 * @return Display-oriented row with a millisecond-precision local timestamp
 *   string, an attribution [ConsoleSource], and a severity [ConsoleLevel].
 */
fun ConsoleEvent.toConsoleLine(): ConsoleLine = ConsoleLine(
    timestamp = SimpleDateFormat(CONSOLE_LINE_TIMESTAMP_PATTERN, Locale.getDefault())
        .format(Date(timestamp)),
    source = type.toConsoleSource(),
    level = type.toConsoleLevel(),
    text = message,
)

/**
 * Projects the [ConsoleEventType] taxonomy onto the [ConsoleSource]
 * attribution shown next to each [ConsoleLine].
 */
internal fun ConsoleEventType.toConsoleSource(): ConsoleSource = when (this) {
    ConsoleEventType.NodeExecution -> ConsoleSource.NODE
    ConsoleEventType.ToolCall -> ConsoleSource.TOOL
    ConsoleEventType.MemoryAccess -> ConsoleSource.RUNTIME
    ConsoleEventType.SystemMessage -> ConsoleSource.RUNTIME
    ConsoleEventType.Error -> ConsoleSource.RUNTIME
}

/**
 * Projects the [ConsoleEventType] taxonomy onto the [ConsoleLevel]
 * severity. Errors are surfaced with the `Error` level; everything else is
 * informational. The legacy collapsed-console renderer rendered tool calls
 * as `Warn` (yellow); the redesigned Knotwork pane reserves `Warn` for
 * actual warnings, so tool calls fall back to `Info`.
 */
internal fun ConsoleEventType.toConsoleLevel(): ConsoleLevel = when (this) {
    ConsoleEventType.Error -> ConsoleLevel.Error
    ConsoleEventType.NodeExecution -> ConsoleLevel.Trace
    ConsoleEventType.MemoryAccess,
    ConsoleEventType.ToolCall,
    ConsoleEventType.SystemMessage,
    -> ConsoleLevel.Info
}

/**
 * Projects a [AgentOrchestratorState.TraceStep] onto a [ConsoleTraceSpan]
 * consumed by the Traces tab. The catalog row carries a pre-formatted
 * [ConsoleTraceSpan.startedAt] string, so the caller threads the wall-clock
 * timestamp it observed when the trace step landed.
 *
 * @param trace Domain trace step emitted by `GraphExecutionEngine`.
 * @param startedAtMs Wall-clock time the trace step landed, in
 *   `System.currentTimeMillis()` units.
 * @return Span row with `Ok` status — the engine emits trace steps only for
 *   successful node completions; failures short-circuit the flow with an
 *   `Error` orchestrator state before any trace would be appended.
 */
fun traceStepToConsoleSpan(trace: AgentOrchestratorState.TraceStep, startedAtMs: Long): ConsoleTraceSpan =
    ConsoleTraceSpan(
        name = trace.nodeName,
        durationMs = trace.durationMs,
        startedAt = SimpleDateFormat(CONSOLE_LINE_TIMESTAMP_PATTERN, Locale.getDefault())
            .format(Date(startedAtMs)),
        status = SpanStatus.Ok,
    )

/**
 * Projects a single [AgentOrchestratorState.NodeIO] snapshot onto the pair
 * of [ConsoleVarRow]s rendered for that node in the Vars tab. The grouping
 * header on each row is the node label (`<TYPE>#<id-prefix>`), so the
 * catalog `ConsoleVarsBody` can collate input/output pairs of the same
 * node into a single section.
 *
 * The raw values are escaped through [JSONObject.quote] so multi-line and
 * special-character payloads render as a single safe JSON string without
 * breaking the row layout.
 *
 * @param io Per-node I/O snapshot.
 * @return Ordered `[input, output]` row pair.
 */
fun nodeIoToVarRows(io: AgentOrchestratorState.NodeIO): List<ConsoleVarRow> {
    val node = formatNodeLabel(io.nodeType, io.nodeId)
    return listOf(
        ConsoleVarRow(node = node, key = CONSOLE_VAR_KEY_INPUT, valueJson = JSONObject.quote(io.input)),
        ConsoleVarRow(node = node, key = CONSOLE_VAR_KEY_OUTPUT, valueJson = JSONObject.quote(io.output)),
    )
}

/**
 * Formats the grouping header for a [ConsoleVarRow] — concatenates the
 * node type with a short id suffix so two `LITE_RT` nodes in the same
 * pipeline remain distinguishable in the Vars tab.
 */
internal fun formatNodeLabel(nodeType: String, nodeId: String): String {
    val shortId = nodeId.take(NODE_ID_SHORT_LENGTH)
    return "$nodeType#$shortId"
}

/** Length of the node-id suffix appended to the grouping header. */
private const val NODE_ID_SHORT_LENGTH: Int = 6

/** Key rendered for the input row of a [AgentOrchestratorState.NodeIO]. */
internal const val CONSOLE_VAR_KEY_INPUT: String = "input"

/** Key rendered for the output row of a [AgentOrchestratorState.NodeIO]. */
internal const val CONSOLE_VAR_KEY_OUTPUT: String = "output"
