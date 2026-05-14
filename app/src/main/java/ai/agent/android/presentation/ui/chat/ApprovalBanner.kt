package ai.agent.android.presentation.ui.chat

import ai.agent.android.R
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ToolRisk
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

/**
 * Prominent inline approval prompt rendered directly above the chat input bar
 * whenever the orchestrator is in [AgentOrchestratorState.WaitingForApproval].
 *
 * The compact console line in [AgentThoughtIndicator] is a one-line summary
 * that is easy to miss inside the 16 dp slot strip — especially in compact /
 * landscape layouts. This banner takes real estate proportional to the
 * decision the user has to make: a coloured risk chip, the tool name and a
 * truncated arguments preview, plus full-width Approve / Deny buttons that
 * meet the 48 dp Material tap target. Rendering does not depend on
 * [ConsolePanelCollapsed] visibility, so even when the panel is hidden the
 * approval prompt stays in front of the user.
 *
 * @param state Current orchestrator state. The composable renders nothing
 *   unless it is [AgentOrchestratorState.WaitingForApproval]; this keeps the
 *   call-site condition-free.
 * @param onApprove Invoked when the user accepts the pending tool execution.
 * @param onDeny Invoked when the user rejects the pending tool execution.
 * @param modifier Optional layout modifier applied to the banner container.
 */
@Composable
fun ApprovalBanner(
    state: AgentOrchestratorState?,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state !is AgentOrchestratorState.WaitingForApproval) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RiskBadge(risk = state.risk)
                Text(
                    text = stringResource(R.string.chat_approval_banner_title, state.toolName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
            }
            if (state.arguments.isNotBlank()) {
                Text(
                    text = state.arguments,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.chat_thought_deny))
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.chat_thought_approve))
                }
            }
        }
    }
}

private const val BADGE_COLOR_READ_ONLY = 0xFF616161
private const val BADGE_COLOR_SENSITIVE = 0xFFFFA000
private const val BADGE_COLOR_DESTRUCTIVE = 0xFFC62828

@Composable
private fun RiskBadge(risk: ToolRisk) {
    val (label, bg) = when (risk) {
        ToolRisk.READ_ONLY -> stringResource(R.string.chat_risk_chip_read_only) to Color(BADGE_COLOR_READ_ONLY)
        ToolRisk.SENSITIVE -> stringResource(R.string.chat_risk_chip_sensitive) to Color(BADGE_COLOR_SENSITIVE)
        ToolRisk.DESTRUCTIVE -> stringResource(R.string.chat_risk_chip_destructive) to Color(BADGE_COLOR_DESTRUCTIVE)
    }
    Text(
        text = label,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
