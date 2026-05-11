package ai.agent.android.presentation.ui.chat

import ai.agent.android.R
import ai.agent.android.domain.models.AgentOrchestratorState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Composable that displays the pipeline trace (intermediate nodes).
 *
 * @param steps The list of trace steps to display.
 */
@Composable
fun PipelineTraceCard(steps: List<AgentOrchestratorState.TraceStep>) {
    var expanded by remember { mutableStateOf(false) }

    if (steps.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_trace_title, steps.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.chat_trace_collapse_cd else R.string.chat_trace_expand_cd,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    steps.forEachIndexed { index, step ->
                        val outputPreview = if (step.outputText.length > 100) {
                            step.outputText.take(100) + "..."
                        } else {
                            step.outputText
                        }
                        val msText = stringResource(R.string.chat_trace_ms, step.durationMs)
                        val tokenText = step.tokenCount?.let {
                            stringResource(R.string.chat_trace_tokens, it)
                        }
                        val metaParts = buildList {
                            add(msText)
                            tokenText?.let { add(it) }
                        }
                        val metaLabel = metaParts.joinToString(", ")

                        Text(
                            text = "${index + 1}. [${step.nodeName}] ($metaLabel) -> $outputPreview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
