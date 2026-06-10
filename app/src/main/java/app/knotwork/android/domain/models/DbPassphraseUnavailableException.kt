package app.knotwork.android.domain.models

/**
 * Signals that the SQLCipher database passphrase cannot be obtained while an
 * encrypted database file already exists on disk.
 *
 * This is a deliberately non-recoverable condition: regenerating the
 * passphrase would render the existing database permanently unreadable, so
 * the provider throws instead of silently recreating its secret store. The
 * UI layer maps this exception to a dedicated recovery screen offering a
 * retry (Android Keystore failures are often transient — e.g. right after a
 * backup/restore or an OS update) and an explicit, user-confirmed full data
 * reset as the last resort.
 *
 * @property reason The specific failure mode that prevented passphrase retrieval.
 * @param cause The underlying throwable, when the failure originated from a
 *   lower-level API (e.g. `EncryptedSharedPreferences` failing to open).
 */
class DbPassphraseUnavailableException(val reason: Reason, cause: Throwable? = null) :
    Exception("Database passphrase unavailable: $reason", cause) {

    /**
     * Enumerates the distinct ways the stored passphrase can become
     * unavailable while the encrypted database file still exists.
     */
    enum class Reason {
        /** The encrypted preferences file backing the passphrase could not be opened. */
        PREFS_OPEN_FAILED,

        /** The preferences opened fine but contain no passphrase entry. */
        PASSPHRASE_MISSING,

        /** A passphrase entry exists but cannot be decoded into a valid key. */
        PASSPHRASE_MALFORMED,

        /**
         * The passphrase was read back fine but SQLCipher rejected it for the existing
         * database file ("file is not a database") — the file was restored from a different
         * install or corrupted. Functionally identical to a lost passphrase: the data cannot
         * be decrypted with what this device knows.
         */
        KEY_MISMATCH,
    }
}
