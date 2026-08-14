package app.knotwork.android.store

import app.knotwork.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the store metadata under `fastlane/metadata/android/`, which is the
 * single source both Google Play and F-Droid read.
 *
 * Every rule here corresponds to a rejection that otherwise surfaces only at
 * submission time, days after the commit that caused it:
 *
 * - **Length limits.** Play truncates nothing; it refuses the listing.
 * - **Screenshot geometry.** Play rejects an image whose longer side exceeds
 *   twice its shorter side — which is exactly what the 1080 × 2400 README hero
 *   baselines are, so "just reuse the hero shots" is a trap with a delayed
 *   fuse. These assertions are the trip-wire.
 * - **A changelog for the shipping `versionCode`.** F-Droid reads
 *   `changelogs/<versionCode>.txt`; a version bump without it silently ships a
 *   release with no release notes.
 * - **No Cyrillic in the English listing** — the same language rule the rest of
 *   the public surface follows.
 */
class StoreMetadataTest {

    @Test
    fun `given each locale when metadata is read then every required file is present`() {
        LOCALES.forEach { locale ->
            REQUIRED_FILES.forEach { name ->
                assertTrue("$locale is missing $name", localeDir(locale).resolve(name).isFile)
            }
            assertTrue(
                "$locale has no changelog for versionCode ${BuildConfig.VERSION_CODE} — " +
                    "add changelogs/${BuildConfig.VERSION_CODE}.txt",
                localeDir(locale).resolve("changelogs/${BuildConfig.VERSION_CODE}.txt").isFile,
            )
        }
    }

    @Test
    fun `given store texts when measured then they fit the store limits`() {
        LOCALES.forEach { locale ->
            assertWithin(locale, "title.txt", MAX_TITLE_CHARS)
            assertWithin(locale, "short_description.txt", MAX_SHORT_DESCRIPTION_CHARS)
            assertWithin(locale, "full_description.txt", MAX_FULL_DESCRIPTION_CHARS)
            assertWithin(locale, "changelogs/${BuildConfig.VERSION_CODE}.txt", MAX_CHANGELOG_CHARS)
        }
    }

    @Test
    fun `given the english listing when read then it carries no cyrillic`() {
        REQUIRED_FILES.forEach { name ->
            val text = localeDir("en-US").resolve(name).readText()
            val offender = text.firstOrNull { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
            assertTrue("en-US/$name contains Cyrillic ('$offender')", offender == null)
        }
    }

    @Test
    fun `given the phone screenshots when measured then Play accepts their geometry`() {
        val screenshots = localeDir("en-US").resolve("images/phoneScreenshots")
            .listFiles { file -> file.extension.lowercase() in setOf("png", "jpg", "jpeg") }
            ?.sortedBy { it.name }
            .orEmpty()

        assertTrue("Play requires at least two phone screenshots", screenshots.size >= MIN_SCREENSHOTS)
        screenshots.forEach { file ->
            val (width, height) = imageSize(file)
            val shorter = minOf(width, height)
            val longer = maxOf(width, height)
            assertTrue("${file.name} is ${width}x$height — under the 320 px floor", shorter >= MIN_SCREENSHOT_PX)
            assertTrue("${file.name} is ${width}x$height — over the 3840 px ceiling", longer <= MAX_SCREENSHOT_PX)
            assertTrue(
                "${file.name} is ${width}x$height — Play rejects a longer side over twice the shorter one",
                longer <= shorter * 2,
            )
        }
    }

    @Test
    fun `given the listing icon when measured then it is the 512 square Play expects`() {
        val (width, height) = imageSize(localeDir("en-US").resolve("images/icon.png"))

        assertEquals("icon width", ICON_PX, width)
        assertEquals("icon height", ICON_PX, height)
    }

    /**
     * Asserts that a metadata file's trimmed text fits a store limit.
     *
     * @param locale Metadata locale directory.
     * @param name File name relative to that directory.
     * @param limit Maximum number of characters the stores accept.
     */
    private fun assertWithin(locale: String, name: String, limit: Int) {
        val text = localeDir(locale).resolve(name).readText().trim()
        assertTrue("$locale/$name is ${text.length} characters, over the $limit limit", text.length <= limit)
        assertTrue("$locale/$name is empty", text.isNotEmpty())
    }

    /**
     * Reads the pixel dimensions of a PNG or JPEG without decoding it.
     *
     * Hand-rolled because the JVM unit-test classpath has no image decoder that
     * is guaranteed present, and because only the header is needed: PNG carries
     * the size in the fixed-position `IHDR` chunk, JPEG in whichever `SOFn`
     * marker the encoder wrote.
     *
     * @param file The image to measure.
     * @return Width to height, in pixels.
     */
    private fun imageSize(file: File): Pair<Int, Int> {
        assertTrue("${file.name} does not exist", file.isFile)
        val bytes = file.readBytes()
        return if (file.extension.lowercase() == "png") pngSize(bytes) else jpegSize(bytes)
    }

    /**
     * @param bytes Complete PNG file.
     * @return Width to height read from the `IHDR` chunk.
     */
    private fun pngSize(bytes: ByteArray): Pair<Int, Int> =
        readInt(bytes, PNG_WIDTH_OFFSET) to readInt(bytes, PNG_WIDTH_OFFSET + Int.SIZE_BYTES)

    /**
     * @param bytes Complete JPEG file.
     * @return Width to height read from the first start-of-frame marker.
     */
    private fun jpegSize(bytes: ByteArray): Pair<Int, Int> {
        var offset = JPEG_FIRST_MARKER_OFFSET
        while (offset + JPEG_SOF_HEIGHT_OFFSET < bytes.size) {
            val marker = bytes[offset + 1].toInt() and BYTE_MASK
            val segmentLength = readShort(bytes, offset + 2)
            if (marker in JPEG_SOF_MARKERS) {
                val height = readShort(bytes, offset + JPEG_SOF_HEIGHT_OFFSET)
                val width = readShort(bytes, offset + JPEG_SOF_HEIGHT_OFFSET + Short.SIZE_BYTES)
                return width to height
            }
            offset += segmentLength + 2
        }
        error("no start-of-frame marker found — not a JPEG?")
    }

    /**
     * @param bytes Source buffer.
     * @param offset Position of the first byte.
     * @return Big-endian 32-bit integer at [offset].
     */
    private fun readInt(bytes: ByteArray, offset: Int): Int = (0 until Int.SIZE_BYTES).fold(0) { acc, i ->
        (acc shl Byte.SIZE_BITS) or
            (bytes[offset + i].toInt() and BYTE_MASK)
    }

    /**
     * @param bytes Source buffer.
     * @param offset Position of the first byte.
     * @return Big-endian 16-bit integer at [offset].
     */
    private fun readShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or (bytes[offset + 1].toInt() and BYTE_MASK)

    /**
     * Resolves a metadata locale directory, walking up from the module
     * directory to the repository root.
     *
     * @param locale Play/F-Droid locale code, e.g. `en-US`.
     * @return The locale directory (asserted to exist).
     */
    private fun localeDir(locale: String): File {
        val workingDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        var dir: File? = File(workingDir)
        while (dir != null) {
            val candidate = File(dir, "$METADATA_ROOT/$locale")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("$METADATA_ROOT/$locale not found walking up from $workingDir")
    }

    private companion object {
        const val METADATA_ROOT = "fastlane/metadata/android"
        val LOCALES = listOf("en-US", "ru-RU")
        val REQUIRED_FILES = listOf("title.txt", "short_description.txt", "full_description.txt")

        const val MAX_TITLE_CHARS = 30
        const val MAX_SHORT_DESCRIPTION_CHARS = 80
        const val MAX_FULL_DESCRIPTION_CHARS = 4000
        const val MAX_CHANGELOG_CHARS = 500

        const val MIN_SCREENSHOTS = 2
        const val MIN_SCREENSHOT_PX = 320
        const val MAX_SCREENSHOT_PX = 3840
        const val ICON_PX = 512

        /** Byte offset of the width field inside a PNG `IHDR` chunk. */
        const val PNG_WIDTH_OFFSET = 16

        /** Byte offset of the first JPEG marker, past the `SOI`. */
        const val JPEG_FIRST_MARKER_OFFSET = 2

        /** Offset of the height field inside a `SOFn` segment. */
        const val JPEG_SOF_HEIGHT_OFFSET = 5

        /** Baseline / progressive start-of-frame markers (excluding `SOF4`/`SOF8`/`SOF12`). */
        val JPEG_SOF_MARKERS = setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)

        const val BYTE_MASK = 0xFF
    }
}
