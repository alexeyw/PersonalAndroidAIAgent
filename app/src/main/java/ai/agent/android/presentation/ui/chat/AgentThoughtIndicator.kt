package ai.agent.android.presentation.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * line aesthetic.
 *
 * Render contract:
 *  - Returns nothing for terminal / inert states ([AgentOrchestratorState.Idle],
 *    [AgentOrchestratorState.Completed], [AgentOrchestratorState.Error]) — the
 *    final reply lands as a regular chat message and the panel's event log
 *    already shows the closing trace.
 *  - Otherwise emits one monospace line in the same `[TAG] message` format as
 *    [ConsoleEvent]s, color-coded by urgency.
 *  - For [AgentOrchestratorState.WaitingForApproval] additionally renders a
 *    compact `Approve / Deny` action row inline below the line.
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
    val label = labelFor(state) ?: return
    val color = colorFor(state)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = LineFontSize,
            lineHeight = LineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state is AgentOrchestratorState.WaitingForApproval) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ConsoleAction(label = "Deny", onClick = onDeny)
                ConsoleAction(label = "Approve", onClick = onApprove)
            }
        }
    }
}

/**
 * Compact in-console action button used by the [AgentOrchestratorState.WaitingForApproval]
 * row. Standard Material `TextButton` reserves a 48dp tap target with
 * generous internal padding which read as a vertical gap inside the dense
 * monospace strip — this variant matches the line-height of console events
 * while still meeting the 44dp tap-target via `clickable` + 12dp horizontal
 * padding (giving ~28dp of vertical padding around an 11sp glyph).
 */
@Composable
private fun ConsoleAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        fontFamily = FontFamily.Monospace,
        fontSize = LineFontSize,
        lineHeight = LineHeight,
    )
}

private val LineFontSize = 11.sp
private val LineHeight = 12.sp
private const val NeutralAlpha = 0.6f

/**
 * Maps an orchestrator state to the single-line label rendered in the
 * thought indicator. Returns `null` for states that should produce no
 * output (caller `return`s early when this is `null`).
 */
private fun labelFor(state: AgentOrchestratorState): String? = when (state) {
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
