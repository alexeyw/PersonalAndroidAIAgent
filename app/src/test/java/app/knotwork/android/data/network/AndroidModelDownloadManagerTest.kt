package app.knotwork.android.data.network

import android.content.Context
import app.knotwork.android.domain.models.DownloadState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class AndroidModelDownloadManagerTest {

    private lateinit var context: Context
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var sut: AndroidModelDownloadManager
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        okHttpClient = mockk(relaxed = true)

        // Mock getExternalFilesDir to return a temporary directory for testing
        tempDir = File(System.getProperty("java.io.tmpdir"), "test_downloads")
        tempDir.mkdirs()
        every { context.getExternalFilesDir(null) } returns tempDir

        sut = AndroidModelDownloadManager(context, okHttpClient)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `downloadModel emits Pending, Downloading, and Success when download finishes successfully`() = runTest {
        val url = "http://example.com/model.bin"
        val fileName = "model.bin"
        val expectedContent = "mock_model_data"

        // Mock OkHttp Call and Response
        val mockCall = mockk<Call>()
        val mockResponse = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(expectedContent.toResponseBody())
            .build()

        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse

        val emissions = mutableListOf<DownloadState>()
        val job = launch(UnconfinedTestDispatcher()) {
            sut.downloadModel(url, fileName).toList(emissions)
        }

        // Wait for flow to finish
        job.join()

        assertTrue("Expected at least Pending and Success states", emissions.size >= 2)
        assertTrue(emissions.first() is DownloadState.Pending)

        val successState = emissions.last() as DownloadState.Success
        val expectedFile = File(tempDir, fileName)
        assertEquals(expectedFile.absolutePath, successState.fileUri)

        // Verify file content was written correctly
        assertTrue(expectedFile.exists())
        assertEquals(expectedContent, expectedFile.readText())

        // Cleanup
        expectedFile.delete()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `downloadModel rejects a traversal-only file name`() = runTest {
        val url = "http://example.com/model.bin"
        val mockCall = mockk<Call>()
        val mockResponse = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("data".toResponseBody())
            .build()
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse

        val emissions = mutableListOf<DownloadState>()
        val job = launch(UnconfinedTestDispatcher()) {
            // "name" collapses to "..", which would escape the models dir.
            sut.downloadModel(url, "..").toList(emissions)
        }
        job.join()

        assertTrue(emissions.last() is DownloadState.Error)
        val errorMsg = ((emissions.last() as DownloadState.Error).error as AndroidModelDownloadManager.DownloadError).message
        assertTrue("Expected invalid-name error, got: $errorMsg", errorMsg.contains("Invalid model file name"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `downloadModel strips path segments so a traversal name writes inside the models dir`() = runTest {
        val url = "http://example.com/model.bin"
        val content = "payload"
        val mockCall = mockk<Call>()
        val mockResponse = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(content.toResponseBody())
            .build()
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse

        val emissions = mutableListOf<DownloadState>()
        val job = launch(UnconfinedTestDispatcher()) {
            sut.downloadModel(url, "../../evil.bin").toList(emissions)
        }
        job.join()

        val success = emissions.last() as DownloadState.Success
        // The "../../" prefix is stripped; the file lands inside tempDir as evil.bin.
        val expectedFile = File(tempDir, "evil.bin")
        assertEquals(expectedFile.absolutePath, success.fileUri)
        assertTrue("file must be written inside the models dir", expectedFile.exists())
        assertEquals(content, expectedFile.readText())
        expectedFile.delete()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `downloadModel emits Pending and Error when network request fails`() = runTest {
        val url = "http://example.com/model.bin"
        val fileName = "model.bin"

        val mockCall = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } throws IOException("Network timeout")

        val emissions = mutableListOf<DownloadState>()
        val job = launch(UnconfinedTestDispatcher()) {
            sut.downloadModel(url, fileName).toList(emissions)
        }

        job.join()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is DownloadState.Pending)
        assertTrue(emissions[1] is DownloadState.Error)
        val errorState = emissions[1] as DownloadState.Error
        val errorMsg = (errorState.error as AndroidModelDownloadManager.DownloadError).message
        assertTrue(errorMsg.contains("Network timeout"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `downloadModel emits Pending and Error when server returns error code`() = runTest {
        val url = "http://example.com/model.bin"
        val fileName = "model.bin"

        val mockCall = mockk<Call>()
        val mockResponse = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .build()

        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse

        val emissions = mutableListOf<DownloadState>()
        val job = launch(UnconfinedTestDispatcher()) {
            sut.downloadModel(url, fileName).toList(emissions)
        }

        job.join()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is DownloadState.Pending)
        assertTrue(emissions[1] is DownloadState.Error)
        val errorState = emissions[1] as DownloadState.Error
        val errorMsg = (errorState.error as AndroidModelDownloadManager.DownloadError).message
        assertTrue(errorMsg.contains("Server returned code: 404"))
    }
}
