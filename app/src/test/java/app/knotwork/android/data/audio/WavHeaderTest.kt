package app.knotwork.android.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for [WavHeader], the pure 44-byte canonical PCM WAV header builder.
 */
class WavHeaderTest {

    @Test
    fun `header is 44 bytes long`() {
        assertEquals(WavHeader.HEADER_BYTES, WavHeader.build(0).size)
    }

    @Test
    fun `header carries the RIFF WAVE and data chunk ids`() {
        val header = WavHeader.build(1_000)
        assertEquals("RIFF", String(header.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(header.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(header.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals("data", String(header.copyOfRange(36, 40), Charsets.US_ASCII))
    }

    @Test
    fun `format fields describe 16kHz mono 16-bit PCM`() {
        val buffer = ByteBuffer.wrap(WavHeader.build(0)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, buffer.getShort(20).toInt()) // audio format = PCM
        assertEquals(1, buffer.getShort(22).toInt()) // channels = mono
        assertEquals(16_000, buffer.getInt(24)) // sample rate
        assertEquals(16_000 * 2, buffer.getInt(28)) // byte rate = rate * blockAlign
        assertEquals(2, buffer.getShort(32).toInt()) // block align = 2 bytes
        assertEquals(16, buffer.getShort(34).toInt()) // bits per sample
    }

    @Test
    fun `riff size and data size scale with the pcm byte count`() {
        val pcmBytes = 32_000
        val buffer = ByteBuffer.wrap(WavHeader.build(pcmBytes)).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk size = header (minus the 8-byte RIFF id+size) + PCM bytes.
        assertEquals(WavHeader.HEADER_BYTES - 8 + pcmBytes, buffer.getInt(4))
        // data chunk size = PCM bytes exactly.
        assertEquals(pcmBytes, buffer.getInt(40))
    }
}
