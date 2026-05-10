package ai.agent.android.presentation.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Maximum number of console lines visible in the collapsed mini-console.
 * Older entries remain in [ai.agent.android.presentation.ui.chat.ChatUiState.consoleLines]
 * for the expanded view (Phase 17.5) but are not rendered here.
 */
private const val COLLAPSED_LINE_LIMIT = 3

/**
 * Target minimum height of the panel. Used via [heightIn] so the strip can
 * grow vertically when the user enables a large font scale in accessibility
 * settings — pinning to a fixed height would clip lines under those scales.
 * Sized to fit three lines at the tight [LineHeight] with minimal vertical
 * padding so the panel feels like a status strip, not a half-empty block.
 */
private val PanelHeight = 40.dp

/** Compact monospace font size used for every console line. */
private val LineFontSize = 11.sp

/**
 * Explicit line height tightened against the default ~16sp Material runtime
 * picks for 11sp. The lines stack flush to make the strip feel dense — the
 * spec asked for a status strip with minimal whitespace.
 */
private val LineHeight = 12.sp

/** Alpha applied to neutral / muted line colors so they stay legible but recede. */
private const val NeutralAlpha = 0.6f
private const val MutedAlpha = 0.4f

/**
 * Always-on agent console rendered as a thin strip below the chat input.
 *
 * The composable is stateless: callers pass the event list, the current
 * orchestrator state (when generating), and decide whether to show the panel
 * at all. Visibility gating lives in `ChatScreen` so the panel can disappear
 * entirely when the agent is idle and the log is empty.
 *
 * Layout from top to bottom:
 *  1. Optional [AgentThoughtIndicator] line (and Approve/Deny row when the
 *     orchestrator is awaiting approval). Phase 17.4 absorbed the standalone
 *     thought card into this surface so the agent area reads as one block in
 *     a unified monospace style.
 *  2. Last [COLLAPSED_LINE_LIMIT] [ConsoleEvent]s, freshest at the bottom so
 *     the user's eye (drawn to the input) lands on the most recent activity.
 *
 * @param events Console events to render. Only the last [COLLAPSED_LINE_LIMIT]
 *   are displayed; the caller does not need to truncate.
 * @param currentState Current orchestrator state. Pass `null` to suppress the
 *   thought line entirely (e.g. when the agent is idle).
 * @param onApprove Forwarded to the embedded thought line for the
 *   [AgentOrchestratorState.WaitingForApproval] action row.
 * @param onDeny Forwarded to the embedded thought line for the
 *   [AgentOrchestratorState.WaitingForApproval] action row.
 * @param modifier Optional layout modifier applied to the panel container.
 */
@Composable
fun ConsolePanelCollapsed(
    events: List<ConsoleEvent>,
    currentState: AgentOrchestratorState? = null,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val visibleEvents = remember(events) { events.takeLast(COLLAPSED_LINE_LIMIT) }

    Surface(
        // The Surface intentionally has no bottom inset padding so the
        // `surfaceVariant` background reaches the very edge of the screen
        // (covering the navigation-bar inset area). The inner Column then
        // carves out the system-bar space via `navigationBarsPadding()` so
        // text always renders above the system buttons.
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PanelHeight),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (currentState != null) {
                AgentThoughtIndicator(
                    state = currentState,
                    onApprove = onApprove,
                    onDeny = onDeny,
                )
            }
            visibleEvents.forEach { event ->
                Text(
                    text = formatLine(event, timeFormatter.format(Date(event.timestamp))),
                    color = lineColor(event.type),
                    fontFamily = FontFamily.Monospace,
                    fontSize = LineFontSize,
                    lineHeight = LineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Builds the single-line representation of an event: `HH:mm:ss [TAG] message`.
 *
 * Keeping the prefix tag here (instead of relying on color alone) ensures the
 * log remains parseable when copied to plain text in the expanded console
 * (Phase 17.5) and stays accessible to users who can't distinguish the
 * accent / error colors.
 */
private fun formatLine(event: ConsoleEvent, timeText: String): String {
    val tag = when (event.type) {
        ConsoleEventType.NodeExecution -> "NODE"
        ConsoleEventType.ToolCall -> "TOOL"
        ConsoleEventType.MemoryAccess -> "MEM"
        ConsoleEventType.SystemMessage -> "SYS"
        ConsoleEventType.Error -> "ERR"
    }
    return "$timeText [$tag] ${event.message}"
}

/**
 * Maps an event category to its line color. Pulled out as a `@Composable`
 * helper so the renderer reads `MaterialTheme.colorScheme.*` lazily and
 * picks up theme switches at runtime.
 */
@Composable
private fun lineColor(type: ConsoleEventType): Color = when (type) {
    ConsoleEventType.NodeExecution -> MaterialTheme.colorScheme.onSurface.copy(alpha = NeutralAlpha)
    ConsoleEventType.ToolCall -> MaterialTheme.colorScheme.primary
    ConsoleEventType.MemoryAccess -> MaterialTheme.colorScheme.tertiary
    ConsoleEventType.SystemMessage -> MaterialTheme.colorScheme.onSurface.copy(alpha = MutedAlpha)
    ConsoleEventType.Error -> MaterialTheme.colorScheme.error
}
