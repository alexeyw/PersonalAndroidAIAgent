package ai.agent.android.data.network

import ai.agent.android.domain.models.DownloadState
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidModelDownloadManagerTest {

    private lateinit var context: Context
    private lateinit var downloadManager: DownloadManager
    private lateinit var sut: AndroidModelDownloadManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)
        
        every { context.getSystemService(Context.DOWNLOAD_SERVICE) } returns downloadManager
        
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()

        sut = AndroidModelDownloadManager(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `downloadModel emits Pending and Success when download finishes successfully`() = runTest {
        val url = "http://example.com/model.bin"
        val fileName = "model.bin"
        val downloadId = 1L
        val localUri = "file:///local/path/model.bin"

        every { downloadManager.enqueue(any()) } returns downloadId

        val cursor = mockk<Cursor>(relaxed = true)
        every { downloadManager.query(any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(DownloadManager.COLUMN_STATUS) } returns 0
        every { cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI) } returns 1
        every { cursor.getInt(0) } returns DownloadManager.STATUS_SUCCESSFUL
        every { cursor.getString(1) } returns localUri

        val emissions = mutableListOf<DownloadState>()
        val job = launch(UnconfinedTestDispatcher()) {
            sut.downloadModel(url, fileName).toList(emissions)
            assertTrue(emissions[0] is DownloadState.Pending)
            val successState = emissions[1] as DownloadState.Success
            assertEquals(localUri, successState.fileUri)
        }

        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `downloadModel emits Pending and Error when download fails`() = runTest {
        val url = "http://example.com/model.bin"
        val fileName = "model.bin"
        val downloadId = 1L
        val reasonCode = 1004 // Simulated error code

        every { downloadManager.enqueue(any()) } returns downloadId

        val cursor = mockk<Cursor>(relaxed = true)
        every { downloadManager.query(any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(DownloadManager.COLUMN_STATUS) } returns 0
        every { cursor.getColumnIndex(DownloadManager.COLUMN_REASON) } returns 1
        every { cursor.getInt(0) } returns DownloadManager.STATUS_FAILED
        every { cursor.getInt(1) } returns reasonCode

        val emissions = mutableListOf<DownloadState>()
        val job = launch(UnconfinedTestDispatcher()) {
            sut.downloadModel(url, fileName).toList(emissions)
            assertTrue(emissions[0] is DownloadState.Pending)
            val errorState = emissions[1] as DownloadState.Error
            val errorMsg = (errorState.error as AndroidModelDownloadManager.DownloadError).message
            assertTrue(errorMsg.contains(reasonCode.toString()))
        }

        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `downloadModel emits Pending and Error when URL is invalid`() = runTest {
        val url = "invalid_url"
        val fileName = "model.bin"

        every { Uri.parse(any()) } throws IllegalArgumentException("Invalid URL")

        val emissions = mutableListOf<DownloadState>()
        val job = launch(UnconfinedTestDispatcher()) {
            sut.downloadModel(url, fileName).toList(emissions)
            assertTrue(emissions[0] is DownloadState.Pending)
            assertTrue(emissions[1] is DownloadState.Error)
            val errorState = emissions[1] as DownloadState.Error
            val errorMsg = (errorState.error as AndroidModelDownloadManager.DownloadError).message
            assertTrue(errorMsg.contains("Invalid URL"))
        }

        job.cancel()
    }
}
