package app.knotwork.android.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * A [SupportSQLiteOpenHelper.Factory] that defers fetching the SQLCipher passphrase until the
 * database is actually opened for the first time.
 *
 * **Why deferral matters.** Room invokes the factory's [create] inside
 * `Room.databaseBuilder(...).build()`, which runs during Hilt provision of the `AppDatabase`
 * singleton — synchronously, on whatever thread first injects a DAO (typically the main thread
 * during ViewModel construction). If the passphrase were fetched there, a
 * [app.knotwork.android.domain.models.DbPassphraseUnavailableException] would crash the app
 * during dependency injection, before any UI error handling exists. By deferring the fetch to
 * the first `writableDatabase` / `readableDatabase` access, the failure surfaces inside
 * `AppInitializationUseCase`'s guarded Room prefetch instead, where it is mapped to the splash
 * recovery screen.
 *
 * **Retry semantics.** A failed delegate construction is *not* cached: the next database access
 * re-fetches the passphrase and rebuilds the delegate. Combined with the non-caching `lazy`
 * inside [EncryptedDbPassphraseProvider], this makes the splash Retry button effective against
 * transient Android Keystore failures.
 *
 * @property passphraseProvider Source of the SQLCipher passphrase, queried lazily.
 * @property delegateFactoryProvider Builds the real (SQLCipher) factory from a passphrase.
 *   Injected as a function so unit tests can substitute a fake without loading native code.
 */
class DeferredPassphraseOpenHelperFactory(
    private val passphraseProvider: EncryptedDbPassphraseProvider,
    private val delegateFactoryProvider: (ByteArray) -> SupportSQLiteOpenHelper.Factory,
) : SupportSQLiteOpenHelper.Factory {

    /**
     * Returns a lazily-initializing wrapper helper for [configuration]. No passphrase access
     * happens here — this method must stay safe to call during dependency injection.
     */
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        DeferredPassphraseOpenHelper(configuration)

    /**
     * Delegating [SupportSQLiteOpenHelper] that constructs the real SQLCipher-backed helper on
     * first database access and replays any buffered [setWriteAheadLoggingEnabled] call onto it.
     */
    private inner class DeferredPassphraseOpenHelper(
        private val configuration: SupportSQLiteOpenHelper.Configuration,
    ) : SupportSQLiteOpenHelper {

        private val lock = Any()
        private var delegate: SupportSQLiteOpenHelper? = null
        private var writeAheadLoggingEnabled: Boolean? = null

        override val databaseName: String?
            get() = configuration.name

        override val writableDatabase: SupportSQLiteDatabase
            get() = requireDelegate().writableDatabase

        override val readableDatabase: SupportSQLiteDatabase
            get() = requireDelegate().readableDatabase

        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            synchronized(lock) {
                writeAheadLoggingEnabled = enabled
                delegate?.setWriteAheadLoggingEnabled(enabled)
            }
        }

        override fun close() {
            synchronized(lock) {
                delegate?.close()
                delegate = null
            }
        }

        /**
         * Returns the real helper, constructing it on first use. Construction failures are not
         * cached — see the class KDoc for why that is load-bearing for the Retry flow.
         */
        private fun requireDelegate(): SupportSQLiteOpenHelper = synchronized(lock) {
            delegate ?: run {
                val passphrase = passphraseProvider.getOrCreatePassphrase()
                val created = delegateFactoryProvider(passphrase).create(configuration)
                writeAheadLoggingEnabled?.let { created.setWriteAheadLoggingEnabled(it) }
                delegate = created
                created
            }
        }
    }
}
