package app.knotwork.android.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.knotwork.android.domain.models.DbPassphraseUnavailableException

/**
 * A [SupportSQLiteOpenHelper.Factory] that defers fetching the SQLCipher passphrase until the
 * database is actually opened for the first time.
 *
 * **Why deferral matters.** Room invokes the factory's [create] inside
 * `Room.databaseBuilder(...).build()`, which runs during Hilt provision of the `AppDatabase`
 * singleton — synchronously, on whatever thread first injects a DAO (typically the main thread
 * during ViewModel construction). If the passphrase were fetched there, a
 * [DbPassphraseUnavailableException] would crash the app during dependency injection, before
 * any UI error handling exists. By deferring the fetch to the first `writableDatabase` /
 * `readableDatabase` access, the failure surfaces at the call sites that query the database —
 * `AppInitializationUseCase`'s guarded Room prefetch maps it to the splash recovery screen,
 * and best-effort background maintenance paths skip their work.
 *
 * **Retry semantics.** A failed delegate construction is *not* cached: the next database access
 * re-fetches the passphrase and rebuilds the delegate. Combined with the non-caching failure
 * path inside [EncryptedDbPassphraseProvider], this makes the splash Retry button effective
 * against transient Android Keystore failures.
 *
 * **Wrong-key detection.** SQLCipher reports a key/file mismatch (database restored from a
 * different install, or a corrupted file) as a generic `file is not a database` error at open
 * time. That state is exactly as unrecoverable as a missing passphrase, so [openDatabase]
 * rewraps it as [DbPassphraseUnavailableException] with reason `KEY_MISMATCH`, routing the
 * user to the same recovery surface instead of a retry-forever generic error.
 *
 * **Wipe coordination.** All database opens run under a single factory-wide [globalLock], and
 * [runExclusive] takes the same lock after closing every live delegate. The user-confirmed
 * data wipe runs inside [runExclusive], so no concurrent open can interleave between "database
 * file deleted" and "stored passphrase deleted" — an interleaving that would create a fresh
 * database keyed by a passphrase the wipe then destroys.
 *
 * @property passphraseProvider Source of the SQLCipher passphrase, queried lazily.
 * @property delegateFactoryProvider Builds the real (SQLCipher) factory from a passphrase.
 *   Injected as a function so unit tests can substitute a fake without loading native code.
 */
class DeferredPassphraseOpenHelperFactory(
    private val passphraseProvider: EncryptedDbPassphraseProvider,
    private val delegateFactoryProvider: (ByteArray) -> SupportSQLiteOpenHelper.Factory,
) : SupportSQLiteOpenHelper.Factory {

    private val globalLock = Any()
    private val helpers = mutableListOf<DeferredPassphraseOpenHelper>()

    /**
     * Returns a lazily-initializing wrapper helper for [configuration]. No passphrase access
     * happens here — this method must stay safe to call during dependency injection.
     */
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        synchronized(globalLock) {
            DeferredPassphraseOpenHelper(configuration).also { helpers.add(it) }
        }

    /**
     * Runs [action] while no database open can proceed: takes the factory-wide lock and closes
     * every live delegate first, so [action] observes a quiesced database and the next access
     * after it returns performs a full fresh open (re-fetching the passphrase).
     *
     * Used by the user-confirmed data wipe to make "delete database file + delete passphrase"
     * atomic with respect to concurrent Room consumers (background workers, watch flows).
     *
     * @param action The critical section to execute.
     * @return Whatever [action] returns.
     */
    fun <T> runExclusive(action: () -> T): T = synchronized(globalLock) {
        helpers.forEach { it.closeDelegateLocked() }
        action()
    }

    /**
     * Delegating [SupportSQLiteOpenHelper] that constructs the real SQLCipher-backed helper on
     * first database access and replays any buffered [setWriteAheadLoggingEnabled] call onto it.
     * All entry points synchronize on the factory-wide [globalLock].
     */
    private inner class DeferredPassphraseOpenHelper(
        private val configuration: SupportSQLiteOpenHelper.Configuration,
    ) : SupportSQLiteOpenHelper {

        private var delegate: SupportSQLiteOpenHelper? = null
        private var writeAheadLoggingEnabled: Boolean? = null

        override val databaseName: String?
            get() = configuration.name

        override val writableDatabase: SupportSQLiteDatabase
            get() = synchronized(globalLock) { openDatabase { it.writableDatabase } }

        override val readableDatabase: SupportSQLiteDatabase
            get() = synchronized(globalLock) { openDatabase { it.readableDatabase } }

        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            synchronized(globalLock) {
                writeAheadLoggingEnabled = enabled
                delegate?.setWriteAheadLoggingEnabled(enabled)
            }
        }

        override fun close() {
            synchronized(globalLock) { closeDelegateLocked() }
        }

        /** Closes and drops the delegate. Must be called under [globalLock]. */
        fun closeDelegateLocked() {
            delegate?.close()
            delegate = null
        }

        /**
         * Opens the database through the (lazily constructed) delegate, translating SQLCipher's
         * wrong-key signature into the typed recovery exception. Must be called under
         * [globalLock] so the open cannot interleave with [runExclusive]'s critical section.
         */
        private fun openDatabase(open: (SupportSQLiteOpenHelper) -> SupportSQLiteDatabase): SupportSQLiteDatabase {
            val helper = requireDelegate()
            return try {
                open(helper)
            } catch (e: RuntimeException) {
                if (e.message?.contains(WRONG_KEY_SIGNATURE, ignoreCase = true) == true) {
                    throw DbPassphraseUnavailableException(
                        reason = DbPassphraseUnavailableException.Reason.KEY_MISMATCH,
                        cause = e,
                    )
                }
                throw e
            }
        }

        /**
         * Returns the real helper, constructing it on first use. Construction failures are not
         * cached — see the class KDoc for why that is load-bearing for the Retry flow.
         */
        private fun requireDelegate(): SupportSQLiteOpenHelper = delegate ?: run {
            val passphrase = passphraseProvider.getOrCreatePassphrase()
            val created = delegateFactoryProvider(passphrase).create(configuration)
            writeAheadLoggingEnabled?.let { created.setWriteAheadLoggingEnabled(it) }
            delegate = created
            created
        }
    }

    private companion object {
        /**
         * Error-message fragment SQLCipher produces when the key does not match the database
         * file (or the file is not an SQLCipher database at all). There is no typed exception
         * for this condition in sqlcipher-android, so message matching is the only available
         * discriminator.
         */
        const val WRONG_KEY_SIGNATURE = "file is not a database"
    }
}
