package app.knotwork.android.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure builder for a 44-byte canonical PCM WAV (RIFF) header. Split out from the
 * Android-coupled recorder so the byte layout is unit-testable on the JVM.
 */
object WavHeader {

    /** Canonical capture sample rate expected by the multimodal audio frontend. */
    const val SAMPLE_RATE_HZ = 16_000

    /** Single (mono) channel. */
    const val CHANNELS = 1

    /** 16-bit signed PCM samples. */
    const val BITS_PER_SAMPLE = 16

    /** Size of the fixed RIFF/WAVE header that precedes the PCM data. */
    const val HEADER_BYTES = 44

    private const val BITS_PER_BYTE = 8
    private const val PCM_FORMAT = 1
    private const val FMT_CHUNK_SIZE = 16

    /**
     * Builds the 44-byte WAV header for [pcmDataBytes] of 16 kHz mono 16-bit PCM
     * audio.
     *
     * @param pcmDataBytes number of raw PCM bytes that will follow the header.
     * @return the little-endian RIFF/WAVE header.
     */
    fun build(pcmDataBytes: Int): ByteArray {
        val byteRate = SAMPLE_RATE_HZ * CHANNELS * BITS_PER_SAMPLE / BITS_PER_BYTE
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / BITS_PER_BYTE
        val buffer = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(HEADER_BYTES - CHUNK_ID_AND_SIZE_BYTES + pcmDataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(FMT_CHUNK_SIZE)
        buffer.putShort(PCM_FORMAT.toShort())
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE_HZ)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmDataBytes)
        return buffer.array()
    }

    /** The 8 bytes ("RIFF" + the 4-byte size field) excluded from the RIFF chunk size. */
    private const val CHUNK_ID_AND_SIZE_BYTES = 8
}
