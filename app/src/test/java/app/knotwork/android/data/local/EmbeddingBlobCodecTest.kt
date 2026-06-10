package app.knotwork.android.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for [EmbeddingBlobCodec] — the binary wire format of the
 * `memory_chunks.embedding` BLOB column.
 *
 * The encode/decode round trip must be **bit-exact**: a stored vector that
 * silently changed even one bit would shift cosine-similarity scores and make
 * retrieval results drift across an export/import or app upgrade.
 */
class EmbeddingBlobCodecTest {

    @Test
    fun `given ordinary vector when round-tripped then bit-exact`() {
        val original = floatArrayOf(0.1f, -0.2f, 3.5f, 1e-30f, 1e30f)

        val decoded = EmbeddingBlobCodec.decode(EmbeddingBlobCodec.encode(original))

        assertBitExact(original, decoded!!)
    }

    @Test
    fun `given special values when round-tripped then bit-exact`() {
        // -0f, infinities, NaN, min/max magnitudes and a subnormal — every one
        // must survive with its raw bit pattern intact.
        val original = floatArrayOf(
            -0.0f,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NaN,
            Float.MIN_VALUE,
            Float.MAX_VALUE,
            java.lang.Float.intBitsToFloat(0x00000001),
        )

        val decoded = EmbeddingBlobCodec.decode(EmbeddingBlobCodec.encode(original))

        assertBitExact(original, decoded!!)
    }

    @Test
    fun `given single component when encoded then four little-endian bytes`() {
        val bytes = EmbeddingBlobCodec.encode(floatArrayOf(1.0f))

        // 1.0f is 0x3F800000; little-endian on disk → 00 00 80 3F.
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F), bytes)
    }

    @Test
    fun `given empty array when encoded then empty blob`() {
        assertEquals(0, EmbeddingBlobCodec.encode(FloatArray(0)).size)
    }

    @Test
    fun `given empty blob when decoded then null`() {
        assertNull(EmbeddingBlobCodec.decode(ByteArray(0)))
    }

    @Test
    fun `given blob length not multiple of four when decoded then null`() {
        assertNull(EmbeddingBlobCodec.decode(ByteArray(5)))
        assertNull(EmbeddingBlobCodec.decode(ByteArray(3)))
    }

    @Test
    fun `given externally built little-endian blob when decoded then matches source floats`() {
        // Cross-check against an independently constructed buffer so the test
        // would catch an accidental endianness flip inside the codec.
        val source = floatArrayOf(42.5f, -7.25f)
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        source.forEach(buffer::putFloat)

        val decoded = EmbeddingBlobCodec.decode(buffer.array())

        assertBitExact(source, decoded!!)
    }

    /** Compares two vectors by raw IEEE-754 bits, so NaN payloads count too. */
    private fun assertBitExact(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (index in expected.indices) {
            assertEquals(
                "Component $index differs",
                expected[index].toRawBits(),
                actual[index].toRawBits(),
            )
        }
    }
}
