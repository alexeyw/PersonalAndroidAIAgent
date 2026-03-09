package ai.agent.android.presentation.ui.models

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Composable screen for managing LLM models. Allows users to view downloaded models,
 * select an active model, and download new models from presets or custom URLs.
 *
 * @param modifier The modifier to be applied to the layout.
 * @param viewModel The view model managing the state for this screen.
 */
@Composable
fun ModelsScreen(
    modifier: Modifier = Modifier,
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Model Management",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // HuggingFace Auth Token
        OutlinedTextField(
            value = uiState.authTokenInput,
            onValueChange = viewModel::onAuthTokenChanged,
            label = { Text("HuggingFace Auth Token (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isDownloading
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Custom URL Input
        OutlinedTextField(
            value = uiState.customUrlInput,
            onValueChange = viewModel::onCustomUrlChanged,
            label = { Text("Custom Model URL") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isDownloading
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val url = uiState.customUrlInput
                if (url.isNotBlank()) {
                    val fileName = url.substringAfterLast("/")
                    viewModel.startDownload(url, fileName.ifBlank { "custom_model.bin" })
                }
            },
            enabled = !uiState.isDownloading && uiState.customUrlInput.isNotBlank(),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Download Custom Model")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Presets
        Text(
            text = "Available Presets",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.availablePresets) { preset ->
                val isAlreadyDownloaded = uiState.downloadedModels.any { it.name == preset.url.substringAfterLast("/") }
                PresetItem(
                    preset = preset,
                    isDownloading = uiState.isDownloading,
                    isAlreadyDownloaded = isAlreadyDownloaded,
                    onDownload = {
                        val fileName = preset.url.substringAfterLast("/")
                        viewModel.startDownload(preset.url, fileName)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Downloaded Models",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(uiState.downloadedModels) { model ->
                DownloadedModelItem(
                    modelName = model.name,
                    isActive = model.isActive,
                    onMakeActive = { viewModel.setActiveModel(model.id) }
                )
            }
        }

        // Active Download Progress
        if (uiState.isDownloading) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Downloading...",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                uiState.downloadProgress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.End)
                    )
                } ?: CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        
        // Error Message
        uiState.downloadError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            val errorMessage = when (error) {
                is DownloadError -> error.message
                else -> "An unknown error occurred"
            }
            Text(
                text = "Error: $errorMessage",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PresetItem(
    preset: ModelPreset,
    isDownloading: Boolean,
    isAlreadyDownloaded: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = preset.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = preset.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
            Button(
                onClick = onDownload,
                enabled = !isDownloading && !isAlreadyDownloaded,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text(if (isAlreadyDownloaded) "Downloaded" else "Download")
            }
        }
    }
}

@Composable
private fun DownloadedModelItem(
    modelName: String,
    isActive: Boolean,
    onMakeActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isActive) {
                Button(onClick = onMakeActive) {
                    Text("Make Active")
                }
            } else {
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
