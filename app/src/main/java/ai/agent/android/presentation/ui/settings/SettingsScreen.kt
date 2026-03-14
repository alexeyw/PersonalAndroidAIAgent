package ai.agent.android.presentation.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Agent Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Section: System Instructions
        Text(
            text = "System Instructions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.systemPromptPrefix,
            onValueChange = { viewModel.updateSystemPromptPrefix(it) },
            label = { Text("System Prompt Prefix") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            maxLines = 10
        )

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Section: Restrictions
        Text(
            text = "Restrictions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Human-in-the-loop", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Require confirmation for critical tool executions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = uiState.requiresUserConfirmation,
                onCheckedChange = { viewModel.updateRequiresUserConfirmation(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Section: LLM Parameters
        Text(
            text = "LLM Parameters",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Temperature Slider
        Text(text = "Temperature: ${String.format("%.2f", uiState.temperature)}")
        Slider(
            value = uiState.temperature,
            onValueChange = { viewModel.updateTemperature(it) },
            valueRange = 0f..2f
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Top-K Slider
        Text(text = "Top-K: ${uiState.topK}")
        Slider(
            value = uiState.topK.toFloat(),
            onValueChange = { viewModel.updateTopK(it.roundToInt()) },
            valueRange = 1f..100f,
            steps = 99
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Top-P Slider
        Text(text = "Top-P: ${String.format("%.2f", uiState.topP)}")
        Slider(
            value = uiState.topP,
            onValueChange = { viewModel.updateTopP(it) },
            valueRange = 0f..1f
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Max Context Length Slider
        Text(text = "Max Context Tokens: ${uiState.maxContextLength}")
        Slider(
            value = uiState.maxContextLength.toFloat(),
            onValueChange = { viewModel.updateMaxContextLength(it.roundToInt()) },
            valueRange = 512f..8192f,
            steps = 15 // 512 step increments roughly
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
