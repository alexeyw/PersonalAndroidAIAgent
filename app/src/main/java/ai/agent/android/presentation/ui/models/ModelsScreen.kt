package ai.agent.android.presentation.ui.models

import ai.agent.android.R
import ai.agent.android.data.network.AndroidModelDownloadManager.DownloadError
import ai.agent.android.domain.models.LocalModel
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.knotwork.design.screens.models.ActiveModelRow
import app.knotwork.design.screens.models.ModelsCallbacks
import app.knotwork.design.screens.models.ModelsContent
import app.knotwork.design.screens.models.ModelsStrings
import app.knotwork.design.screens.models.ModelsViewState
import app.knotwork.design.screens.models.ModelsVisualState
import app.knotwork.design.screens.models.PresetRow
import app.knotwork.design.screens.models.PresetStatus
import java.util.Locale

/**
 * Slim app-side Models mapper. Subscribes to [ModelsViewModel.uiState],
 * folds it into the catalog [ModelsViewState], and renders [ModelsContent].
 *
 * @param viewModel Hilt-injected [ModelsViewModel].
 * @param onBack Navigation callback invoked when the user taps the
 * TopAppBar back arrow.
 * @param modifier Optional layout modifier.
 */
@Composable
fun ModelsScreen(modifier: Modifier = Modifier, viewModel: ModelsViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val unknownErrorText = stringResource(R.string.models_error_unknown)
    val defaultCustomFilename = stringResource(R.string.models_default_custom_filename)
    val strings = modelsStrings()

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

    val viewState = remember(uiState) { uiState.toViewState(strings.subtitleFormat) }

    ModelsContent(
        state = viewState,
        modifier = modifier,
        strings = strings.content,
        callbacks = ModelsCallbacks(
            onBack = onBack,
            onAuthTokenChange = viewModel::onAuthTokenChanged,
            onAuthTokenPaste = { viewModel.onAuthTokenChanged(readClipboard(context)) },
            onCustomUrlChange = viewModel::onCustomUrlChanged,
            onCustomUrlSubmit = {
                val url = uiState.customUrlInput
                if (url.isNotBlank()) {
                    val fileName = url.substringAfterLast(delimiter = "/").ifBlank { defaultCustomFilename }
                    viewModel.startDownload(url, fileName)
                }
            },
            onPresetDownload = { presetId ->
                uiState.availablePresets.find { it.url.toPresetId() == presetId }?.let { preset ->
                    val fileName = preset.url.substringAfterLast(delimiter = "/")
                    viewModel.startDownload(preset.url, fileName)
                }
            },
            onPresetCancelDownload = { viewModel.cancelDownload() },
            onPresetOpen = { presetId ->
                uiState.downloadedModels.find { it.name.toPresetId() == presetId }?.let { model ->
                    viewModel.setActiveModel(model.id)
                }
            },
            onActiveOpen = {},
            onOverflowMenu = {},
            onRetry = {},
        ),
    )
}

/** Pull the latest plain-text clipboard content for the HF auth-token paste action. */
private fun readClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val item = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
    return item?.text?.toString().orEmpty()
}

/**
 * Map the in-app [ModelsUiState] onto the catalog [ModelsViewState] consumed by
 * `ModelsContent`. Pulled out as a regular function so it can be exercised by
 * unit tests independently of the composable subscription.
 *
 * @param subtitleFormat localised `"%1$d active · %2$d on disk · %3$s"` template.
 */
internal fun ModelsUiState.toViewState(subtitleFormat: String): ModelsViewState {
    val totalSizeGb = downloadedModels.sumOf { it.size }.toGigabytesLabel()
    val subtitle = subtitleFormat.format(
        if (activeModel != null) 1 else 0,
        downloadedModels.size,
        totalSizeGb,
    )
    val active = activeModel?.let { model ->
        ActiveModelRow(
            id = model.id,
            displayName = model.name,
            meta = model.toMetaLine(),
        )
    }
    val downloadingName = activeDownloadFileName
    val presets = availablePresets.map { preset ->
        val fileName = preset.url.substringAfterLast(delimiter = "/")
        val onDisk = downloadedModels.any { it.name == fileName }
        val status: PresetStatus = when {
            downloadingName == fileName -> PresetStatus.Downloading(
                progress = downloadProgress ?: 0,
                metaLine = null,
            )
            onDisk -> PresetStatus.OnDisk(
                sizeMeta = downloadedModels.firstOrNull { it.name == fileName }?.toMetaLine(),
            )
            else -> PresetStatus.Idle()
        }
        PresetRow(
            id = preset.url.toPresetId(),
            name = preset.name,
            source = preset.url.toShortSource(),
            status = status,
        )
    }
    return ModelsViewState(
        visualState = ModelsVisualState.Default,
        active = active,
        authToken = authTokenInput,
        customUrl = customUrlInput,
        customUrlEnabled = !isDownloading,
        presets = presets,
        subtitle = subtitle,
    )
}

private fun LocalModel.toMetaLine(): String {
    val sizeLabel = size.toGigabytesLabel()
    return "$sizeLabel · NPU · LiteRT"
}

private fun String.toPresetId(): String = this
    .substringAfterLast(delimiter = "/")
    .substringBeforeLast(delimiter = ".")

private fun String.toShortSource(): String {
    val withoutScheme = removePrefix(prefix = "https://").removePrefix(prefix = "http://")
    return if (withoutScheme.length > MAX_SOURCE_LEN) {
        withoutScheme.take(MAX_SOURCE_LEN) + "…"
    } else {
        withoutScheme
    }
}

private const val MAX_SOURCE_LEN = 44

private fun Long.toGigabytesLabel(): String {
    if (this <= 0L) return "0 GB"
    val gb = this.toDouble() / GIGABYTE
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        val mb = this.toDouble() / MEGABYTE
        String.format(Locale.US, "%.0f MB", mb)
    }
}

private const val MEGABYTE = 1024.0 * 1024.0
private const val GIGABYTE = MEGABYTE * 1024.0

/** Bundle of localised display strings threaded into [ModelsContent]. */
private data class LocalisedModelsStrings(val content: ModelsStrings, val subtitleFormat: String)

@Composable
private fun modelsStrings(): LocalisedModelsStrings = LocalisedModelsStrings(
    content = ModelsStrings(
        title = stringResource(R.string.models_screen_title),
        backCd = stringResource(R.string.common_back),
        overflowCd = stringResource(R.string.models_overflow_cd),
        activeBadge = stringResource(R.string.models_active_badge),
        hfSection = stringResource(R.string.models_section_hf),
        hfOptional = stringResource(R.string.models_section_hf_optional),
        hfPlaceholder = stringResource(R.string.models_field_hf_placeholder),
        hfPaste = stringResource(R.string.models_action_paste),
        customUrlSection = stringResource(R.string.models_section_custom_url),
        customUrlPlaceholder = stringResource(R.string.models_field_url_placeholder),
        customUrlGet = stringResource(R.string.models_action_get),
        formatHint = stringResource(R.string.models_hint_formats),
        presetsSection = stringResource(R.string.models_section_available_presets),
        presetsAll = stringResource(R.string.models_action_all),
        presetGet = stringResource(R.string.models_preset_get),
        presetOnDisk = stringResource(R.string.models_preset_on_disk),
        presetCancelCd = stringResource(R.string.models_preset_cancel_cd),
        emptyTitle = stringResource(R.string.models_empty_title),
        emptySubtitle = stringResource(R.string.models_empty_subtitle),
        emptyCta = stringResource(R.string.models_empty_cta),
        errorTitle = stringResource(R.string.models_error_title),
        errorRetry = stringResource(R.string.common_retry),
    ),
    subtitleFormat = stringResource(R.string.models_subtitle_format),
)
