package ai.agent.android.data.network

import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.DownloadState
import ai.agent.android.domain.repositories.ModelDownloadManager
import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Implementation of [ModelDownloadManager] that uses the Android system's [DownloadManager]
 * to handle large file downloads efficiently in the background.
 *
 * @property context The application context used to access the [DownloadManager] system service.
 */
class AndroidModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelDownloadManager {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override fun downloadModel(url: String, fileName: String, authToken: String?): Flow<DownloadState> = flow {
        emit(DownloadState.Pending)

        val request = try {
            val req = DownloadManager.Request(url.toUri())
                .setTitle(fileName)
                .setDescription("Downloading AI Model")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, null, fileName)
            
            if (!authToken.isNullOrBlank()) {
                req.addRequestHeader("Authorization", "Bearer $authToken")
            }
            req
        } catch (e: Exception) {
            emit(DownloadState.Error(DownloadError("Invalid URL or request setup: ${e.message}")))
            return@flow
        }

        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            emit(DownloadState.Error(DownloadError("Failed to enqueue download: ${e.message}")))
            return@flow
        }

        var isFinished = false
        while (!isFinished) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else DownloadManager.STATUS_FAILED

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val uri = if (uriIndex >= 0) cursor.getString(uriIndex) else ""
                        emit(DownloadState.Success(uri ?: ""))
                        isFinished = true
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                        emit(DownloadState.Error(DownloadError("Download failed with reason code: $reason")))
                        isFinished = true
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                        if (bytesDownloadedIndex >= 0 && bytesTotalIndex >= 0) {
                            val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                            val bytesTotal = cursor.getLong(bytesTotalIndex)

                            if (bytesTotal > 0) {
                                val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                                emit(DownloadState.Downloading(progress))
                            } else {
                                emit(DownloadState.Downloading(0))
                            }
                        }
                    }
                }
                cursor.close()
            } else {
                cursor?.close()
                emit(DownloadState.Error(DownloadError("Download task not found or cancelled")))
                isFinished = true
            }

            if (!isFinished) {
                delay(1000L) // Poll every second
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /**
     * A simple implementation of [AppError.Network] for download failures.
     *
     * @property message The error message detailing the failure.
     */
    data class DownloadError(val message: String) : AppError.Network
}
