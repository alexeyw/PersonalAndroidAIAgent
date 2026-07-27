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
     * Attaches to the download already running for [fileName], without ever
     * starting one.
     *
     * Opening a screen must not begin a transfer, so this is deliberately not
     * [downloadModel] with a flag: a screen restoring its state after the user
     * navigated away needs to *find* the download, and find nothing when there
     * is none.
     *
     * @param fileName The local filename the download was started with.
     * @return A [Flow] mirroring the running download through to its terminal
     *   state, or an empty flow when nothing is running for that file.
     */
    fun observeDownload(fileName: String): Flow<DownloadState>

    /**
     * Reports the model download currently in flight, if any.
     *
     * The file name is the part a returning screen cannot know on its own —
     * without it there is no way to re-attach after the ViewModel that started
     * the download is gone.
     *
     * @return A [Flow] emitting the live download (file name + latest state), or
     *   `null` whenever none is running.
     */
    fun observeActiveDownload(): Flow<ActiveDownload?>

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

/**
 * A model download currently in flight.
 *
 * @property fileName Local file name the download was started with — the key a
 *   screen needs to re-attach.
 * @property state Latest state of that download.
 */
data class ActiveDownload(val fileName: String, val state: DownloadState)
