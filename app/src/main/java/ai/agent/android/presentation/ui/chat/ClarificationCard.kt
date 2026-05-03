package ai.agent.android.presentation.ui.chat

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Inline chat card that surfaces an [AgentOrchestratorState.AwaitingClarification]
 * request to the user.
 *
 * The card has three visual modes driven by [model.status]:
 *  - `PENDING`: shows the question and either option buttons (when
 *    `model.options != null` and non-empty) or a free-form `OutlinedTextField`
 *    plus a Send button. Renders a [LinearProgressIndicator] and an
 *    "Auto-reply in N s" label that updates every second; when the visual
 *    countdown reaches zero the card invokes [onTimeout] (the
 *    [ClarificationRepository] is the authoritative timer — see
 *    [ChatViewModel.markClarificationTimedOut]).
 *  - `ANSWERED`: collapsed, non-editable summary of what the user replied.
 *  - `TIMED_OUT`: collapsed, faded summary noting the default answer that the
 *    agent received.
 *
 * @param model The card data model from [ChatUiState.clarificationCards].
 * @param onAnswer Invoked when the user picks an option or sends free-form text.
 *   Receives the chosen string verbatim.
 * @param onTimeout Invoked when the visual countdown reaches zero. The lambda
 *   receives the default answer (first option, or empty string for free-form
 *   requests) the agent will fall back to.
 * @param modifier Standard Compose modifier.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClarificationCard(
    model: ClarificationCardUiModel,
    onAnswer: (String) -> Unit,
    onTimeout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ClarificationCard"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(12.dp)
                .alpha(if (model.status == ClarificationCardUiModel.Status.TIMED_OUT) 0.6f else 1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Agent requests clarification",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (model.status) {
                ClarificationCardUiModel.Status.PENDING -> PendingBody(model, onAnswer, onTimeout)
                ClarificationCardUiModel.Status.ANSWERED -> AnsweredBody(model)
                ClarificationCardUiModel.Status.TIMED_OUT -> TimedOutBody(model)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingBody(
    model: ClarificationCardUiModel,
    onAnswer: (String) -> Unit,
    onTimeout: (String) -> Unit,
) {
    Text(
        text = model.question,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Spacer(modifier = Modifier.height(8.dp))

    val options = model.options
    if (!options.isNullOrEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                OutlinedButton(
                    onClick = { onAnswer(option) },
                    modifier = Modifier.semantics { contentDescription = "Option: $option" },
                ) {
                    Text(option)
                }
            }
        }
    } else {
        var text by remember(model.id) { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ClarificationInput"),
                placeholder = { Text("Your answer") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) onAnswer(trimmed)
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send answer",
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    CountdownRow(model = model, onTimeout = onTimeout)
}

@Composable
private fun CountdownRow(
    model: ClarificationCardUiModel,
    onTimeout: (String) -> Unit,
) {
    val deadline = remember(model.id) { model.startedAtMs + model.timeoutMs }
    var remainingMs by remember(model.id) {
        mutableLongStateOf((deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L))
    }

    LaunchedEffect(model.id) {
        // Poll once per second using a monotonic clock so the visible value stays
        // aligned with wall-clock progress even after recompositions or brief skips.
        while (true) {
            val now = SystemClock.uptimeMillis()
            val left = (deadline - now).coerceAtLeast(0L)
            remainingMs = left
            if (left == 0L) {
                val defaultAnswer = model.options?.firstOrNull().orEmpty()
                onTimeout(defaultAnswer)
                break
            }
            delay(1000L)
        }
    }

    val totalMs = model.timeoutMs.coerceAtLeast(1L)
    val progress = (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val seconds = ((remainingMs + 999L) / 1000L).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Auto-reply in ${seconds}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun AnsweredBody(model: ClarificationCardUiModel) {
    Text(
        text = model.question,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "You answered: ${model.answer.orEmpty()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.testTag("ClarificationAnswered"),
    )
}

@Composable
private fun TimedOutBody(model: ClarificationCardUiModel) {
    Text(
        text = model.question,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Default answer used: ${model.answer.orEmpty()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.testTag("ClarificationTimedOut"),
    )
}
