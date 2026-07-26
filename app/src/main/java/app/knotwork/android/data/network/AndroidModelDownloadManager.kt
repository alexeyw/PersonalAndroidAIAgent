package app.knotwork.android.data.network

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.knotwork.android.data.services.ModelDownloadWorker
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.DownloadState
import app.knotwork.android.domain.repositories.ModelDownloadManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import javax.inject.Inject

/**
 * [ModelDownloadManager] that hands the transfer to [ModelDownloadWorker] and
 * reports its progress back as a [DownloadState] stream.
 *
 * The indirection is the point. A model bundle takes minutes to fetch, and the
 * user will leave the screen — to finish onboarding, to look around the app, or
 * simply because the phone locked. Running the transfer inside the collecting
 * coroutine tied its life to a ViewModel; running it as background work tied to
 * a foreground service does not.
 *
 * Work is keyed uniquely per target file with [ExistingWorkPolicy.KEEP], so
 * calling this again for a download already in flight **attaches** to it rather
 * than starting a second copy — which is exactly what re-entering the screen
 * should do.
 *
 * @property workManager Schedules and observes the download work.
 */
class AndroidModelDownloadManager @Inject constructor(private val workManager: WorkManager) : ModelDownloadManager {

    override fun downloadModel(url: String, fileName: String, useStoredAuth: Boolean): Flow<DownloadState> = flow {
        val uniqueName = uniqueWorkName(fileName)
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_URL to url,
                    ModelDownloadWorker.KEY_FILE_NAME to fileName,
                    ModelDownloadWorker.KEY_USE_STORED_AUTH to useStoredAuth,
                ),
            )
            // Retries resume rather than restart, so waiting for any connection
            // is strictly better than failing on a momentary drop.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)

        emitAll(
            workManager.getWorkInfosForUniqueWorkFlow(uniqueName)
                .transformWhile { infos ->
                    // A live run wins over any finished one still attached to
                    // the name: the list order is unspecified, and reading a
                    // previous download's Success would end this stream on the
                    // spot with the wrong answer.
                    val info = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()
                    info?.toDownloadState()?.let { emit(it) }
                    // A missing WorkInfo means the observation raced the enqueue;
                    // keep listening rather than reporting a finished download.
                    info == null || !info.state.isFinished
                }
                .distinctUntilChanged(),
        )
    }

    override fun cancelDownload(fileName: String) {
        workManager.cancelUniqueWork(uniqueWorkName(fileName))
    }

    /**
     * Projects a [WorkInfo] onto the download state the UI speaks.
     *
     * A cancelled download maps to `null` — the collector simply stops, because
     * the user who cancelled needs neither an error nor a success.
     */
    private fun WorkInfo.toDownloadState(): DownloadState? = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Pending
        // A running worker that has not reported a percent yet is still
        // connecting; reporting 0 % keeps the progress monotonic.
        WorkInfo.State.RUNNING -> DownloadState.Downloading(progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0))
        WorkInfo.State.SUCCEEDED -> outputData.getString(ModelDownloadWorker.KEY_OUTPUT_PATH)
            ?.let { DownloadState.Success(it) }
            ?: DownloadState.Error(DownloadError("Download finished without a file path."))
        WorkInfo.State.FAILED -> DownloadState.Error(
            DownloadError(
                message = outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Unknown download error",
                code = outputData
                    .getInt(ModelDownloadWorker.KEY_ERROR_CODE, ModelDownloadWorker.NO_HTTP_CODE)
                    .takeIf { it != ModelDownloadWorker.NO_HTTP_CODE },
            ),
        )
        WorkInfo.State.CANCELLED -> null
    }

    /**
     * A simple implementation of [AppError.Network] for download failures.
     *
     * @property message The error message detailing the failure.
     * @property code The HTTP status code when the failure was a non-2xx
     *   response, or `null` for transport/IO failures. Carried as a typed
     *   field so consumers can branch on the status (e.g. an access-gated
     *   401/403) without parsing it back out of [message].
     */
    data class DownloadError(val message: String, val code: Int? = null) : AppError.Network

    private companion object {

        /**
         * Unique-work name for a target file. Derived from the file name (not
         * the URL) because the file is what a second request would collide on.
         */
        fun uniqueWorkName(fileName: String): String = "model-download-$fileName"
    }
}
