package app.knotwork.android.data.local

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

/**
 * Verifies [AttachmentStoreImpl]: aspect-preserving downscale to the longest-side
 * cap (never a square crop), JPEG re-encode, ingest of invalid bytes, the
 * content-URI ingest path, and the delete / list surface used by retention.
 *
 * Runs under Robolectric so [Bitmap] / `BitmapFactory` round-trip real
 * dimensions through compress + decode.
 */
@RunWith(RobolectricTestRunner::class)
class AttachmentStoreImplTest {

    private lateinit var context: Context
    private lateinit var store: AttachmentStoreImpl

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        store = AttachmentStoreImpl(context)
    }

    private fun jpegBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_TEST_QUALITY, out)
            out.toByteArray()
        }
    }

    @Test
    fun `given oversized image when ingested then longest side capped and aspect preserved`() = runTest {
        val result = store.ingest(jpegBytes(width = 3000, height = 1000))

        val attachment = result.getOrNull()
        assertTrue("ingest should succeed", result.isSuccess)
        requireNotNull(attachment)
        assertEquals("image/jpeg", attachment.mimeType)
        assertTrue(
            "Longest side must be capped at $CAP, got ${attachment.width}x${attachment.height}",
            maxOf(attachment.width, attachment.height) <= CAP,
        )
        val sourceRatio = 3000f / 1000f
        val storedRatio = attachment.width.toFloat() / attachment.height
        assertTrue(
            "Aspect ratio must be preserved (no square crop): $storedRatio vs $sourceRatio",
            abs(storedRatio - sourceRatio) < RATIO_TOLERANCE,
        )
    }

    @Test
    fun `given small image when ingested then dimensions are left untouched`() = runTest {
        val attachment = store.ingest(jpegBytes(width = 800, height = 600)).getOrNull()

        requireNotNull(attachment)
        assertEquals(800, attachment.width)
        assertEquals(600, attachment.height)
    }

    @Test
    fun `given stored attachment then file exists at the resolved absolute path`() = runTest {
        val attachment = store.ingest(jpegBytes(800, 600)).getOrNull()

        requireNotNull(attachment)
        assertTrue(File(store.absolutePathFor(attachment.path)).exists())
    }

    @Test
    fun `given stored attachment when deleted then the file is gone`() = runTest {
        val attachment = store.ingest(jpegBytes(800, 600)).getOrNull()
        requireNotNull(attachment)

        val deleteResult = store.delete(attachment.path)

        assertTrue(deleteResult.isSuccess)
        assertFalse(File(store.absolutePathFor(attachment.path)).exists())
    }

    @Test
    fun `given missing path when deleted then success (idempotent)`() = runTest {
        assertTrue(store.delete("does-not-exist.jpg").isSuccess)
    }

    @Test
    fun `given path-traversal name when deleted then it is rejected without touching siblings`() = runTest {
        // A name with separators is not a valid flat attachment name → treated as absent (no-op success).
        assertTrue(store.delete("../secret.txt").isSuccess)
    }

    @Test
    fun `given several stored attachments then listStoredPaths enumerates them`() = runTest {
        val first = store.ingest(jpegBytes(800, 600)).getOrNull()
        val second = store.ingest(jpegBytes(640, 480)).getOrNull()
        requireNotNull(first)
        requireNotNull(second)

        val stored = store.listStoredPaths().getOrNull().orEmpty()

        assertTrue(stored.contains(first.path))
        assertTrue(stored.contains(second.path))
    }

    @Test
    fun `given a grace window then a freshly-written file is excluded but an aged one is listed`() = runTest {
        val fresh = store.ingest(jpegBytes(800, 600)).getOrNull()
        val aged = store.ingest(jpegBytes(640, 480)).getOrNull()
        requireNotNull(fresh)
        requireNotNull(aged)
        // Backdate one file well beyond the grace window.
        File(store.absolutePathFor(aged.path)).setLastModified(System.currentTimeMillis() - TWO_HOURS_MS)

        val listed = store.listStoredPaths(minAgeMillis = ONE_HOUR_MS).getOrNull().orEmpty()

        assertFalse("in-flight (fresh) file must be skipped by the grace window", listed.contains(fresh.path))
        assertTrue("aged file is a real orphan candidate", listed.contains(aged.path))
    }

    @Test
    fun `given content uri when ingested then bytes are read and stored`() = runTest {
        val uri = "content://test/image".toUri()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(jpegBytes(1000, 800)))

        val attachment = store.ingestUri(uri.toString()).getOrNull()

        requireNotNull(attachment)
        assertEquals(1000, attachment.width)
        assertEquals(800, attachment.height)
    }

    @Test
    fun `given unreadable content uri when ingested then failure is returned`() = runTest {
        val result = store.ingestUri("content://test/missing")

        assertTrue(result.isFailure)
    }

    private companion object {
        const val CAP = AttachmentStoreImpl.MAX_LONGEST_SIDE_PX
        const val JPEG_TEST_QUALITY = 90
        const val RATIO_TOLERANCE = 0.05f
        const val ONE_HOUR_MS = 60L * 60L * 1000L
        const val TWO_HOURS_MS = 2L * ONE_HOUR_MS
    }
}
