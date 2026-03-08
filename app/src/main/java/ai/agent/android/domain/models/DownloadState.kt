package ai.agent.android.domain.models

/**
 * Represents the current state of a model download process.
 * This sealed class is used to emit real-time updates from the DownloadManager
 * to the UI via Kotlin Flow.
 */
sealed class DownloadState {
    /**
     * The download task has been enqueued but hasn't started yet.
     */
    data object Pending : DownloadState()

    /**
     * The file is currently being downloaded.
     *
     * @property progress The download progress in percentage (0 to 100).
     */
    data class Downloading(val progress: Int) : DownloadState()

    /**
     * The download has completed successfully.
     *
     * @property fileUri The local file URI where the model was saved.
     */
    data class Success(val fileUri: String) : DownloadState()

    /**
     * The download failed due to a network or filesystem error.
     *
     * @property error The application error detailing what went wrong.
     */
    data class Error(val error: AppError) : DownloadState()
}
