package ai.agent.android.presentation.ui.models

import ai.agent.android.R
import ai.agent.android.data.network.AndroidModelDownloadManager.DownloadError
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.design.components.controls.KnotworkField
import app.knotwork.design.components.controls.KnotworkPasswordField
import app.knotwork.design.components.controls.KnotworkTextField

/**
 * Composable screen for managing LLM models. Allows users to view downloaded models,
 * select an active model, and download new models from presets or custom URLs.
 *
 * @param modifier The modifier to be applied to the layout.
 * @param viewModel The view model managing the state for this screen.
 * @param onBack Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(modifier: Modifier = Modifier, viewModel: ModelsViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val unknownErrorText = stringResource(R.string.models_error_unknown)
    LaunchedEffect(uiState.downloadError) {
        uiState.downloadError?.let { error ->
            val errorMessage = when (error) {
                is DownloadError -> error.message
                else -> unknownErrorText
            }
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models_screen_title)) },
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // HuggingFace Auth Token — KnotworkPasswordField masks the token by
            // default and exposes an eye-toggle for the rare case the user
            // needs to verify what they pasted.
            KnotworkField(
                label = stringResource(R.string.models_field_auth_token),
            ) {
                KnotworkPasswordField(
                    value = uiState.authTokenInput,
                    onValueChange = viewModel::onAuthTokenChanged,
                    enabled = !uiState.isDownloading,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom URL Input — monospace so users can pick out path /
            // query-string components without sans-shaped letter-spacing
            // tripping them up.
            KnotworkField(
                label = stringResource(R.string.models_field_custom_url),
            ) {
                KnotworkTextField(
                    value = uiState.customUrlInput,
                    onValueChange = viewModel::onCustomUrlChanged,
                    enabled = !uiState.isDownloading,
                    monospace = true,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val defaultCustomFilename = stringResource(R.string.models_default_custom_filename)
            Button(
                onClick = {
                    val url = uiState.customUrlInput
                    if (url.isNotBlank()) {
                        val fileName = url.substringAfterLast("/")
                        viewModel.startDownload(url, fileName.ifBlank { defaultCustomFilename })
                    }
                },
                enabled = !uiState.isDownloading && uiState.customUrlInput.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.models_button_download_custom))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Presets
            Text(
                text = stringResource(R.string.models_section_presets),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.availablePresets) { preset ->
                    val isAlreadyDownloaded =
                        uiState.downloadedModels.any { it.name == preset.url.substringAfterLast("/") }
                    PresetItem(
                        preset = preset,
                        isDownloading = uiState.isDownloading,
                        isAlreadyDownloaded = isAlreadyDownloaded,
                        onDownload = {
                            val fileName = preset.url.substringAfterLast("/")
                            viewModel.startDownload(preset.url, fileName)
                        },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.models_section_downloaded),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                items(uiState.downloadedModels) { model ->
                    DownloadedModelItem(
                        modelName = model.name,
                        isActive = model.isActive,
                        onMakeActive = { viewModel.setActiveModel(model.id) },
                    )
                }
            }

            // Active Download Progress
            if (uiState.isDownloading) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.models_progress_downloading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    uiState.downloadProgress?.let { progress ->
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.models_progress_percent, progress),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                        ?: CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PresetItem(
    preset: ModelPreset,
    isDownloading: Boolean,
    isAlreadyDownloaded: Boolean,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = preset.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = preset.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            Button(
                onClick = onDownload,
                enabled = !isDownloading && !isAlreadyDownloaded,
                modifier = Modifier.padding(start = 16.dp),
            ) {
                Text(
                    stringResource(
                        if (isAlreadyDownloaded) R.string.models_button_downloaded else R.string.models_button_download,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DownloadedModelItem(modelName: String, isActive: Boolean, onMakeActive: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (!isActive) {
                Button(onClick = onMakeActive) {
                    Text(stringResource(R.string.models_button_make_active))
                }
            } else {
                Text(
                    text = stringResource(R.string.models_badge_active),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
