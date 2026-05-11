package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.DownloadState
import kotlinx.coroutines.flow.Flow

/**
 * Interface responsible for managing the downloading of large LLM files.
 * This repository handles network requests and safe file storage, providing
 * a streaming state of the download process.
 */
interface ModelDownloadManager {

    /**
     * Starts a download for a specified model URL and saves it to local storage.
     *
     * @param url The direct URL to the model file (e.g., HuggingFace download link).
     * @param fileName The desired local filename for the model (e.g., "gemma-2b.bin").
     * @param authToken Optional authorization token (e.g. HuggingFace Bearer token).
     * @return A [Flow] emitting [DownloadState] updates regarding the download progress.
     */
    fun downloadModel(url: String, fileName: String, authToken: String? = null): Flow<DownloadState>
}
