package app.knotwork.android.data.local.crypto

import java.security.GeneralSecurityException

/**
 * Deterministic [AeadCipher] stand-in for JVM unit tests.
 *
 * "Encrypts" by prefixing the plaintext with a header derived from the key
 * alias and the associated data, and "decrypts" by verifying that header —
 * which faithfully reproduces the production contract that a blob can only
 * be opened with the same alias and associated data it was sealed with,
 * without requiring the Android Keystore.
 */
class FakeAeadCipher : AeadCipher {

    /** When `true`, every [decrypt] call fails — simulates a lost Keystore key. */
    var failDecrypt: Boolean = false

    /** Aliases passed to [deleteKey], in call order. */
    val deletedAliases: MutableList<String> = mutableListOf()

    override fun encrypt(keyAlias: String, associatedData: ByteArray, plaintext: ByteArray): ByteArray =
        header(keyAlias, associatedData) + plaintext

    override fun decrypt(keyAlias: String, associatedData: ByteArray, blob: ByteArray): ByteArray {
        if (failDecrypt) {
            throw GeneralSecurityException("Simulated Keystore failure")
        }
        val expected = header(keyAlias, associatedData)
        if (blob.size < expected.size || !blob.copyOfRange(0, expected.size).contentEquals(expected)) {
            throw GeneralSecurityException("Simulated authentication failure")
        }
        return blob.copyOfRange(expected.size, blob.size)
    }

    override fun deleteKey(keyAlias: String) {
        deletedAliases += keyAlias
    }

    private fun header(keyAlias: String, associatedData: ByteArray): ByteArray =
        "$keyAlias|${associatedData.decodeToString()}|".toByteArray(Charsets.UTF_8)
}
