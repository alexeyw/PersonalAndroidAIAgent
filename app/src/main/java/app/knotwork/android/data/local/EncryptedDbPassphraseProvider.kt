package app.knotwork.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.knotwork.android.domain.models.DbPassphraseUnavailableException
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
 * **Loss-protection invariant.** A new passphrase is generated **only** when no database file
 * exists yet (fresh install or post-wipe). Once the encrypted database has been created, the
 * stored passphrase is the only key that can ever open it — so any failure to read it back
 * (preferences fail to open, entry missing, entry malformed) throws
 * [DbPassphraseUnavailableException] instead of regenerating. Android Keystore failures are
 * frequently transient (backup/restore, OS update, TEE hiccup); throwing keeps the encrypted
 * database intact so a later retry can still open it, whereas silent regeneration would make
 * it permanently unreadable and destroy all user data.
 *
 * This is deliberately the **opposite** recovery semantics of [ApiKeyManager]: API keys can be
 * re-entered by the user at any time, so that store recreates itself on corruption. The
 * database passphrase cannot be re-derived from anything, so this provider never does.
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

    // Kotlin's SYNCHRONIZED lazy does not cache a failed initializer, so a transient
    // Keystore error here is naturally retryable on the next access — which is exactly
    // what the splash recovery screen's Retry button relies on.
    private val sharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedSharedPreferences()
        } catch (e: Exception) {
            Timber.e(e, "Failed to open EncryptedSharedPreferences for DB passphrase.")
            throw DbPassphraseUnavailableException(
                reason = DbPassphraseUnavailableException.Reason.PREFS_OPEN_FAILED,
                cause = e,
            )
        }
    }

    /**
     * Returns the persisted database passphrase, generating and storing a new one **only**
     * when no encrypted database file exists yet.
     *
     * The returned [ByteArray] is a freshly allocated copy: the caller may hand it to
     * SQLCipher's factory (which will zero its own copy) without affecting the stored value.
     *
     * @return A 32-byte random passphrase.
     * @throws DbPassphraseUnavailableException When the database file already exists but the
     *   stored passphrase cannot be read back (see class KDoc for the invariant rationale).
     */
    fun getOrCreatePassphrase(): ByteArray {
        val existingHex = sharedPreferences.getString(passphraseKey, null)
        if (existingHex != null) {
            val decoded = decodeHexOrNull(existingHex)
            if (decoded != null && decoded.size == PASSPHRASE_BYTE_LENGTH) {
                return decoded
            }
        }

        val databaseExists = context.getDatabasePath(AppDatabase.DATABASE_NAME).exists()
        if (databaseExists) {
            // The encrypted DB is on disk but its key is gone or unreadable. Regenerating
            // would orphan the database forever — surface the condition instead.
            val reason = if (existingHex == null) {
                DbPassphraseUnavailableException.Reason.PASSPHRASE_MISSING
            } else {
                DbPassphraseUnavailableException.Reason.PASSPHRASE_MALFORMED
            }
            Timber.e("DB passphrase unavailable ($reason) while database file exists; refusing to regenerate.")
            throw DbPassphraseUnavailableException(reason)
        }

        if (existingHex != null) {
            Timber.w("Stored DB passphrase is malformed but no database exists yet; regenerating.")
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

    /**
     * Deletes the persisted passphrase store. Called **only** from the explicit user-confirmed
     * full data wipe ([app.knotwork.android.domain.services.DatabaseResetService]) — the
     * database file must be deleted in the same operation, otherwise the loss-protection
     * invariant in [getOrCreatePassphrase] will refuse the next generation attempt.
     */
    fun resetStoredPassphrase() {
        deleteSharedPreferences(prefsName)
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
            Timber.w(e, "deleteSharedPreferences failed; falling back to direct file removal.")
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
