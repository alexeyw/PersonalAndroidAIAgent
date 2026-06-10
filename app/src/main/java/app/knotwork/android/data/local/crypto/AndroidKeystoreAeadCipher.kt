package app.knotwork.android.data.local.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [AeadCipher] backed directly by the **Android Keystore**.
 *
 * Each alias maps to a non-exportable AES-256 key generated inside the
 * Keystore (hardware-backed where available) and used in GCM mode through
 * [AesGcmCodec]. This is the project's replacement for the deprecated
 * `androidx.security:security-crypto` stack: instead of a Tink keyset file
 * wrapped by a Keystore master key — an on-disk artifact with its own
 * corruption modes — the data key *is* the Keystore entry, so the only
 * remaining failure mode is the Keystore itself losing the key, which the
 * callers' recovery paths already handle.
 *
 * Keys are created lazily on first [encrypt] and never on [decrypt]: a
 * decryption request for a missing key means the stored blob can no longer
 * be opened, and silently creating a fresh key would only mask that as a
 * tag-verification failure. The miss is surfaced as a
 * [GeneralSecurityException] instead.
 *
 * All Keystore interactions are synchronized on a single lock so that two
 * threads racing on first use cannot both generate a key for the same alias.
 */
@Singleton
class AndroidKeystoreAeadCipher @Inject constructor() : AeadCipher {

    private val lock = Any()

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override fun encrypt(keyAlias: String, associatedData: ByteArray, plaintext: ByteArray): ByteArray =
        AesGcmCodec.encrypt(getOrCreateKey(keyAlias), associatedData, plaintext)

    override fun decrypt(keyAlias: String, associatedData: ByteArray, blob: ByteArray): ByteArray {
        val key = getKeyOrNull(keyAlias)
            ?: throw GeneralSecurityException("No Keystore key under alias '$keyAlias'")
        return AesGcmCodec.decrypt(key, associatedData, blob)
    }

    override fun deleteKey(keyAlias: String): Unit = synchronized(lock) {
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun getKeyOrNull(keyAlias: String): SecretKey? = synchronized(lock) {
        keyStore.getKey(keyAlias, null) as? SecretKey
    }

    private fun getOrCreateKey(keyAlias: String): SecretKey = synchronized(lock) {
        (keyStore.getKey(keyAlias, null) as? SecretKey) ?: generateKey(keyAlias)
    }

    private fun generateKey(keyAlias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        /** Provider name of the system Keystore. */
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** AES key size; 256-bit matches the strength of the replaced master key. */
        const val KEY_SIZE_BITS = 256
    }
}
