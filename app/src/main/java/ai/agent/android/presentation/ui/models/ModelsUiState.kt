package ai.agent.android.presentation.ui.models

import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.domain.models.AppError

/**
 * Represents the UI state for the Models management screen.
 *
 * @property availablePresets A list of pre-defined model URLs (e.g., Gemma 2B, 7B).
 * @property downloadedModels A list of models currently saved in the local database.
 * @property activeModel The currently active (selected) model, if any.
 * @property downloadProgress The progress percentage of the current download (0..100), or null if not downloading.
 * @property isDownloading True if a download is currently in progress.
 * @property downloadError Any error that occurred during the download process, or null.
 * @property customUrlInput The current text in the custom URL input field.
 */
data class ModelsUiState(
    val availablePresets: List<ModelPreset> = listOf(
        ModelPreset("Gemma-3n-E4B-it", "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm"),
        ModelPreset("Gemma-3n-E2B-it", "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm"),
        //ModelPreset("Qwen3.5-0.8B", "https://huggingface.co/g-ntovas/Qwen3.5-0.8B-LiteRT/resolve/main/qwen35_mm_q8_ekv2048.litertlm"),
        //ModelPreset("Qwen3.5-2B", "https://huggingface.co/g-ntovas/Qwen3.5-2B-LiteRT/resolve/main/qwen35_2b_mm_q4_block32_ekv4096.litertlm")
    ),
    val downloadedModels: List<LocalModelEntity> = emptyList(),
    val activeModel: LocalModelEntity? = null,
    val downloadProgress: Int? = null,
    val isDownloading: Boolean = false,
    val downloadError: AppError? = null,
    val customUrlInput: String = "",
    val authTokenInput: String = ""
)

/**
 * Represents a predefined model download option.
 *
 * @property name The human-readable name of the preset.
 * @property url The direct download URL for the preset model.
 */
data class ModelPreset(
    val name: String,
    val url: String
)
