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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Composable screen for managing agent settings.
 *
 * @param modifier The modifier to be applied to the layout.
 * @param viewModel The view model managing the state for this screen.
 * @param onBack Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

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
            HorizontalDivider()
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
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: LLM Parameters
            Text(
                text = "LLM Parameters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Temperature Slider
            Text(
                text = "Temperature: ${
                    String.format(
                        Locale.getDefault(),
                        "%.2f",
                        uiState.temperature
                    )
                }"
            )
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
            Text(text = "Top-P: ${String.format(Locale.getDefault(), "%.2f", uiState.topP)}")
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
                steps = 14 // 512 step increments roughly
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: External Providers (API Keys)
            Text(
                text = "External Providers (API Keys)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.openAiKey,
                onValueChange = { viewModel.updateOpenAiKey(it) },
                label = { Text("OpenAI API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.anthropicKey,
                onValueChange = { viewModel.updateAnthropicKey(it) },
                label = { Text("Anthropic API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.googleKey,
                onValueChange = { viewModel.updateGoogleKey(it) },
                label = { Text("Google API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.deepSeekKey,
                onValueChange = { viewModel.updateDeepSeekKey(it) },
                label = { Text("DeepSeek API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: Local Network Models (Ollama)
            Text(
                text = "Local Network Models (Ollama)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.ollamaBaseUrl,
                onValueChange = { viewModel.updateOllamaBaseUrl(it) },
                label = { Text("Ollama Base URL (e.g., http://192.168.1.100:11434)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
