package ai.agent.android.presentation.ui.settings.provider

import ai.agent.android.R
import ai.agent.android.data.engine.KoogModelMapper
import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.models.ProviderId
import ai.agent.android.domain.repositories.ApiKeyRepository
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.design.screens.settings.KnotworkProviderRow
import app.knotwork.design.screens.settings.OllamaProviderInputs
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Standalone editor for a single external LLM provider, reached from
 * the Settings → External providers nav-row.
 *
 * Wraps the catalog [KnotworkProviderRow] inside a top-level scaffold —
 * lets users tweak API key, model and (for Ollama) the LAN base URL and
 * context window without scrolling back inside the main Settings stack.
 *
 * @param providerId Which provider to render. Determines which fields
 *   appear (Ollama gets two extra inputs).
 * @param onBack Invoked when the user taps the system back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    providerId: ProviderId,
    onBack: () -> Unit,
    viewModel: ProviderDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(providerId) { viewModel.bind(providerId) }
    val uiState by viewModel.uiState.collectAsState()
    val locale = LocalConfiguration.current.locales[0]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_provider_detail_title, providerLabel(providerId)),
                        style = KnotworkTextStyles.TitleLg,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KnotworkTheme.spacing.sp4),
            ) {
                when (providerId) {
                    ProviderId.OpenAi -> CommonProviderEditor(
                        title = providerLabel(providerId),
                        keyValue = uiState.openAiKey,
                        onKeyChange = viewModel::updateOpenAiKey,
                        modelValue = uiState.openAiModel,
                        onModelChange = viewModel::updateOpenAiModel,
                        availableModels = KoogModelMapper.getOpenAIModelIdList(),
                    )
                    ProviderId.Anthropic -> CommonProviderEditor(
                        title = providerLabel(providerId),
                        keyValue = uiState.anthropicKey,
                        onKeyChange = viewModel::updateAnthropicKey,
                        modelValue = uiState.anthropicModel,
                        onModelChange = viewModel::updateAnthropicModel,
                        availableModels = KoogModelMapper.getAnthropicModelIdList(),
                    )
                    ProviderId.Google -> CommonProviderEditor(
                        title = providerLabel(providerId),
                        keyValue = uiState.googleKey,
                        onKeyChange = viewModel::updateGoogleKey,
                        modelValue = uiState.googleModel,
                        onModelChange = viewModel::updateGoogleModel,
                        availableModels = KoogModelMapper.getGoogleModelIdList(),
                    )
                    ProviderId.DeepSeek -> CommonProviderEditor(
                        title = providerLabel(providerId),
                        keyValue = uiState.deepSeekKey,
                        onKeyChange = viewModel::updateDeepSeekKey,
                        modelValue = uiState.deepSeekModel,
                        onModelChange = viewModel::updateDeepSeekModel,
                        availableModels = KoogModelMapper.getDeepSeekModelIdList(),
                    )
                    ProviderId.Ollama -> {
                        val ollamaError = if (uiState.ollamaBaseUrlInvalid) {
                            stringResource(R.string.settings_ollama_base_url_error)
                        } else {
                            null
                        }
                        KnotworkProviderRow(
                            title = providerLabel(providerId),
                            keyValue = "",
                            onKeyChange = {},
                            keyLabel = "",
                            modelValue = uiState.ollamaModel,
                            onModelChange = viewModel::updateOllamaModel,
                            modelLabel = stringResource(R.string.settings_ollama_model_label),
                            availableModels = emptyList(),
                            // Ollama runs LAN-local without authentication — hide the API-key
                            // input entirely. Only base URL, model name, and context window
                            // remain configurable.
                            showApiKey = false,
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
                }
            }
        }
    }
}

@Composable
private fun CommonProviderEditor(
    title: String,
    keyValue: String,
    onKeyChange: (String) -> Unit,
    modelValue: String,
    onModelChange: (String) -> Unit,
    availableModels: List<String>,
) {
    KnotworkProviderRow(
        title = title,
        keyValue = keyValue,
        onKeyChange = onKeyChange,
        keyLabel = stringResource(R.string.settings_provider_api_key_label, title),
        modelValue = modelValue,
        onModelChange = onModelChange,
        modelLabel = stringResource(R.string.settings_provider_model_label, title),
        availableModels = availableModels,
    )
}

@Composable
private fun providerLabel(id: ProviderId): String = when (id) {
    ProviderId.OpenAi -> "OpenAI"
    ProviderId.Anthropic -> "Anthropic"
    ProviderId.Google -> "Google"
    ProviderId.DeepSeek -> "DeepSeek"
    ProviderId.Ollama -> "Ollama"
}

/**
 * UI state slice surfaced by [ProviderDetailViewModel].
 *
 * Kept as a single data class so the screen recomposes against one
 * snapshot.
 */
data class ProviderDetailUiState(
    val openAiKey: String = "",
    val openAiModel: String = "",
    val anthropicKey: String = "",
    val anthropicModel: String = "",
    val googleKey: String = "",
    val googleModel: String = "",
    val deepSeekKey: String = "",
    val deepSeekModel: String = "",
    val ollamaBaseUrl: String = "",
    val ollamaModel: String = "",
    val ollamaContextWindow: String = "4096",
    val ollamaBaseUrlInvalid: Boolean = false,
)

/**
 * ViewModel backing [ProviderDetailScreen] — owns the API-key / model
 * edits routed through [ApiKeyRepository]. Independent from the main
 * Settings VM so the provider detail screen can be reached as a deep-
 * link target without forcing the entire Settings tree to load.
 */
@HiltViewModel
class ProviderDetailViewModel @Inject constructor(private val apiKeyRepository: ApiKeyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderDetailUiState())
    val uiState: StateFlow<ProviderDetailUiState> = _uiState.asStateFlow()

    /**
     * Binds the screen to the relevant API-key flows. Idempotent —
     * `LaunchedEffect(providerId)` invokes this on the very first
     * composition.
     */
    fun bind(providerId: ProviderId) {
        when (providerId) {
            ProviderId.OpenAi -> {
                apiKeyRepository.getOpenAIKey().onEach { v -> _uiState.update { it.copy(openAiKey = v.orEmpty()) } }
                    .launchIn(viewModelScope)
                apiKeyRepository.getOpenAIModel().onEach { v -> _uiState.update { it.copy(openAiModel = v.orEmpty()) } }
                    .launchIn(viewModelScope)
            }
            ProviderId.Anthropic -> {
                apiKeyRepository.getAnthropicKey()
                    .onEach { v -> _uiState.update { it.copy(anthropicKey = v.orEmpty()) } }
                    .launchIn(viewModelScope)
                apiKeyRepository.getAnthropicModel()
                    .onEach { v -> _uiState.update { it.copy(anthropicModel = v.orEmpty()) } }
                    .launchIn(viewModelScope)
            }
            ProviderId.Google -> {
                apiKeyRepository.getGoogleKey()
                    .onEach { v -> _uiState.update { it.copy(googleKey = v.orEmpty()) } }
                    .launchIn(viewModelScope)
                apiKeyRepository.getGoogleModel()
                    .onEach { v -> _uiState.update { it.copy(googleModel = v.orEmpty()) } }
                    .launchIn(viewModelScope)
            }
            ProviderId.DeepSeek -> {
                apiKeyRepository.getDeepSeekKey()
                    .onEach { v -> _uiState.update { it.copy(deepSeekKey = v.orEmpty()) } }
                    .launchIn(viewModelScope)
                apiKeyRepository.getDeepSeekModel()
                    .onEach { v -> _uiState.update { it.copy(deepSeekModel = v.orEmpty()) } }
                    .launchIn(viewModelScope)
            }
            ProviderId.Ollama -> {
                apiKeyRepository.getOllamaBaseUrl()
                    .onEach { v -> _uiState.update { it.copy(ollamaBaseUrl = v.orEmpty()) } }
                    .launchIn(viewModelScope)
                apiKeyRepository.getOllamaModelName()
                    .onEach { v -> _uiState.update { it.copy(ollamaModel = v.orEmpty()) } }
                    .launchIn(viewModelScope)
                apiKeyRepository.getOllamaContextWindowSize()
                    .onEach { v -> _uiState.update { it.copy(ollamaContextWindow = v.toString()) } }
                    .launchIn(viewModelScope)
            }
        }
    }

    fun updateOpenAiKey(value: String) {
        viewModelScope.launch { apiKeyRepository.setOpenAIKey(value.takeIf { it.isNotBlank() }) }
    }

    fun updateOpenAiModel(value: String) {
        viewModelScope.launch { apiKeyRepository.setOpenAIModel(value.takeIf { it.isNotBlank() }) }
    }

    fun updateAnthropicKey(value: String) {
        viewModelScope.launch { apiKeyRepository.setAnthropicKey(value.takeIf { it.isNotBlank() }) }
    }

    fun updateAnthropicModel(value: String) {
        viewModelScope.launch { apiKeyRepository.setAnthropicModel(value.takeIf { it.isNotBlank() }) }
    }

    fun updateGoogleKey(value: String) {
        viewModelScope.launch { apiKeyRepository.setGoogleKey(value.takeIf { it.isNotBlank() }) }
    }

    fun updateGoogleModel(value: String) {
        viewModelScope.launch { apiKeyRepository.setGoogleModel(value.takeIf { it.isNotBlank() }) }
    }

    fun updateDeepSeekKey(value: String) {
        viewModelScope.launch { apiKeyRepository.setDeepSeekKey(value.takeIf { it.isNotBlank() }) }
    }

    fun updateDeepSeekModel(value: String) {
        viewModelScope.launch { apiKeyRepository.setDeepSeekModel(value.takeIf { it.isNotBlank() }) }
    }

    fun updateOllamaBaseUrl(value: String) {
        _uiState.update { it.copy(ollamaBaseUrl = value, ollamaBaseUrlInvalid = value.isBlank()) }
        viewModelScope.launch { apiKeyRepository.setOllamaBaseUrl(value.takeIf { it.isNotBlank() }) }
    }

    fun updateOllamaModel(value: String) {
        viewModelScope.launch { apiKeyRepository.setOllamaModelName(value.takeIf { it.isNotBlank() }) }
    }

    fun updateOllamaContextWindow(value: String) {
        viewModelScope.launch {
            val size = value.toIntOrNull() ?: SettingsDefaults.OLLAMA_CONTEXT_WINDOW_DEFAULT
            apiKeyRepository.setOllamaContextWindowSize(size)
        }
    }
}
