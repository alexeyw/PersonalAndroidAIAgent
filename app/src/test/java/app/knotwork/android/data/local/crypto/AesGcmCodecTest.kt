package app.knotwork.android.data.local.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Exercises the full AES-GCM framing matrix of [AesGcmCodec] with a software
 * AES key — the same code path production uses with Android-Keystore-backed
 * keys, minus the key residency.
 */
class AesGcmCodecTest {

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val otherKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val aad = "store/entry".toByteArray()

    @Test
    fun `given encrypted blob when decrypted with same key and aad then round-trips`() {
        val plaintext = "secret value éè".toByteArray()

        val blob = AesGcmCodec.encrypt(key, aad, plaintext)
        val decrypted = AesGcmCodec.decrypt(key, aad, blob)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `given same plaintext when encrypted twice then blobs differ`() {
        val plaintext = "same input".toByteArray()

        val first = AesGcmCodec.encrypt(key, aad, plaintext)
        val second = AesGcmCodec.encrypt(key, aad, plaintext)

        // A repeated IV would be a catastrophic GCM failure; distinct blobs
        // demonstrate per-call randomized IVs.
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `given mismatched associated data when decrypted then fails authentication`() {
        val blob = AesGcmCodec.encrypt(key, aad, "value".toByteArray())

        assertThrows(GeneralSecurityException::class.java) {
            AesGcmCodec.decrypt(key, "store/other-entry".toByteArray(), blob)
        }
    }

    @Test
    fun `given tampered ciphertext when decrypted then fails authentication`() {
        val blob = AesGcmCodec.encrypt(key, aad, "value".toByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            AesGcmCodec.decrypt(key, aad, blob)
        }
    }

    @Test
    fun `given wrong key when decrypted then fails authentication`() {
        val blob = AesGcmCodec.encrypt(key, aad, "value".toByteArray())

        assertThrows(GeneralSecurityException::class.java) {
            AesGcmCodec.decrypt(otherKey, aad, blob)
        }
    }

    @Test
    fun `given blob shorter than an IV when decrypted then fails with a clear error`() {
        val thrown = assertThrows(GeneralSecurityException::class.java) {
            AesGcmCodec.decrypt(key, aad, ByteArray(5))
        }

        assertEquals("Encrypted blob too short: 5 bytes", thrown.message)
    }

    @Test
    fun `given empty plaintext when round-tripped then yields empty plaintext`() {
        val blob = AesGcmCodec.encrypt(key, aad, ByteArray(0))

        assertArrayEquals(ByteArray(0), AesGcmCodec.decrypt(key, aad, blob))
    }
}
