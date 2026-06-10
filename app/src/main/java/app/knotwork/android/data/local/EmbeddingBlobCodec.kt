package app.knotwork.android.data.local

import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary codec for vector embeddings stored in the `memory_chunks.embedding`
 * BLOB column.
 *
 * ### Wire format
 *
 * A flat sequence of IEEE-754 32-bit floats in **little-endian** byte order,
 * 4 bytes per component, with no header or length prefix — the vector
 * dimensionality is derived from the blob length (`bytes / 4`). An empty
 * blob is the canonical "no usable embedding" marker: the TEXT → BLOB
 * migration writes it for legacy rows whose comma-separated string could
 * not be parsed, preserving the row (and its repairable `text`) instead of
 * deleting user data.
 *
 * Shared by [Converters] (the Room entity boundary) and the TEXT → BLOB
 * migration in [AppDatabase], so the on-disk encoding can never drift
 * between the two.
 */
internal object EmbeddingBlobCodec {

    /** Size of one encoded vector component in bytes (an IEEE-754 float). */
    private const val BYTES_PER_FLOAT: Int = java.lang.Float.BYTES

    /**
     * Encodes [array] into the little-endian binary form.
     *
     * Every component is written as its raw IEEE-754 bit pattern, so
     * non-finite values (NaN, ±Infinity) and signed zero survive the round
     * trip bit-exactly.
     *
     * @param array The embedding vector to encode.
     * @return The encoded bytes; an empty array yields an empty blob.
     */
    fun encode(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * BYTES_PER_FLOAT).order(ByteOrder.LITTLE_ENDIAN)
        for (value in array) buffer.putFloat(value)
        return buffer.array()
    }

    /**
     * Decodes [bytes] produced by [encode] back into a [FloatArray].
     *
     * Total by design — it never throws. Two inputs decode to `null` so a
     * single corrupt row is skipped by retrieval rather than aborting a
     * whole memory load:
     * - an empty blob (the migration's marker for an unparseable legacy
     *   embedding); and
     * - a blob whose length is not a multiple of 4 (cannot be a valid float
     *   sequence; logged as a warning).
     *
     * @param bytes The stored blob.
     * @return The decoded vector, or `null` when the blob carries no usable
     *   embedding.
     */
    fun decode(bytes: ByteArray): FloatArray? {
        if (bytes.isEmpty()) return null
        if (bytes.size % BYTES_PER_FLOAT != 0) {
            Timber.w("Corrupt embedding blob: %d bytes is not a multiple of %d", bytes.size, BYTES_PER_FLOAT)
            return null
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / BYTES_PER_FLOAT) { buffer.float }
    }
}
