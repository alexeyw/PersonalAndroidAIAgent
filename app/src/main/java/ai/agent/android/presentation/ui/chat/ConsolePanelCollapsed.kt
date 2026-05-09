package ai.agent.android.presentation.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** Fixed height of the panel — matches the spec for Phase 17.4. */
private val PanelHeight = 56.dp

/** Compact monospace font size used for every console line. */
private val LineFontSize = 11.sp

/** Alpha applied to neutral / muted line colors so they stay legible but recede. */
private const val NeutralAlpha = 0.6f
private const val MutedAlpha = 0.4f

/**
 * Always-on agent console rendered as a thin strip above the chat input.
 *
 * The composable is stateless: callers pass the event list (already truncated
 * or filtered if needed) and decide whether to show the panel at all. Visibility
 * gating lives in `ChatScreen` so the panel can disappear entirely when the
 * agent is idle and the log is empty.
 *
 * The most recent event is rendered at the bottom (closest to the input) so
 * the user's eye, naturally drawn to the input area, lands on the freshest
 * activity. Up to [COLLAPSED_LINE_LIMIT] lines are shown — anything older is
 * available in the expanded console (Phase 17.5).
 *
 * @param events Console events to render. Only the last [COLLAPSED_LINE_LIMIT]
 *   are displayed; the caller does not need to truncate.
 * @param modifier Optional layout modifier applied to the panel container.
 */
@Composable
fun ConsolePanelCollapsed(
    events: List<ConsoleEvent>,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val visibleEvents = remember(events) { events.takeLast(COLLAPSED_LINE_LIMIT) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(PanelHeight),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            visibleEvents.forEach { event ->
                Text(
                    text = formatLine(event, timeFormatter.format(Date(event.timestamp))),
                    color = lineColor(event.type),
                    fontFamily = FontFamily.Monospace,
                    fontSize = LineFontSize,
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
