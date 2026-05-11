package ai.agent.android.data.network

import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.DownloadState
import ai.agent.android.domain.repositories.ModelDownloadManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.buffer
import okio.sink
import java.io.File
import javax.inject.Inject

/**
 * Implementation of [ModelDownloadManager] that uses [OkHttp]
 * to handle large file downloads efficiently. This bypasses the Android system's
 * [android.app.DownloadManager] cache limits (which frequently cause ERROR_INSUFFICIENT_SPACE
 * for gigabyte-sized LLMs on emulators) by streaming data directly to external storage.
 *
 * @property context The application context used to access external files directory.
 * @property client The injected OkHttpClient for making network requests.
 */
class AndroidModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) : ModelDownloadManager {

    override fun downloadModel(url: String, fileName: String, authToken: String?): Flow<DownloadState> = flow {
        emit(DownloadState.Pending)

        try {
            val requestBuilder = Request.Builder().url(url)
            if (!authToken.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $authToken")
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error(DownloadError("Server returned code: ${response.code}")))
                return@flow
            }

            val body = response.body
            if (body == ResponseBody.EMPTY) {
                emit(DownloadState.Error(DownloadError("Empty response body from server")))
                return@flow
            }

            val targetFile = File(context.getExternalFilesDir(null), fileName)
            val contentLength = body.contentLength()

            body.source().use { source ->
                targetFile.sink().buffer().use { sink ->
                    var totalBytesRead = 0L
                    var lastProgressEmit = -1
                    val buffer = okio.Buffer()
                    val bufferSize = DOWNLOAD_BUFFER_BYTES

                    var bytesRead: Long
                    while (source.read(buffer, bufferSize).also { bytesRead = it } != -1L) {
                        sink.write(buffer, bytesRead)
                        totalBytesRead += bytesRead

                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            if (progress != lastProgressEmit) {
                                emit(DownloadState.Downloading(progress))
                                lastProgressEmit = progress
                            }
                        } else {
                            // If total size is unknown, just emit 0 or indeterminate
                            emit(DownloadState.Downloading(0))
                        }
                    }
                    sink.flush()
                }
            }

            emit(DownloadState.Success(targetFile.absolutePath))
        } catch (e: Exception) {
            emit(DownloadState.Error(DownloadError(e.message ?: "Unknown download error")))
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /**
     * A simple implementation of [AppError.Network] for download failures.
     *
     * @property message The error message detailing the failure.
     */
    data class DownloadError(val message: String) : AppError.Network

    private companion object {
        /** Size, in bytes, of the chunk read from the network and flushed to disk on each loop. */
        const val DOWNLOAD_BUFFER_BYTES: Long = 8_192L
    }
}
