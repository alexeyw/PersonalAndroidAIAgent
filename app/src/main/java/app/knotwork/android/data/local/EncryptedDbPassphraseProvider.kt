package app.knotwork.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a stable passphrase used to encrypt the application's Room database via SQLCipher.
 *
 * The passphrase is a random 32-byte value generated on first access and persisted inside
 * [EncryptedSharedPreferences] (backed by the Android Keystore through [MasterKey]). All
 * subsequent calls return the same value, so the database can be reopened across process
 * restarts without user interaction.
 *
 * Byte arrays are intentionally used (instead of strings) because SQLCipher's
 * `SupportOpenHelperFactory` consumes a mutable byte array that it may zero after use.
 *
 * @property context The application context used to back the underlying preferences file.
 */
@Singleton
class EncryptedDbPassphraseProvider @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefsName = "secure_db_passphrase"
    private val passphraseKey = "db_passphrase_hex"

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedSharedPreferences()
        } catch (e: Exception) {
            Timber.e(e, "Failed to open EncryptedSharedPreferences for DB passphrase. Recreating store.")
            deleteSharedPreferences(prefsName)
            createEncryptedSharedPreferences()
        }
    }

    /**
     * Returns the persisted database passphrase, generating and storing a new one if necessary.
     *
     * The returned [ByteArray] is a freshly allocated copy: the caller may hand it to
     * SQLCipher's factory (which will zero its own copy) without affecting the stored value.
     *
     * @return A 32-byte random passphrase.
     */
    fun getOrCreatePassphrase(): ByteArray {
        val existingHex = sharedPreferences.getString(passphraseKey, null)
        if (existingHex != null) {
            val decoded = decodeHexOrNull(existingHex)
            if (decoded != null && decoded.size == PASSPHRASE_BYTE_LENGTH) {
                return decoded
            }
            Timber.w("Stored DB passphrase is malformed; regenerating.")
        }
        val generated = ByteArray(PASSPHRASE_BYTE_LENGTH).also { SecureRandom().nextBytes(it) }
        // commit = true forces synchronous fsync: a freshly generated passphrase must hit disk
        // before it is ever used to open the DB, otherwise a process crash between creating the
        // encrypted DB and flushing the prefs would leave the DB permanently unreadable.
        sharedPreferences.edit(commit = true) {
            putString(passphraseKey, encodeHex(generated))
        }
        return generated.copyOf()
    }

    private fun createEncryptedSharedPreferences(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        prefsName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun deleteSharedPreferences(name: String) {
        try {
            context.deleteSharedPreferences(name)
        } catch (e: Exception) {
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            val file = File(dir, "$name.xml")
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun encodeHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and BYTE_MASK))
        }
        return sb.toString()
    }

    private fun decodeHexOrNull(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            val hi = Character.digit(hex[i], HEX_RADIX)
            val lo = Character.digit(hex[i + 1], HEX_RADIX)
            if (hi < 0 || lo < 0) return null
            out[i / 2] = ((hi shl BITS_PER_NIBBLE) or lo).toByte()
            i += 2
        }
        return out
    }

    private companion object {
        /** Length, in bytes, of the random SQLCipher passphrase. */
        const val PASSPHRASE_BYTE_LENGTH: Int = 32

        /** Bit mask used to widen a signed `Byte` to an unsigned 0..255 `Int`. */
        const val BYTE_MASK: Int = 0xff

        /** Radix passed to [Character.digit] when decoding hexadecimal characters. */
        const val HEX_RADIX: Int = 16

        /** Number of bits in one hexadecimal digit; used when packing two nibbles into a byte. */
        const val BITS_PER_NIBBLE: Int = 4
    }
}
