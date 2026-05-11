package ai.agent.android.presentation.ui.settings

import ai.agent.android.R
import ai.agent.android.data.engine.KoogModelMapper
import ai.agent.android.domain.models.LocalBackend
import ai.agent.android.presentation.ui.common.resolve
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsSection(
    title: String,
    keyValue: String,
    onKeyChange: (String) -> Unit,
    modelValue: String,
    onModelChange: (String) -> Unit,
    availableModels: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }

    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = keyValue,
        onValueChange = onKeyChange,
        label = { Text(stringResource(R.string.settings_provider_api_key_label, title)) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = modelValue,
            onValueChange = onModelChange,
            label = { Text(stringResource(R.string.settings_provider_model_label, title)) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onModelChange(model)
                        expanded = false
                    },
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

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
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val locale = LocalConfiguration.current.locales[0]

    val openAiModels = KoogModelMapper.getOpenAIModelIdList()
    val anthropicModels = KoogModelMapper.getAnthropicModelIdList()
    val googleModels = KoogModelMapper.getGoogleModelIdList()
    val deepSeekModels = KoogModelMapper.getDeepSeekModelIdList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Section: System Instructions
            Text(
                text = stringResource(R.string.settings_section_system_instructions),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.systemPromptPrefix,
                onValueChange = { viewModel.updateSystemPromptPrefix(it) },
                label = { Text(stringResource(R.string.settings_field_system_prompt_prefix)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 10,
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: Restrictions
            Text(
                text = stringResource(R.string.settings_section_restrictions),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_human_in_loop_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_human_in_loop_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.requiresUserConfirmation,
                    onCheckedChange = { viewModel.updateRequiresUserConfirmation(it) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: LLM Parameters
            Text(
                text = stringResource(R.string.settings_section_llm_parameters),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Temperature Slider
            Text(
                text = stringResource(
                    R.string.settings_param_temperature,
                    String.format(locale, "%.2f", uiState.temperature),
                ),
            )
            Slider(
                value = uiState.temperature,
                onValueChange = { viewModel.updateTemperature(it) },
                valueRange = 0f..2f,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Top-K Slider
            Text(text = stringResource(R.string.settings_param_top_k, uiState.topK))
            Slider(
                value = uiState.topK.toFloat(),
                onValueChange = { viewModel.updateTopK(it.roundToInt()) },
                valueRange = 1f..100f,
                steps = 99,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Top-P Slider
            Text(
                text = stringResource(
                    R.string.settings_param_top_p,
                    String.format(locale, "%.2f", uiState.topP),
                ),
            )
            Slider(
                value = uiState.topP,
                onValueChange = { viewModel.updateTopP(it) },
                valueRange = 0f..1f,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Max Context Length Slider
            Text(text = stringResource(R.string.settings_param_max_context, uiState.maxContextLength))
            Slider(
                value = uiState.maxContextLength.toFloat(),
                onValueChange = { viewModel.updateMaxContextLength(it.roundToInt()) },
                valueRange = 512f..8192f,
                steps = 14,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Pipeline Max Steps
            // State is kept independent of uiState to preserve cursor position while typing.
            // LaunchedEffect syncs only when the value changes from an external source.
            var pipelineMaxStepsText by remember { mutableStateOf(uiState.pipelineMaxSteps.toString()) }
            LaunchedEffect(uiState.pipelineMaxSteps) {
                if (pipelineMaxStepsText.toIntOrNull()?.coerceIn(5, 100) != uiState.pipelineMaxSteps) {
                    pipelineMaxStepsText = uiState.pipelineMaxSteps.toString()
                }
            }
            OutlinedTextField(
                value = pipelineMaxStepsText,
                onValueChange = { input ->
                    pipelineMaxStepsText = input
                    val parsed = input.toIntOrNull()
                    if (parsed != null && parsed in 5..100) {
                        viewModel.updatePipelineMaxSteps(parsed)
                    }
                },
                label = { Text(stringResource(R.string.settings_pipeline_max_steps_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = pipelineMaxStepsText.toIntOrNull()?.let { it !in 5..100 } ?: true,
                supportingText = {
                    if (pipelineMaxStepsText.toIntOrNull()?.let { it !in 5..100 } != false) {
                        Text(stringResource(R.string.settings_pipeline_max_steps_helper))
                    }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: Local Model Settings
            Text(
                text = stringResource(R.string.settings_section_local_model),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            var backendExpanded by remember { mutableStateOf(false) }
            val context = LocalContext.current

            Text(
                text = stringResource(R.string.settings_inference_backend),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = backendExpanded,
                onExpandedChange = { backendExpanded = it },
            ) {
                OutlinedTextField(
                    value = uiState.localModelBackend,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_select_backend)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = backendExpanded) },
                    modifier = Modifier.menuAnchor(
                        androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        true,
                    ).fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = backendExpanded,
                    onDismissRequest = { backendExpanded = false },
                ) {
                    LocalBackend.entries.forEach { backend ->
                        DropdownMenuItem(
                            text = { Text(backend.key) },
                            onClick = {
                                viewModel.updateLocalModelBackend(backend.key)
                                backendExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.testBackend { resultMsg ->
                        Toast.makeText(context, context.resolve(resultMsg), Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_test_backend_button, uiState.localModelBackend))
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: External Providers (API Keys & Models)
            Text(
                text = stringResource(R.string.settings_section_external_providers),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            ProviderSettingsSection(
                title = "OpenAI",
                keyValue = uiState.openAiKey,
                onKeyChange = { viewModel.updateOpenAiKey(it) },
                modelValue = uiState.openAiModel,
                onModelChange = { viewModel.updateOpenAiModel(it) },
                availableModels = openAiModels,
            )

            ProviderSettingsSection(
                title = "Anthropic",
                keyValue = uiState.anthropicKey,
                onKeyChange = { viewModel.updateAnthropicKey(it) },
                modelValue = uiState.anthropicModel,
                onModelChange = { viewModel.updateAnthropicModel(it) },
                availableModels = anthropicModels,
            )

            ProviderSettingsSection(
                title = "Google",
                keyValue = uiState.googleKey,
                onKeyChange = { viewModel.updateGoogleKey(it) },
                modelValue = uiState.googleModel,
                onModelChange = { viewModel.updateGoogleModel(it) },
                availableModels = googleModels,
            )

            ProviderSettingsSection(
                title = "DeepSeek",
                keyValue = uiState.deepSeekKey,
                onKeyChange = { viewModel.updateDeepSeekKey(it) },
                modelValue = uiState.deepSeekModel,
                onModelChange = { viewModel.updateDeepSeekModel(it) },
                availableModels = deepSeekModels,
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section: Local Network Models (Ollama)
            Text(
                text = stringResource(R.string.settings_section_ollama),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.ollamaBaseUrl,
                onValueChange = { viewModel.updateOllamaBaseUrl(it) },
                label = { Text(stringResource(R.string.settings_ollama_base_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.ollamaModel,
                onValueChange = { viewModel.updateOllamaModel(it) },
                label = { Text(stringResource(R.string.settings_ollama_model_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.ollamaContextWindow,
                onValueChange = { viewModel.updateOllamaContextWindow(it) },
                label = { Text(stringResource(R.string.settings_ollama_context_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
