package ai.agent.android.presentation.ui.settings

import ai.agent.android.R
import ai.agent.android.data.engine.KoogModelMapper
import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.LocalBackend
import ai.agent.android.presentation.ui.common.resolve
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles
import kotlin.math.roundToInt

/**
 * Composable screen for managing agent settings.
 *
 * Phase 21 / Task 10 (mockup pass): every row is styled with the brand
 * tokens — small-caps mono section headers, amber-tinted sliders with the
 * value on the right edge, mono outlined text fields, and a brand-amber
 * Switch for toggles.
 */
@Suppress("LongMethod") // Single long form; helper composables already split the chrome.
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
    val context = LocalContext.current

    val openAiModels = KoogModelMapper.getOpenAIModelIdList()
    val anthropicModels = KoogModelMapper.getAnthropicModelIdList()
    val googleModels = KoogModelMapper.getGoogleModelIdList()
    val deepSeekModels = KoogModelMapper.getDeepSeekModelIdList()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        // Outer `AppShellScaffold` already absorbs the bottom-nav inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_screen_title),
                        style = KnotworkTextStyles.TitleLg,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = KnotworkTheme.spacing.sp4)
                .verticalScroll(scrollState),
        ) {
            // ---------------- SYSTEM INSTRUCTIONS ----------------
            SettingsSectionHeader(label = stringResource(R.string.settings_section_system_instructions))
            MonoOutlinedTextField(
                value = uiState.systemPromptPrefix,
                onValueChange = viewModel::updateSystemPromptPrefix,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SYSTEM_PROMPT_FIELD_HEIGHT),
                placeholder = stringResource(R.string.settings_field_system_prompt_prefix),
            )
            Text(
                text = stringResource(R.string.settings_system_prompt_helper),
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )

            // ---------------- RESTRICTIONS ----------------
            SettingsSectionHeader(label = stringResource(R.string.settings_section_restrictions))
            SettingsToggleRow(
                icon = Icons.Outlined.Shield,
                title = stringResource(R.string.settings_human_in_loop_label),
                subtitle = stringResource(R.string.settings_human_in_loop_hint),
                checked = uiState.requiresUserConfirmation,
                onCheckedChange = viewModel::updateRequiresUserConfirmation,
            )

            // ---------------- LLM PARAMETERS ----------------
            SettingsSectionHeader(label = stringResource(R.string.settings_section_llm_parameters))
            KnotworkParamSlider(
                label = stringResource(R.string.settings_param_temperature_label),
                valueLabel = String.format(locale, "%.1f", uiState.temperature),
                value = uiState.temperature,
                onValueChange = viewModel::updateTemperature,
                valueRange = 0f..2f,
            )
            KnotworkParamSlider(
                label = stringResource(R.string.settings_param_top_k_label),
                valueLabel = uiState.topK.toString(),
                value = uiState.topK.toFloat(),
                onValueChange = { viewModel.updateTopK(it.roundToInt()) },
                valueRange = 1f..100f,
                steps = 99,
            )
            KnotworkParamSlider(
                label = stringResource(R.string.settings_param_top_p_label),
                valueLabel = String.format(locale, "%.2f", uiState.topP),
                value = uiState.topP,
                onValueChange = viewModel::updateTopP,
                valueRange = 0f..1f,
            )
            KnotworkParamSlider(
                label = stringResource(R.string.settings_param_max_context_label),
                valueLabel = androidx.compose.ui.res.pluralStringResource(
                    R.plurals.settings_param_max_context_value,
                    uiState.maxContextLength,
                    uiState.maxContextLength,
                ),
                value = uiState.maxContextLength.toFloat(),
                onValueChange = { viewModel.updateMaxContextLength(it.roundToInt()) },
                valueRange = 512f..8192f,
                steps = 14,
            )
            PipelineMaxStepsField(
                currentValue = uiState.pipelineMaxSteps,
                onCommit = viewModel::updatePipelineMaxSteps,
            )

            // ---------------- LOCAL MODEL ----------------
            SettingsSectionHeader(label = stringResource(R.string.settings_section_local_model))
            InferenceBackendRow(
                currentBackend = uiState.localModelBackend,
                onBackendChange = viewModel::updateLocalModelBackend,
            )
            Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp1))
            TextButton(
                onClick = {
                    viewModel.testBackend { resultMsg ->
                        Toast.makeText(context, context.resolve(resultMsg), Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_test_backend_button, uiState.localModelBackend))
            }

            // ---------------- PRIVACY ----------------
            SettingsSectionHeader(label = stringResource(R.string.settings_section_privacy))
            PrivacySection(
                crashReportingEnabled = uiState.crashReportingEnabled,
                onCrashReportingChange = viewModel::updateCrashReportingEnabled,
            )

            // ---------------- EXTERNAL PROVIDERS ----------------
            SettingsSectionHeader(label = stringResource(R.string.settings_section_external_providers))
            ProviderSettingsSection(
                title = "OpenAI",
                keyValue = uiState.openAiKey,
                onKeyChange = viewModel::updateOpenAiKey,
                modelValue = uiState.openAiModel,
                onModelChange = viewModel::updateOpenAiModel,
                availableModels = openAiModels,
            )
            ProviderSettingsSection(
                title = "Anthropic",
                keyValue = uiState.anthropicKey,
                onKeyChange = viewModel::updateAnthropicKey,
                modelValue = uiState.anthropicModel,
                onModelChange = viewModel::updateAnthropicModel,
                availableModels = anthropicModels,
            )
            ProviderSettingsSection(
                title = "Google",
                keyValue = uiState.googleKey,
                onKeyChange = viewModel::updateGoogleKey,
                modelValue = uiState.googleModel,
                onModelChange = viewModel::updateGoogleModel,
                availableModels = googleModels,
            )
            ProviderSettingsSection(
                title = "DeepSeek",
                keyValue = uiState.deepSeekKey,
                onKeyChange = viewModel::updateDeepSeekKey,
                modelValue = uiState.deepSeekModel,
                onModelChange = viewModel::updateDeepSeekModel,
                availableModels = deepSeekModels,
            )

            // ---------------- OLLAMA ----------------
            SettingsSectionHeader(label = stringResource(R.string.settings_section_ollama))
            OutlinedTextField(
                value = uiState.ollamaBaseUrl,
                onValueChange = viewModel::updateOllamaBaseUrl,
                label = { Text(stringResource(R.string.settings_ollama_base_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.ollamaModel,
                onValueChange = viewModel::updateOllamaModel,
                label = { Text(stringResource(R.string.settings_ollama_model_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.ollamaContextWindow,
                onValueChange = viewModel::updateOllamaContextWindow,
                label = { Text(stringResource(R.string.settings_ollama_context_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp10))
        }
    }
}

// ---------- Brand-styled helper composables ----------

/**
 * Small-caps monospace section header rendered before every settings
 * block. Mirrors the spec mockup ("SYSTEM INSTRUCTIONS" / "RESTRICTIONS"
 * / "LLM PARAMETERS" / "LOCAL MODEL" etc.). The label is uppercased
 * here so callers can keep the string resources human-readable.
 */
@Composable
private fun SettingsSectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = KnotworkTextStyles.MonoSm,
        color = KnotworkTheme.extended.onSurfaceMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = KnotworkTheme.spacing.sp3, bottom = KnotworkTheme.spacing.sp1),
    )
}

/**
 * Branded slider row — `TitleMd` label on the left, brand-primary value
 * label on the right, amber-tinted Material slider underneath.
 */
@Composable
private fun KnotworkParamSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = KnotworkTextStyles.MonoBase.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = KnotworkTheme.extended.surface3,
                activeTickColor = MaterialTheme.colorScheme.primary,
                inactiveTickColor = KnotworkTheme.extended.surface3,
            ),
        )
    }
}

/**
 * Toggle row used inside the RESTRICTIONS and PRIVACY sections — leading
 * shield-like icon, two-line content, trailing brand-amber Switch.
 */
@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = KnotworkTheme.spacing.sp2),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(KnotworkTheme.spacing.sp6),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
            Text(
                text = title,
                style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = KnotworkTextStyles.BodyBase,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/**
 * Multi-line outlined text field rendered with the brand monospace
 * typeface for system-prompt entry. Uses a transparent background +
 * outline border so it sits on the surface like every other input on
 * the screen.
 */
@Composable
private fun MonoOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
) {
    Box(
        modifier = modifier
            .clip(KnotworkTheme.shapes.md)
            .border(width = 1.dp, color = KnotworkTheme.extended.outlineStrong, shape = KnotworkTheme.shapes.md)
            .padding(KnotworkTheme.spacing.sp3),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = KnotworkTextStyles.MonoBase.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxSize(),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = KnotworkTextStyles.MonoBase,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
    }
}

/**
 * Inline pipeline-max-steps field. Numeric-keyboard `OutlinedTextField`
 * with brand-amber error state when the parsed value falls outside the
 * `5..100` bracket.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PipelineMaxStepsField(currentValue: Int, onCommit: (Int) -> Unit) {
    var text by remember { mutableStateOf(currentValue.toString()) }
    LaunchedEffect(currentValue) {
        val coerced = text.toIntOrNull()?.coerceIn(
            SettingsDefaults.PIPELINE_MAX_STEPS_MIN,
            SettingsDefaults.PIPELINE_MAX_STEPS_MAX,
        )
        if (coerced != currentValue) {
            text = currentValue.toString()
        }
    }
    val parsed = text.toIntOrNull()
    val outOfRange = parsed?.let { it !in PIPELINE_MAX_STEPS_VALID_RANGE } ?: true
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val v = input.toIntOrNull()
            if (v != null && v in PIPELINE_MAX_STEPS_VALID_RANGE) onCommit(v)
        },
        label = { Text(stringResource(R.string.settings_pipeline_max_steps_label)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = outOfRange,
        supportingText = {
            if (outOfRange) Text(stringResource(R.string.settings_pipeline_max_steps_helper))
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/**
 * Inference-backend row: leading chip glyph, primary label + monospace
 * subtitle `"NPU · QNN · auto"`-shaped, trailing chevron that toggles
 * the dropdown. Wraps a Material `ExposedDropdownMenuBox` so the chip
 * receipts the menu anchor cleanly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InferenceBackendRow(currentBackend: String, onBackendChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .padding(vertical = KnotworkTheme.spacing.sp2),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(BACKEND_ICON_TILE_SIZE)
                    .clip(KnotworkTheme.shapes.sm)
                    .background(color = KnotworkTheme.extended.surface2),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(KnotworkTheme.spacing.sp5),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_inference_backend),
                    style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = currentBackend,
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LocalBackend.entries.forEach { backend ->
                DropdownMenuItem(
                    text = { Text(backend.key) },
                    onClick = {
                        onBackendChange(backend.key)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Per-provider section: brand-styled title, masked API-key input,
 * model-picker dropdown. Wraps the legacy fields with the brand
 * outline + spacing.
 */
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
    Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2)) {
        Text(
            text = title,
            style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = keyValue,
            onValueChange = onKeyChange,
            label = { Text(stringResource(R.string.settings_provider_api_key_label, title)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = modelValue,
                onValueChange = onModelChange,
                label = { Text(stringResource(R.string.settings_provider_model_label, title)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true,
                    ),
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
        HorizontalDivider(color = KnotworkTheme.extended.divider)
    }
}

/**
 * Privacy section — crash-reporting opt-in with a consent dialog. The
 * toggle is inverted (default OFF) so flipping ON requires explicit
 * confirmation.
 */
@Composable
private fun PrivacySection(crashReportingEnabled: Boolean, onCrashReportingChange: (Boolean) -> Unit) {
    var showConsentDialog by remember { mutableStateOf(false) }

    SettingsToggleRow(
        icon = Icons.Outlined.Shield,
        title = stringResource(R.string.settings_crash_reporting_label),
        subtitle = stringResource(R.string.settings_crash_reporting_hint),
        checked = crashReportingEnabled,
        onCheckedChange = { wantOn ->
            if (wantOn) {
                showConsentDialog = true
            } else {
                onCrashReportingChange(false)
            }
        },
    )

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text(stringResource(R.string.settings_crash_reporting_dialog_title)) },
            text = { Text(stringResource(R.string.settings_crash_reporting_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConsentDialog = false
                        onCrashReportingChange(true)
                    },
                ) {
                    Text(stringResource(R.string.settings_crash_reporting_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) {
                    Text(stringResource(R.string.settings_crash_reporting_dialog_dismiss))
                }
            },
        )
    }
}

/** Approximate height of the system-prompt mono text field. */
private val SYSTEM_PROMPT_FIELD_HEIGHT = 120.dp

/** Size of the leading icon tile rendered next to the inference backend row. */
private val BACKEND_ICON_TILE_SIZE = 40.dp

/** Inclusive range for the pipeline-max-steps input. */
private val PIPELINE_MAX_STEPS_VALID_RANGE =
    SettingsDefaults.PIPELINE_MAX_STEPS_MIN..SettingsDefaults.PIPELINE_MAX_STEPS_MAX
