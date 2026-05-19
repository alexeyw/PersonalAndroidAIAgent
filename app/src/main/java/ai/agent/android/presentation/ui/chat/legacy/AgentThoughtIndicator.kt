package ai.agent.android.presentation.ui.chat.legacy

import ai.agent.android.R
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ToolRisk
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 *    the question on the left (taking remaining width with `weight(1f)`), a
 *    compact risk chip (`READ` / `SENS` / `DEST`, coloured by
 *    [AgentOrchestratorState.WaitingForApproval.risk]) and `Deny` / `Approve`
 *    clickable Text affordances on the right. Tap target is 6dp horizontal +
 *    2dp vertical padding around an 11sp glyph — small but acceptable inside
 *    the dense status strip; the system notification fallback
 *    (`ApprovalNotificationManager`) provides the larger touch surface when
 *    the chat isn't visible. The chip colour is a temporary code constant
 *    until the Phase 21 design pass replaces it with palette tokens.
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
            RiskChip(risk = state.risk)
            ConsoleAction(label = stringResource(R.string.chat_thought_deny), onClick = onDeny)
            ConsoleAction(label = stringResource(R.string.chat_thought_approve), onClick = onApprove)
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
private const val NEUTRAL_ALPHA = 0.6f

// Temporary risk-chip palette. Phase 21 will replace these with proper
// MaterialTheme tokens; baked into code here so the gate ships without
// blocking on the design pass.
private const val RISK_COLOR_READ_ONLY = 0xFF808080
private const val RISK_COLOR_SENSITIVE = 0xFFFFC107
private const val RISK_COLOR_DESTRUCTIVE = 0xFFD32F2F
private val RiskColorReadOnly = Color(RISK_COLOR_READ_ONLY)
private val RiskColorSensitive = Color(RISK_COLOR_SENSITIVE)
private val RiskColorDestructive = Color(RISK_COLOR_DESTRUCTIVE)

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
 * Risk badge rendered between the question and the Approve / Deny actions.
 * Uses a temporary three-colour palette (read-only/grey, sensitive/amber,
 * destructive/red) until the Phase 21 design pass swaps it for palette
 * tokens; intentionally **not** wired into the Material theme so the choice
 * is visible in code review during the transition phase.
 */
@Composable
private fun RiskChip(risk: ToolRisk) {
    val (label, bg) = when (risk) {
        ToolRisk.READ_ONLY -> stringResource(R.string.chat_risk_chip_read_only) to RiskColorReadOnly
        ToolRisk.SENSITIVE -> stringResource(R.string.chat_risk_chip_sensitive) to RiskColorSensitive
        ToolRisk.DESTRUCTIVE -> stringResource(R.string.chat_risk_chip_destructive) to RiskColorDestructive
    }
    Text(
        text = label,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = LineFontSize,
        lineHeight = LineHeight,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .padding(horizontal = 4.dp),
    )
}

/**
 * Composable that maps an orchestrator state to the single-line label
 * rendered in the thought indicator. Returns `null` for states that should
 * produce no output.
 *
 * Composable because the label includes localized text from
 * `strings_chat.xml`; render-decision callers that only need to know
 * "would this state produce output?" should consult
 * [thoughtLineHasOutput] instead (which is pure Kotlin and safe to call
 * from non-composable scopes such as `remember { … }` blocks).
 */
@Composable
internal fun thoughtLineFor(state: AgentOrchestratorState): String? = when (state) {
    is AgentOrchestratorState.Idle,
    is AgentOrchestratorState.Completed,
    is AgentOrchestratorState.Error,
    is AgentOrchestratorState.AwaitingClarification,
    -> null
    is AgentOrchestratorState.Loading -> stringResource(R.string.chat_thought_now_initializing)
    is AgentOrchestratorState.Thinking -> stringResource(R.string.chat_thought_now_thinking)
    is AgentOrchestratorState.Answering -> stringResource(R.string.chat_thought_now_answering)
    is AgentOrchestratorState.ExecutingTool -> stringResource(R.string.chat_thought_now_using_tool, state.toolName)
    is AgentOrchestratorState.ObservationResult -> stringResource(R.string.chat_thought_now_observation, state.toolName)
    is AgentOrchestratorState.WaitingForApproval -> stringResource(R.string.chat_thought_now_approve, state.toolName)
    is AgentOrchestratorState.PipelineStage -> stringResource(R.string.chat_thought_now_stage, state.stepInfo.nodeName)
    is AgentOrchestratorState.PipelineTrace,
    is AgentOrchestratorState.ConsoleLog,
    is AgentOrchestratorState.NodeIO,
    -> null
}

/**
 * Pure-Kotlin predicate mirroring the null/non-null result of
 * [thoughtLineFor]. Lets non-composable callers (e.g. the
 * `remember { ... }` block in [ConsolePanelCollapsed] that decides whether
 * to consume a console slot for the state line) make the same render
 * decision without entering a composition scope.
 */
internal fun thoughtLineHasOutput(state: AgentOrchestratorState): Boolean = when (state) {
    is AgentOrchestratorState.Idle,
    is AgentOrchestratorState.Completed,
    is AgentOrchestratorState.Error,
    is AgentOrchestratorState.AwaitingClarification,
    is AgentOrchestratorState.PipelineTrace,
    is AgentOrchestratorState.ConsoleLog,
    is AgentOrchestratorState.NodeIO,
    -> false
    is AgentOrchestratorState.Loading,
    is AgentOrchestratorState.Thinking,
    is AgentOrchestratorState.Answering,
    is AgentOrchestratorState.ExecutingTool,
    is AgentOrchestratorState.ObservationResult,
    is AgentOrchestratorState.WaitingForApproval,
    is AgentOrchestratorState.PipelineStage,
    -> true
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
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = NEUTRAL_ALPHA)
}
