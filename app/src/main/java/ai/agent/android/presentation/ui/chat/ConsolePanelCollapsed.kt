package ai.agent.android.presentation.ui.chat

import ai.agent.android.R
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Default number of slots rendered by the panel. Three so the strip is
 * exactly three lines tall regardless of which state (event log, thought,
 * approval) it currently shows. The chat screen may opt into a single-slot
 * compact layout via [ConsolePanelCollapsed]'s `compact` parameter when the
 * available viewport is too short for the full strip.
 */
private const val DEFAULT_SLOT_COUNT = 3

/**
 * Slot count used when the panel is requested in compact mode. One slot
 * keeps the very last entry visible while reclaiming ~32dp of vertical
 * space — used on short viewports (e.g. 480dp height with an open IME) so
 * the chat history isn't squeezed by the bottom bar.
 */
private const val COMPACT_SLOT_COUNT = 1

/**
 * Per-slot vertical extent in dp. Sized roomier than the `14.sp` line height
 * of the monospace text so glyph descenders (`y`, `p`, `q`) and ascenders
 * stay fully visible inside the fixed-height slot at default font scale.
 * Total panel content height is therefore `SLOT_COUNT * SlotHeight` plus the
 * vertical padding wrapping the column.
 */
private val SlotHeight = 16.dp

/**
 * Sealed model of a single console row. Both the rolling event log and the
 * orchestrator state line flow through the same list so they share slot
 * positioning, alignment, and the bottom-anchored chronological ordering.
 */
private sealed interface ConsoleLine {
    data class Event(val event: ConsoleEvent) : ConsoleLine
    data class State(val state: AgentOrchestratorState) : ConsoleLine
}

/**
 * Always-on agent console rendered as a thin strip below the chat input.
 *
 * Stateless and locked to a fixed number of slots — [DEFAULT_SLOT_COUNT]
 * normally, [COMPACT_SLOT_COUNT] when [compact] is `true`. The orchestrator
 * state line — when present — is treated as just another console row,
 * appended to the rolling event log as the freshest entry. The strip then
 * shows the last N entries bottom-aligned, so the user sees the running
 * activity regardless of whether it's a node event, a thought-state line,
 * or an approval prompt; the panel does not gain or lose height between
 * cases.
 *
 * @param events Console events to render. Caller passes the full log; the
 *   composable picks the last few that fit the available slots.
 * @param currentState Current orchestrator state. When non-null it joins the
 *   line list as the freshest row (rendered at the bottom). For
 *   [AgentOrchestratorState.WaitingForApproval] the row carries inline
 *   `Deny` / `Approve` affordances; for every other live state it's a
 *   single monospace label like `[NOW] Agent is answering...`.
 * @param onApprove Forwarded to the embedded approval row.
 * @param onDeny Forwarded to the embedded approval row.
 * @param onClick Invoked when the user taps any non-interactive area of
 *   the panel; the host opens the expanded-console `ModalBottomSheet`
 *   (Phase 17.5). Inner buttons (`Approve` / `Deny`) consume their own
 *   click via Compose's hit dispatch and do not trigger this callback.
 * @param compact When `true`, the panel collapses to a single slot
 *   ([COMPACT_SLOT_COUNT]) so it stays usable on short viewports without
 *   squeezing the chat history. Default `false` keeps the full
 *   three-slot strip.
 * @param modifier Optional layout modifier applied to the panel container.
 */
@Composable
fun ConsolePanelCollapsed(
    events: List<ConsoleEvent>,
    currentState: AgentOrchestratorState? = null,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    onClick: () -> Unit = {},
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    val slotCount = if (compact) COMPACT_SLOT_COUNT else DEFAULT_SLOT_COUNT
    val visible = remember(events, currentState, slotCount) {
        val combined = buildList<ConsoleLine> {
            events.forEach { add(ConsoleLine.Event(it)) }
            // Only add the state line when AgentThoughtIndicator would render
            // something for it — `PipelineTrace`, `ConsoleLog`,
            // `AwaitingClarification` etc. produce no output and would
            // otherwise consume a slot, silently dropping one event line.
            if (currentState != null && thoughtLineHasOutput(currentState)) {
                add(ConsoleLine.State(currentState))
            }
        }
        combined.takeLast(slotCount)
    }
    val emptySlotsAtTop = slotCount - visible.size

    Surface(
        // The Surface intentionally has no bottom inset padding so the
        // `surfaceVariant` background reaches the very edge of the screen
        // (covering the navigation-bar inset area). The inner Column then
        // carves out the system-bar space via `navigationBarsPadding()` so
        // text always renders above the system buttons.
        //
        // `clickable` is attached to the Surface (the outermost touch
        // target) — Compose dispatches inner Button / IconButton clicks
        // first, so the embedded `Approve` / `Deny` row keeps working
        // and only background taps fall through to [onClick].
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            // Pad top with empty slots so the visible content stays
            // bottom-aligned and the panel keeps its fixed three-slot
            // height even with sparse content.
            repeat(emptySlotsAtTop.coerceAtLeast(0)) {
                Slot {}
            }
            visible.forEach { line ->
                Slot {
                    when (line) {
                        is ConsoleLine.Event -> Text(
                            text = formatEventLine(
                                line.event,
                                timeFormatter.format(Date(line.event.timestamp)),
                                consoleTagFor(line.event.type),
                            ),
                            color = lineColor(line.event.type),
                            fontFamily = FontFamily.Monospace,
                            fontSize = LineFontSize,
                            lineHeight = LineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        is ConsoleLine.State -> AgentThoughtIndicator(
                            state = line.state,
                            onApprove = onApprove,
                            onDeny = onDeny,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fixed-height slot wrapping a single console row. Centers content
 * vertically so the monospace baseline lines up across slots regardless
 * of which composable rendered the row.
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
internal fun formatEventLine(event: ConsoleEvent, timeText: String, tag: String): String =
    "$timeText [$tag] ${event.message}"

/**
 * Resolves the localized console tag (NODE / TOOL / MEM / SYS / ERR) for an
 * event type. Exposed at module scope so both the collapsed strip and the
 * expanded log sheet pull tags from the same source.
 */
@Composable
internal fun consoleTagFor(type: ConsoleEventType): String = stringResource(
    when (type) {
        ConsoleEventType.NodeExecution -> R.string.chat_console_tag_node
        ConsoleEventType.ToolCall -> R.string.chat_console_tag_tool
        ConsoleEventType.MemoryAccess -> R.string.chat_console_tag_memory
        ConsoleEventType.SystemMessage -> R.string.chat_console_tag_system
        ConsoleEventType.Error -> R.string.chat_console_tag_error
    },
)

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
