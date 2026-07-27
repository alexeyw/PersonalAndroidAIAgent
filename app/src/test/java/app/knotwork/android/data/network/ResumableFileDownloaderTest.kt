package app.knotwork.android.data.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Covers the streaming downloader, with the weight on the paths that only
 * matter once a transfer can be interrupted: resuming a partial file, refusing
 * to resume when that would corrupt the result, and never letting an unfinished
 * transfer sit at the final file name where it would pass for an installed
 * model.
 */
class ResumableFileDownloaderTest {

    private val url = "http://example.com/model.bin"

    private lateinit var context: Context
    private lateinit var client: OkHttpClient
    private lateinit var tempDir: File
    private lateinit var downloader: ResumableFileDownloader

    /** Captures the request handed to OkHttp so header assertions can read it. */
    private val sentRequest = slot<Request>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        client = mockk(relaxed = true)
        tempDir = File(System.getProperty("java.io.tmpdir"), "resumable_download_test_${System.nanoTime()}")
        tempDir.mkdirs()
        every { context.getExternalFilesDir(null) } returns tempDir
        downloader = ResumableFileDownloader(context, client)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `given a fresh download when it completes then the file lands at its final name`() = runTest {
        respondWith(response(code = 200, body = "mock_model_data"))
        val progress = mutableListOf<Int>()

        val outcome = downloader.download(url, "model.bin", authToken = null) { progress += it }

        val success = outcome as ResumableFileDownloader.Outcome.Success
        val file = File(tempDir, "model.bin")
        assertEquals(file.absolutePath, success.path)
        assertEquals("mock_model_data", file.readText())
        assertEquals(listOf(100), progress)
        // Nothing partial is left behind once the rename happened.
        assertTrue(tempDir.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun `given an interrupted transfer when it resumes then the range is requested and bytes are appended`() = runTest {
        partFileFor("model.bin").writeText("abc")
        respondWith(response(code = 206, body = "def"))

        val outcome = downloader.download(url, "model.bin", authToken = null) {}

        assertEquals("bytes=3-", sentRequest.captured.header("Range"))
        val success = outcome as ResumableFileDownloader.Outcome.Success
        // The prefix already on disk plus the newly fetched suffix — the
        // whole point of resuming.
        assertEquals("abcdef", File(success.path).readText())
    }

    @Test
    fun `given a server that ignores the range when resuming then the stale prefix is discarded`() = runTest {
        partFileFor("model.bin").writeText("abc")
        // 200 (not 206) means the body is the whole file again; appending it
        // would produce a file with a duplicated prefix that still looks valid.
        respondWith(response(code = 200, body = "whole-file"))

        val outcome = downloader.download(url, "model.bin", authToken = null) {}

        assertEquals("whole-file", File((outcome as ResumableFileDownloader.Outcome.Success).path).readText())
    }

    @Test
    fun `given a rejected range when downloading then it restarts from zero`() = runTest {
        partFileFor("model.bin").writeText("stale-and-too-long")
        val rangeRejected = mockk<Call>()
        every { rangeRejected.execute() } returns response(code = 416, body = "")
        val fresh = mockk<Call>()
        every { fresh.execute() } returns response(code = 200, body = "fresh")
        every { client.newCall(any()) } returnsMany listOf(rangeRejected, fresh)

        val outcome = downloader.download(url, "model.bin", authToken = null) {}

        assertEquals("fresh", File((outcome as ResumableFileDownloader.Outcome.Success).path).readText())
    }

    @Test
    fun `given a partial file from another URL when downloading then it is discarded`() = runTest {
        val stale = File(tempDir, "model.bin.deadbeef.part")
        stale.writeText("bytes of a different file")
        respondWith(response(code = 200, body = "correct"))

        val outcome = downloader.download(url, "model.bin", authToken = null) {}

        // Splicing two different files together would yield a bundle that looks
        // complete and is unusable — so the mismatched part goes.
        assertFalse(stale.exists())
        assertEquals("correct", File((outcome as ResumableFileDownloader.Outcome.Success).path).readText())
    }

    @Test
    fun `given a transport failure when downloading then the partial bytes survive for the next attempt`() = runTest {
        partFileFor("model.bin").writeText("abc")
        val call = mockk<Call>()
        every { call.execute() } throws IOException("Network timeout")
        every { client.newCall(any()) } returns call

        val outcome = downloader.download(url, "model.bin", authToken = null) {}

        val failure = outcome as ResumableFileDownloader.Outcome.Failure
        assertTrue(failure.message.contains("Network timeout"))
        // No HTTP status — the caller reads this as "retrying may help".
        assertNull(failure.httpCode)
        assertEquals("abc", partFileFor("model.bin").readText())
    }

    @Test
    fun `given an HTTP error when downloading then the status is carried on the failure`() = runTest {
        respondWith(response(code = 404, body = ""))

        val outcome = downloader.download(url, "model.bin", authToken = null) {}

        val failure = outcome as ResumableFileDownloader.Outcome.Failure
        assertEquals(404, failure.httpCode)
        assertTrue(failure.message.contains("404"))
        assertFalse(File(tempDir, "model.bin").exists())
    }

    @Test
    fun `given a traversal-only file name when downloading then it is rejected`() = runTest {
        respondWith(response(code = 200, body = "data"))

        val outcome = downloader.download(url, "..", authToken = null) {}

        val failure = outcome as ResumableFileDownloader.Outcome.Failure
        assertTrue(failure.message.contains("Invalid model file name"))
    }

    @Test
    fun `given separators in the file name when downloading then they flatten inside the models dir`() = runTest {
        respondWith(response(code = 200, body = "payload"))

        val outcome = downloader.download(url, "../../evil.bin", authToken = null) {}

        val success = outcome as ResumableFileDownloader.Outcome.Success
        assertEquals(File(tempDir, ".._.._evil.bin").absolutePath, success.path)
        assertEquals("payload", File(success.path).readText())
    }

    @Test
    fun `given names differing only by subdirectory when downloading then they stay distinct on disk`() = runTest {
        val q4 = mockk<Call>()
        every { q4.execute() } returns response(code = 200, body = "four")
        val q8 = mockk<Call>()
        every { q8.execute() } returns response(code = 200, body = "eight")
        every { client.newCall(any()) } returnsMany listOf(q4, q8)

        val first = downloader.download(url, "q4/model.litertlm", authToken = null) {}
        val second = downloader.download(url, "q8/model.litertlm", authToken = null) {}

        val pathA = (first as ResumableFileDownloader.Outcome.Success).path
        val pathB = (second as ResumableFileDownloader.Outcome.Success).path
        assertNotEquals(pathA, pathB)
        assertEquals("four", File(pathA).readText())
        assertEquals("eight", File(pathB).readText())
    }

    @Test
    fun `given a gated repository when downloading then the bearer token is sent`() = runTest {
        respondWith(response(code = 200, body = "gated"))

        downloader.download(url, "model.bin", authToken = "hf_secret") {}

        assertEquals("Bearer hf_secret", sentRequest.captured.header("Authorization"))
    }

    /** Mirrors the downloader's own part-file naming so tests can seed one. */
    private fun partFileFor(fileName: String): File =
        File(tempDir, "$fileName.${Integer.toHexString(url.hashCode())}.part")

    private fun response(code: Int, body: String): Response = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody())
        .build()

    /** Stubs a single response and captures the request that asked for it. */
    private fun respondWith(response: Response) {
        val call = mockk<Call>()
        every { call.execute() } returns response
        every { client.newCall(capture(sentRequest)) } returns call
    }
}
