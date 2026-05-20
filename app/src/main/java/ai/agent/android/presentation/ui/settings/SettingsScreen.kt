package ai.agent.android.presentation.ui.settings

import ai.agent.android.BuildConfig
import ai.agent.android.R
import ai.agent.android.data.engine.KoogModelMapper
import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.LocalBackend
import ai.agent.android.presentation.ui.common.resolve
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.design.screens.settings.KnotworkMonoTextArea
import app.knotwork.design.screens.settings.KnotworkParamSlider
import app.knotwork.design.screens.settings.KnotworkProviderRow
import app.knotwork.design.screens.settings.OllamaProviderInputs
import app.knotwork.design.screens.settings.SettingsContent
import app.knotwork.design.screens.settings.SettingsRowState
import app.knotwork.design.screens.settings.SettingsSection
import app.knotwork.design.screens.settings.SettingsSectionBlock
import app.knotwork.design.screens.settings.SettingsViewState
import app.knotwork.design.screens.settings.SettingsVisualState
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles
import kotlin.math.roundToInt

/**
 * Phase 22 / Task 8 — Knotwork-styled settings surface.
 *
 * The screen renders the six canonical settings sections (Appearance,
 * Models, Privacy, Memory, MCP, About) via the catalog
 * [SettingsContent]. Each row is dispatched through the catalog's
 * `rowContent` slot so the screen can replace the default
 * title-subtitle-trailing layout with the richer Knotwork variants —
 * the multi-line system-prompt area, branded sliders, collapsible
 * provider cards, and About/MCP navigation rows.
 *
 * The composable holds no settings state itself: every value originates
 * in [SettingsViewModel] and writes flow back through the same VM.
 *
 * @param modifier outer layout modifier (typically `Modifier.fillMaxSize`).
 * @param viewModel injected Hilt VM bridging DataStore + EncryptedPrefs.
 * @param onBack invoked when the user taps the system back button.
 * @param onOpenTools invoked from the MCP section's `Manage servers` row;
 *  routes the user to the Tools screen where MCP servers are wired in
 *  task 10.
 * @param onOpenAbout invoked from the About section's license row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current

    val ollamaError = if (uiState.ollamaBaseUrlInvalid) {
        stringResource(R.string.settings_ollama_base_url_error)
    } else {
        null
    }
    val viewState = buildViewState(uiState, locale, ollamaError)

    SettingsContent(
        state = viewState,
        modifier = modifier,
        onBack = onBack,
        rowContent = { row, defaultRender ->
            when (row.id) {
                // ─── Appearance ─────────────────────────────────────────
                ROW_ID_THEME -> defaultRender()
                ROW_ID_SYSTEM_PROMPT -> SystemPromptRow(
                    value = uiState.systemPromptPrefix,
                    onValueChange = viewModel::updateSystemPromptPrefix,
                )

                // ─── Models ─────────────────────────────────────────────
                ROW_ID_BACKEND -> InferenceBackendRow(
                    currentBackend = uiState.localModelBackend,
                    onBackendChange = viewModel::updateLocalModelBackend,
                    onTestBackend = {
                        viewModel.testBackend { resultMsg ->
                            Toast.makeText(context, context.resolve(resultMsg), Toast.LENGTH_LONG).show()
                        }
                    },
                )
                ROW_ID_TEMPERATURE -> ParamSliderRow {
                    KnotworkParamSlider(
                        label = stringResource(R.string.settings_row_temperature_title),
                        valueLabel = String.format(locale, "%.1f", uiState.temperature),
                        value = uiState.temperature,
                        onValueChange = viewModel::updateTemperature,
                        valueRange = 0f..2f,
                    )
                }
                ROW_ID_TOP_K -> ParamSliderRow {
                    KnotworkParamSlider(
                        label = stringResource(R.string.settings_row_top_k_title),
                        valueLabel = uiState.topK.toString(),
                        value = uiState.topK.toFloat(),
                        onValueChange = { viewModel.updateTopK(it.roundToInt()) },
                        valueRange = 1f..100f,
                        steps = 99,
                    )
                }
                ROW_ID_TOP_P -> ParamSliderRow {
                    KnotworkParamSlider(
                        label = stringResource(R.string.settings_row_top_p_title),
                        valueLabel = String.format(locale, "%.2f", uiState.topP),
                        value = uiState.topP,
                        onValueChange = viewModel::updateTopP,
                        valueRange = 0f..1f,
                    )
                }
                ROW_ID_MAX_CONTEXT -> ParamSliderRow {
                    KnotworkParamSlider(
                        label = stringResource(R.string.settings_row_max_context_title),
                        valueLabel = pluralStringResource(
                            R.plurals.settings_param_max_context_value,
                            uiState.maxContextLength,
                            uiState.maxContextLength,
                        ),
                        value = uiState.maxContextLength.toFloat(),
                        onValueChange = { viewModel.updateMaxContextLength(it.roundToInt()) },
                        valueRange = 512f..8192f,
                        steps = 14,
                    )
                }
                ROW_ID_PIPELINE_MAX_STEPS -> ParamSliderRow {
                    PipelineMaxStepsField(
                        currentValue = uiState.pipelineMaxSteps,
                        onCommit = viewModel::updatePipelineMaxSteps,
                    )
                }
                ROW_ID_OPENAI -> ProviderCardRow {
                    KnotworkProviderRow(
                        title = "OpenAI",
                        keyValue = uiState.openAiKey,
                        onKeyChange = viewModel::updateOpenAiKey,
                        keyLabel = stringResource(R.string.settings_provider_api_key_label, "OpenAI"),
                        modelValue = uiState.openAiModel,
                        onModelChange = viewModel::updateOpenAiModel,
                        modelLabel = stringResource(R.string.settings_provider_model_label, "OpenAI"),
                        availableModels = KoogModelMapper.getOpenAIModelIdList(),
                        pendingChange = ROW_ID_OPENAI in uiState.pendingRowIds,
                    )
                }
                ROW_ID_ANTHROPIC -> ProviderCardRow {
                    KnotworkProviderRow(
                        title = "Anthropic",
                        keyValue = uiState.anthropicKey,
                        onKeyChange = viewModel::updateAnthropicKey,
                        keyLabel = stringResource(R.string.settings_provider_api_key_label, "Anthropic"),
                        modelValue = uiState.anthropicModel,
                        onModelChange = viewModel::updateAnthropicModel,
                        modelLabel = stringResource(R.string.settings_provider_model_label, "Anthropic"),
                        availableModels = KoogModelMapper.getAnthropicModelIdList(),
                        pendingChange = ROW_ID_ANTHROPIC in uiState.pendingRowIds,
                    )
                }
                ROW_ID_GOOGLE -> ProviderCardRow {
                    KnotworkProviderRow(
                        title = "Google",
                        keyValue = uiState.googleKey,
                        onKeyChange = viewModel::updateGoogleKey,
                        keyLabel = stringResource(R.string.settings_provider_api_key_label, "Google"),
                        modelValue = uiState.googleModel,
                        onModelChange = viewModel::updateGoogleModel,
                        modelLabel = stringResource(R.string.settings_provider_model_label, "Google"),
                        availableModels = KoogModelMapper.getGoogleModelIdList(),
                        pendingChange = ROW_ID_GOOGLE in uiState.pendingRowIds,
                    )
                }
                ROW_ID_DEEPSEEK -> ProviderCardRow {
                    KnotworkProviderRow(
                        title = "DeepSeek",
                        keyValue = uiState.deepSeekKey,
                        onKeyChange = viewModel::updateDeepSeekKey,
                        keyLabel = stringResource(R.string.settings_provider_api_key_label, "DeepSeek"),
                        modelValue = uiState.deepSeekModel,
                        onModelChange = viewModel::updateDeepSeekModel,
                        modelLabel = stringResource(R.string.settings_provider_model_label, "DeepSeek"),
                        availableModels = KoogModelMapper.getDeepSeekModelIdList(),
                        pendingChange = ROW_ID_DEEPSEEK in uiState.pendingRowIds,
                    )
                }
                ROW_ID_OLLAMA -> ProviderCardRow {
                    KnotworkProviderRow(
                        title = "Ollama",
                        keyValue = "",
                        onKeyChange = {},
                        keyLabel = stringResource(R.string.settings_provider_api_key_label, "Ollama"),
                        modelValue = uiState.ollamaModel,
                        onModelChange = viewModel::updateOllamaModel,
                        modelLabel = stringResource(R.string.settings_ollama_model_label),
                        availableModels = emptyList(),
                        pendingChange = ROW_ID_OLLAMA in uiState.pendingRowIds,
                        ollama = OllamaProviderInputs(
                            baseUrl = uiState.ollamaBaseUrl,
                            baseUrlPlaceholder = stringResource(R.string.settings_ollama_base_url_placeholder),
                            baseUrlValidationError = ollamaError,
                            contextWindow = uiState.ollamaContextWindow,
                            contextWindowLabel = stringResource(R.string.settings_ollama_context_label),
                            baseUrlLabel = stringResource(R.string.settings_ollama_base_url_label),
                        ),
                        onOllamaBaseUrlChange = viewModel::updateOllamaBaseUrl,
                        onOllamaContextWindowChange = viewModel::updateOllamaContextWindow,
                    )
                }

                // ─── Privacy ────────────────────────────────────────────
                ROW_ID_HITL -> ToggleRow(
                    row = row,
                    checked = uiState.requiresUserConfirmation,
                    onCheckedChange = viewModel::updateRequiresUserConfirmation,
                )
                ROW_ID_CRASH_REPORTING -> CrashReportingRow(
                    enabled = uiState.crashReportingEnabled,
                    onChange = viewModel::updateCrashReportingEnabled,
                    row = row,
                )

                // ─── Memory ─────────────────────────────────────────────
                ROW_ID_MEMORY_SUMMARY_LIMIT -> ParamSliderRow {
                    val minLimit = SettingsDefaults.MEMORY_SUMMARY_LIMIT_MIN
                    val maxLimit = SettingsDefaults.MEMORY_SUMMARY_LIMIT_MAX
                    KnotworkParamSlider(
                        label = stringResource(R.string.settings_row_memory_summary_limit_title),
                        valueLabel = uiState.memorySummaryDefaultLimit.toString(),
                        value = uiState.memorySummaryDefaultLimit.toFloat(),
                        onValueChange = { viewModel.updateMemorySummaryDefaultLimit(it.roundToInt()) },
                        valueRange = minLimit.toFloat()..maxLimit.toFloat(),
                        steps = maxLimit - minLimit - 1,
                    )
                }

                // ─── MCP ────────────────────────────────────────────────
                ROW_ID_MCP_MANAGE -> NavigationRow(row = row, onClick = onOpenTools)

                // ─── About ──────────────────────────────────────────────
                ROW_ID_ABOUT_LICENSE -> NavigationRow(row = row, onClick = onOpenAbout)
                else -> defaultRender()
            }
        },
    )
}

/**
 * Builds a fresh [SettingsViewState] from the current [uiState]. Composable
 * so localized titles / subtitles flow through `stringResource`.
 */
@Composable
@Suppress("LongMethod") // Single declarative builder; splitting hurts readability.
private fun buildViewState(
    uiState: SettingsUiState,
    locale: java.util.Locale,
    ollamaError: String?,
): SettingsViewState {
    val licenseName = stringResource(R.string.license_name)
    fun row(id: String, title: String, subtitle: String? = null): SettingsRowState = SettingsRowState(
        id = id,
        title = title,
        subtitle = subtitle,
        pendingChange = id in uiState.pendingRowIds,
        validationError = if (id == ROW_ID_OLLAMA) ollamaError else null,
    )

    val appearance = SettingsSectionBlock(
        section = SettingsSection.Appearance,
        rows = listOf(
            row(
                ROW_ID_THEME,
                stringResource(R.string.settings_row_theme_title),
                stringResource(R.string.settings_row_theme_subtitle),
            ),
            row(ROW_ID_SYSTEM_PROMPT, stringResource(R.string.settings_row_system_prompt_title)),
        ),
    )
    val models = SettingsSectionBlock(
        section = SettingsSection.Models,
        rows = listOf(
            row(ROW_ID_BACKEND, stringResource(R.string.settings_row_backend_title), uiState.localModelBackend),
            row(
                ROW_ID_TEMPERATURE,
                stringResource(R.string.settings_row_temperature_title),
                String.format(locale, "%.1f", uiState.temperature),
            ),
            row(ROW_ID_TOP_K, stringResource(R.string.settings_row_top_k_title), uiState.topK.toString()),
            row(
                ROW_ID_TOP_P,
                stringResource(R.string.settings_row_top_p_title),
                String.format(locale, "%.2f", uiState.topP),
            ),
            row(
                ROW_ID_MAX_CONTEXT,
                stringResource(R.string.settings_row_max_context_title),
                uiState.maxContextLength.toString(),
            ),
            row(
                ROW_ID_PIPELINE_MAX_STEPS,
                stringResource(R.string.settings_row_pipeline_max_steps_title),
                stringResource(R.string.settings_row_pipeline_max_steps_subtitle),
            ),
            row(ROW_ID_OPENAI, "OpenAI"),
            row(ROW_ID_ANTHROPIC, "Anthropic"),
            row(ROW_ID_GOOGLE, "Google"),
            row(ROW_ID_DEEPSEEK, "DeepSeek"),
            row(ROW_ID_OLLAMA, "Ollama"),
        ),
    )
    val privacy = SettingsSectionBlock(
        section = SettingsSection.Privacy,
        rows = listOf(
            row(
                ROW_ID_HITL,
                stringResource(R.string.settings_human_in_loop_label),
                stringResource(R.string.settings_human_in_loop_hint),
            ),
            row(
                ROW_ID_CRASH_REPORTING,
                stringResource(R.string.settings_crash_reporting_label),
                stringResource(R.string.settings_crash_reporting_hint),
            ),
        ),
    )
    val memory = SettingsSectionBlock(
        section = SettingsSection.Memory,
        rows = listOf(
            row(
                ROW_ID_MEMORY_SUMMARY_LIMIT,
                stringResource(R.string.settings_row_memory_summary_limit_title),
                stringResource(R.string.settings_row_memory_summary_limit_subtitle),
            ),
        ),
    )
    val mcp = SettingsSectionBlock(
        section = SettingsSection.Mcp,
        rows = listOf(
            row(
                ROW_ID_MCP_MANAGE,
                stringResource(R.string.settings_row_mcp_manage_title),
                stringResource(R.string.settings_row_mcp_manage_subtitle),
            ),
        ),
    )
    val about = SettingsSectionBlock(
        section = SettingsSection.About,
        rows = listOf(
            row(
                ROW_ID_ABOUT_VERSION,
                stringResource(R.string.settings_row_about_version_title),
                BuildConfig.VERSION_NAME,
            ),
            row(
                ROW_ID_ABOUT_COMMIT,
                stringResource(R.string.settings_row_about_commit_title),
                BuildConfig.GIT_SHA,
            ),
            row(ROW_ID_ABOUT_LICENSE, stringResource(R.string.settings_row_about_license_title), licenseName),
        ),
    )

    val visualState = when {
        uiState.ollamaBaseUrlInvalid -> SettingsVisualState.ValidationError
        uiState.pendingRowIds.isNotEmpty() -> SettingsVisualState.PendingChange
        else -> SettingsVisualState.Default
    }

    return SettingsViewState(
        visualState = visualState,
        sections = listOf(appearance, models, privacy, memory, mcp, about),
    )
}

// ─── Row composables ─────────────────────────────────────────────────────

@Composable
private fun SystemPromptRow(value: String, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        Text(
            text = stringResource(R.string.settings_row_system_prompt_title),
            style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        KnotworkMonoTextArea(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.settings_field_system_prompt_prefix),
            modifier = Modifier
                .fillMaxWidth()
                .height(SYSTEM_PROMPT_FIELD_HEIGHT),
        )
        Text(
            text = stringResource(R.string.settings_row_system_prompt_subtitle),
            style = KnotworkTextStyles.BodySm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}

@Composable
private fun ParamSliderRow(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
    ) { content() }
}

@Composable
private fun ProviderCardRow(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp1),
    ) { content() }
}

@Composable
private fun ToggleRow(row: SettingsRowState, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
            Text(
                text = row.title,
                style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = row.subtitle
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = KnotworkTextStyles.BodyBase,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
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

@Composable
private fun CrashReportingRow(enabled: Boolean, onChange: (Boolean) -> Unit, row: SettingsRowState) {
    var showConsentDialog by remember { mutableStateOf(false) }
    ToggleRow(
        row = row,
        checked = enabled,
        onCheckedChange = { wantOn ->
            if (wantOn) {
                showConsentDialog = true
            } else {
                onChange(false)
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
                        onChange(true)
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

@Composable
private fun NavigationRow(row: SettingsRowState, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = row.subtitle
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InferenceBackendRow(currentBackend: String, onBackendChange: (String) -> Unit, onTestBackend: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
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
                    ),
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
                        text = stringResource(R.string.settings_row_backend_title),
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
        TextButton(onClick = onTestBackend, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_test_backend_button, currentBackend))
        }
    }
}

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

// ─── Constants ───────────────────────────────────────────────────────────

internal const val ROW_ID_THEME = "theme"
internal const val ROW_ID_SYSTEM_PROMPT = "system_prompt"
internal const val ROW_ID_BACKEND = "backend"
internal const val ROW_ID_TEMPERATURE = "temperature"
internal const val ROW_ID_TOP_K = "top_k"
internal const val ROW_ID_TOP_P = "top_p"
internal const val ROW_ID_MAX_CONTEXT = "max_context"
internal const val ROW_ID_PIPELINE_MAX_STEPS = "pipeline_max_steps"
internal const val ROW_ID_OPENAI = "openai"
internal const val ROW_ID_ANTHROPIC = "anthropic"
internal const val ROW_ID_GOOGLE = "google"
internal const val ROW_ID_DEEPSEEK = "deepseek"
internal const val ROW_ID_OLLAMA = "ollama"
internal const val ROW_ID_HITL = "hitl"
internal const val ROW_ID_CRASH_REPORTING = "crash_reporting"
internal const val ROW_ID_MEMORY_SUMMARY_LIMIT = "memory_summary_limit"
internal const val ROW_ID_MCP_MANAGE = "mcp_manage"
internal const val ROW_ID_ABOUT_VERSION = "about_version"
internal const val ROW_ID_ABOUT_COMMIT = "about_commit"
internal const val ROW_ID_ABOUT_LICENSE = "about_license"

private val SYSTEM_PROMPT_FIELD_HEIGHT = 120.dp
private val BACKEND_ICON_TILE_SIZE = 40.dp
private val PIPELINE_MAX_STEPS_VALID_RANGE =
    SettingsDefaults.PIPELINE_MAX_STEPS_MIN..SettingsDefaults.PIPELINE_MAX_STEPS_MAX
