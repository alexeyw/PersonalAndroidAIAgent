package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.DownloadState
import kotlinx.coroutines.flow.Flow

/**
 * Interface responsible for managing the downloading of large LLM files.
 * This repository handles network requests and safe file storage, providing
 * a streaming state of the download process.
 *
 * A download outlives the screen that started it: the returned [Flow] only
 * *observes* work that runs independently, so ending collection (leaving
 * onboarding, closing the app) no longer ends the transfer. Stopping it is an
 * explicit act — [cancelDownload].
 */
interface ModelDownloadManager {

    /**
     * Starts a download for a specified model URL and saves it to local storage,
     * or attaches to the one already running for [fileName].
     *
     * @param url The direct URL to the model file (e.g., HuggingFace download link).
     * @param fileName The desired local filename for the model (e.g., "gemma-2b.bin").
     * @param useStoredAuth `true` to send the stored Hugging Face token, needed
     *   for gated repositories. The token is read where it is kept — the
     *   Keystore-backed store — rather than passed through here, so it never
     *   lands in the background-work database in the clear.
     * @return A [Flow] emitting [DownloadState] updates regarding the download
     *   progress. The flow completes once the download reaches a terminal state,
     *   and completes without a terminal emission if it was cancelled.
     */
    fun downloadModel(url: String, fileName: String, useStoredAuth: Boolean = false): Flow<DownloadState>

    /**
     * Cancels the download running for [fileName], if any.
     *
     * Bytes already fetched are kept on disk: re-requesting the same file from
     * the same URL resumes rather than starting over.
     *
     * @param fileName The local filename the download was started with.
     */
    fun cancelDownload(fileName: String)
}
