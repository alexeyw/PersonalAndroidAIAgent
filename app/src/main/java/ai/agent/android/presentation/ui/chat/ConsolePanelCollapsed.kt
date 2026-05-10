package ai.agent.android.presentation.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Total number of slots rendered by the panel. Locked to three so the panel
 * never resizes between states (one-line thought, two-line approval, etc.) —
 * spec says the strip is exactly three lines tall.
 */
private const val SLOT_COUNT = 3

/**
 * Per-slot vertical extent in dp. Set just above the `12.sp` line height of
 * the monospace text so glyph descenders aren't clipped at default font
 * scale. Total content height is therefore `SLOT_COUNT * SlotHeight` plus the
 * vertical padding wrapping the column.
 */
private val SlotHeight = 14.dp

/**
 * Always-on agent console rendered as a thin strip below the chat input.
 *
 * The composable is stateless: callers pass the event list, the current
 * orchestrator state (when generating), and decide whether to show the panel
 * at all. Visibility gating lives in `ChatScreen` so the panel can disappear
 * entirely when the agent is idle and the log is empty.
 *
 * The strip is locked to exactly [SLOT_COUNT] slots, top-to-bottom:
 *  - When [currentState] is non-null, slot 0 hosts a single-line
 *    [AgentThoughtIndicator] (which packs Approve/Deny inline for
 *    [AgentOrchestratorState.WaitingForApproval]). The remaining 2 slots
 *    show the latest events bottom-aligned.
 *  - When [currentState] is null, all 3 slots are populated by the latest
 *    events bottom-aligned.
 *
 * Slots without content render as transparent spacers of [SlotHeight] so the
 * panel keeps the same total height regardless of what's available — the
 * spec asks for an immutable three-line strip.
 *
 * @param events Console events to render. The composable picks the freshest
 *   ones that fit the remaining slots; no truncation needed at the call site.
 * @param currentState Current orchestrator state. Pass `null` to suppress the
 *   thought line entirely (e.g. when the agent is idle).
 * @param onApprove Forwarded to the embedded thought line for the
 *   [AgentOrchestratorState.WaitingForApproval] action affordance.
 * @param onDeny Forwarded to the embedded thought line for the
 *   [AgentOrchestratorState.WaitingForApproval] action affordance.
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

    val thoughtSlotUsed = currentState != null
    val eventsSlotsAvailable = SLOT_COUNT - if (thoughtSlotUsed) 1 else 0
    val visibleEvents = remember(events, eventsSlotsAvailable) {
        events.takeLast(eventsSlotsAvailable)
    }

    // Build the slot list top-to-bottom. The thought line (if present) sits
    // at the top; events stack at the bottom; any leftover slots in the
    // middle render as empty spacers of SlotHeight so the panel keeps the
    // fixed total height even with sparse content.
    val emptySlotsBetween = SLOT_COUNT - (if (thoughtSlotUsed) 1 else 0) - visibleEvents.size

    Surface(
        // The Surface intentionally has no bottom inset padding so the
        // `surfaceVariant` background reaches the very edge of the screen
        // (covering the navigation-bar inset area). The inner Column then
        // carves out the system-bar space via `navigationBarsPadding()` so
        // text always renders above the system buttons. The total height is
        // determined by the column's intrinsic content (SLOT_COUNT slots +
        // padding + nav-bar inset) — no `heightIn` needed.
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            if (thoughtSlotUsed) {
                Slot {
                    AgentThoughtIndicator(
                        state = currentState!!,
                        onApprove = onApprove,
                        onDeny = onDeny,
                    )
                }
            }
            repeat(emptySlotsBetween.coerceAtLeast(0)) {
                Slot {}
            }
            visibleEvents.forEach { event ->
                Slot {
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
}

/**
 * Fixed-height slot wrapping a single console row (thought line, event line,
 * or empty spacer). Centers content vertically inside the slot so the
 * monospace baseline lines up across slots regardless of which composable
 * rendered the row.
 */
@Composable
private fun Slot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SlotHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
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
    ConsoleEventType.NodeExecution -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    ConsoleEventType.ToolCall -> MaterialTheme.colorScheme.primary
    ConsoleEventType.MemoryAccess -> MaterialTheme.colorScheme.tertiary
    ConsoleEventType.SystemMessage -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    ConsoleEventType.Error -> MaterialTheme.colorScheme.error
}
