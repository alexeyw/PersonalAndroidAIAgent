package ai.agent.android.presentation.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.agent.android.domain.models.AgentOrchestratorState

/**
 * Console-styled single-line indicator of the agent's current orchestrator
 * state, intended to render at the top of [ConsolePanelCollapsed]. Phase 17.4
 * pulled this card out of the chat list (where it kept dragging the message
 * list during streaming) and re-skinned it to match the console's monospace
 * line aesthetic; later the entire panel was locked to exactly three slots,
 * so this composable is guaranteed to fit in a single row regardless of
 * orchestrator state — including [AgentOrchestratorState.WaitingForApproval],
 * whose Approve/Deny actions render inline alongside the question instead
 * of in a separate row beneath it.
 *
 * Render contract:
 *  - Returns nothing for terminal / inert states ([AgentOrchestratorState.Idle],
 *    [AgentOrchestratorState.Completed], [AgentOrchestratorState.Error]) — the
 *    final reply lands as a regular chat message and the panel's event log
 *    already shows the closing trace.
 *  - Otherwise emits **one** monospace row in the same `[TAG] message`
 *    format as [ConsoleEvent]s, color-coded by urgency.
 *  - For [AgentOrchestratorState.WaitingForApproval] the row is a `Row` with
 *    the question on the left (taking remaining width with `weight(1f)`) and
 *    `Deny` / `Approve` clickable Text affordances on the right. Tap target
 *    is 6dp horizontal + 2dp vertical padding around an 11sp glyph — small
 *    but acceptable inside the dense status strip; the system notification
 *    fallback (`ApprovalNotificationManager`) provides the larger touch
 *    surface when the chat isn't visible.
 *
 * @param state Current orchestrator state. Caller is expected to gate
 *   visibility (e.g. `isGenerating && !hasPendingClarification`); this
 *   composable does not check those external signals itself.
 * @param onApprove Invoked when the user accepts the pending tool execution.
 * @param onDeny Invoked when the user rejects the pending tool execution.
 */
@Composable
fun AgentThoughtIndicator(
    state: AgentOrchestratorState,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val label = thoughtLineFor(state) ?: return
    val color = colorFor(state)

    if (state is AgentOrchestratorState.WaitingForApproval) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = LineFontSize,
                lineHeight = LineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ConsoleAction(label = "Deny", onClick = onDeny)
            ConsoleAction(label = "Approve", onClick = onApprove)
        }
    } else {
        Text(
            text = label,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = LineFontSize,
            lineHeight = LineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/**
 * Console line typography — kept in sync with [ConsolePanelCollapsed].
 * `LineHeight` deliberately leaves room above the natural ascender / below
 * the descender of the 11sp monospace glyph so letters like `y`, `p`, `q`
 * aren't clipped when wrapped in a fixed-height slot.
 */
internal val LineFontSize = 11.sp
internal val LineHeight = 14.sp
private const val NeutralAlpha = 0.6f

/**
 * Compact in-line action affordance used by [AgentOrchestratorState.WaitingForApproval].
 * Clickable monospace text with minimal padding so the entire approval row
 * stays inside a single console slot.
 */
@Composable
private fun ConsoleAction(label: String, onClick: () -> Unit) {
    // Horizontal padding only — adding vertical padding would push the
    // composable past the slot's fixed height and clip the descenders of
    // `y` / `p` / `q`. Tap target stays the slot height (16dp) which is
    // small but acceptable here; the system notification fallback gives
    // the larger 48dp surface when the chat isn't visible.
    Text(
        text = label,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        color = MaterialTheme.colorScheme.primary,
        fontFamily = FontFamily.Monospace,
        fontSize = LineFontSize,
        lineHeight = LineHeight,
    )
}

/**
 * Maps an orchestrator state to the single-line label rendered in the
 * thought indicator. Returns `null` for states that should produce no
 * output. Public to the chat package so [ConsolePanelCollapsed] can
 * decide whether the state would actually consume a console slot — adding
 * a `ConsoleLine.State` for a state that ends up rendering nothing would
 * silently swallow one of the three event lines.
 */
internal fun thoughtLineFor(state: AgentOrchestratorState): String? = when (state) {
    is AgentOrchestratorState.Idle,
    is AgentOrchestratorState.Completed,
    is AgentOrchestratorState.Error,
    is AgentOrchestratorState.AwaitingClarification -> null
    is AgentOrchestratorState.Loading -> "[NOW] Initializing agent..."
    is AgentOrchestratorState.Thinking -> "[NOW] Agent is thinking..."
    is AgentOrchestratorState.Answering -> "[NOW] Agent is answering..."
    is AgentOrchestratorState.ExecutingTool -> "[NOW] Using tool: ${state.toolName}..."
    is AgentOrchestratorState.ObservationResult -> "[NOW] Observation: ${state.toolName}"
    is AgentOrchestratorState.WaitingForApproval -> "[ASK] Approve ${state.toolName}?"
    is AgentOrchestratorState.PipelineStage -> "[NOW] Stage: ${state.stepInfo.nodeName}"
    is AgentOrchestratorState.PipelineTrace,
    is AgentOrchestratorState.ConsoleLog -> null
}

/**
 * Color for the thought line. Approval prompts demand attention so they pick
 * up the error palette; everything else uses the same muted `onSurface`
 * neutral that [ConsolePanelCollapsed] uses for `NodeExecution` events so
 * the thought line and the rolling event log read as a single typographic
 * surface.
 */
@Composable
private fun colorFor(state: AgentOrchestratorState): Color = when (state) {
    is AgentOrchestratorState.WaitingForApproval -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = NeutralAlpha)
}
