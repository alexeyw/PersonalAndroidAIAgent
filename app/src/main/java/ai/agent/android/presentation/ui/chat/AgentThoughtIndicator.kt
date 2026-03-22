package ai.agent.android.presentation.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.agent.android.domain.models.AgentOrchestratorState
import com.mikepenz.markdown.m3.Markdown

/**
 * A Composable widget that visualizes the internal "Chain of Thought" process of the agent.
 * It displays states like thinking, using tools, or loading in an animated, expandable card.
 *
 * @param state The current state of the orchestrator to visualize.
 * @param onApprove Callback when the user approves a tool execution.
 * @param onDeny Callback when the user denies a tool execution.
 */
@Composable
fun AgentThoughtIndicator(
    state: AgentOrchestratorState,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    // Render the final generation as a standard message bubble.
    if (state is AgentOrchestratorState.Answering) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(12.dp)
            ) {
                // Using Text instead of Markdown during streaming to prevent severe UI flickering and layout recalculations
                Text(
                    text = state.partialText,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        return
    }

    if (state is AgentOrchestratorState.Completed || state is AgentOrchestratorState.Error) {
        return
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AnimatedContent(targetState = state, label = "thought_state") { targetState ->
                        Text(
                            text = when (targetState) {
                                is AgentOrchestratorState.Loading -> "Initializing agent..."
                                is AgentOrchestratorState.Thinking -> "Agent is thinking..."
                                is AgentOrchestratorState.WaitingForApproval -> "Action requires approval!"
                                is AgentOrchestratorState.ExecutingTool -> "Using tool: ${targetState.toolName}..."
                                is AgentOrchestratorState.ObservationResult -> "Observation received..."
                                else -> "Processing..."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (targetState is AgentOrchestratorState.WaitingForApproval) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded || state is AgentOrchestratorState.WaitingForApproval) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    when (state) {
                        is AgentOrchestratorState.WaitingForApproval -> {
                            Text(
                                text = "Agent wants to execute '${state.toolName}'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = onDeny,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Deny")
                                }
                                Button(onClick = onApprove) {
                                    Text("Approve")
                                }
                            }
                        }
                        is AgentOrchestratorState.Thinking -> {
                            Text(
                                text = state.partialText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AgentOrchestratorState.ExecutingTool -> {
                            Text(
                                text = "Arguments: ${state.arguments}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AgentOrchestratorState.ObservationResult -> {
                            Text(
                                text = "Result from ${state.toolName}:\n${state.result}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
