package app.knotwork.android.data.local.crypto

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Pure AES-GCM framing logic shared by the production Keystore cipher and
 * its unit tests.
 *
 * Encapsulates the wire format of an encrypted blob — a 12-byte IV followed
 * by the GCM ciphertext (which itself ends with the 16-byte authentication
 * tag) — and the associated-data binding. The codec is deliberately free of
 * any Android Keystore dependency: it operates on whatever [SecretKey] it is
 * handed, so the full encrypt/decrypt/tamper matrix is testable on the JVM
 * with a software AES key, while [AndroidKeystoreAeadCipher] feeds it
 * hardware-backed keys in production.
 *
 * The IV is always taken from the initialized [Cipher] rather than generated
 * by the caller: Android Keystore keys require randomized encryption and
 * reject caller-supplied IVs, and the JCE provider's IV generation is equally
 * correct on the JVM.
 */
internal object AesGcmCodec {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** GCM authentication-tag length in bits (the maximum, 16 bytes). */
    private const val TAG_LENGTH_BITS = 128

    /** Standard GCM IV length in bytes; both JCE and Keystore generate 12-byte IVs. */
    private const val IV_LENGTH_BYTES = 12

    /**
     * Encrypts [plaintext] under [key], binding [associatedData] into the
     * authentication tag.
     *
     * @param key The AES key to encrypt with.
     * @param associatedData Authenticated context bytes; must be replayed on decryption.
     * @param plaintext The raw bytes to protect.
     * @return `IV (12 bytes) ‖ ciphertext+tag`.
     * @throws GeneralSecurityException When the provider rejects the operation
     *   or produces an IV with an unexpected length.
     */
    fun encrypt(key: SecretKey, associatedData: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        if (iv == null || iv.size != IV_LENGTH_BYTES) {
            throw GeneralSecurityException("Unexpected GCM IV length: ${iv?.size ?: "null"}")
        }
        return iv + ciphertext
    }

    /**
     * Decrypts and authenticates a blob produced by [encrypt].
     *
     * @param key The AES key the blob was encrypted under.
     * @param associatedData The same context bytes passed to [encrypt].
     * @param blob `IV ‖ ciphertext+tag` as returned by [encrypt].
     * @return The original plaintext bytes.
     * @throws GeneralSecurityException When the blob is too short to contain
     *   an IV and a tag, has been tampered with, or the associated data does
     *   not match (surfaces as `AEADBadTagException`).
     */
    fun decrypt(key: SecretKey, associatedData: ByteArray, blob: ByteArray): ByteArray {
        if (blob.size <= IV_LENGTH_BYTES) {
            throw GeneralSecurityException("Encrypted blob too short: ${blob.size} bytes")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, blob, 0, IV_LENGTH_BYTES))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(blob, IV_LENGTH_BYTES, blob.size - IV_LENGTH_BYTES)
    }
}
