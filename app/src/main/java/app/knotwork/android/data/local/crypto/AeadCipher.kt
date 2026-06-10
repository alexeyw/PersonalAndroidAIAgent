package app.knotwork.android.data.local.crypto

/**
 * Authenticated encryption boundary for small secret values.
 *
 * Implementations encrypt and decrypt byte payloads with AEAD (authenticated
 * encryption with associated data) under a key identified by [String] alias.
 * The interface exists so that storage classes ([KeystoreBackedPrefsStore]
 * and its consumers) can be unit-tested on the JVM with a fake cipher, while
 * the production implementation ([AndroidKeystoreAeadCipher]) anchors keys in
 * the hardware-backed Android Keystore, which is unavailable in local tests.
 *
 * Contract notes:
 *  - [encrypt] and [decrypt] are inverse operations only when called with the
 *    same alias **and** the same associated data; callers bind the associated
 *    data to the logical storage slot so that two ciphertexts can never be
 *    swapped between slots without detection.
 *  - [decrypt] must authenticate before returning: any tampering with the
 *    blob or a mismatched associated-data value fails with an exception
 *    instead of returning garbage plaintext.
 */
interface AeadCipher {

    /**
     * Encrypts [plaintext] under the key identified by [keyAlias], creating
     * the key if it does not exist yet.
     *
     * @param keyAlias Stable identifier of the encryption key.
     * @param associatedData Authenticated-but-not-encrypted context bytes;
     *   the same value must be supplied to [decrypt].
     * @param plaintext The raw secret bytes to protect.
     * @return An opaque blob containing everything needed for decryption
     *   except the key (for AES-GCM: IV followed by ciphertext + tag).
     * @throws java.security.GeneralSecurityException When the key cannot be
     *   created or the encryption operation fails.
     */
    fun encrypt(keyAlias: String, associatedData: ByteArray, plaintext: ByteArray): ByteArray

    /**
     * Decrypts and authenticates a blob produced by [encrypt].
     *
     * @param keyAlias Alias of the key the blob was encrypted under.
     * @param associatedData The same context bytes passed to [encrypt].
     * @param blob The opaque blob returned by [encrypt].
     * @return The original plaintext bytes.
     * @throws java.security.GeneralSecurityException When the key is missing,
     *   the blob is malformed or tampered with, or the associated data does
     *   not match.
     */
    fun decrypt(keyAlias: String, associatedData: ByteArray, blob: ByteArray): ByteArray

    /**
     * Deletes the key identified by [keyAlias], if present. Blobs encrypted
     * under that alias become permanently undecryptable — callers must delete
     * the corresponding stored values in the same logical operation.
     *
     * @param keyAlias Alias of the key to remove.
     */
    fun deleteKey(keyAlias: String)
}
