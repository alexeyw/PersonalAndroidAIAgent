package app.knotwork.android.data.local

import android.content.Context
import app.knotwork.android.domain.services.DatabaseResetService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [DatabaseResetService] implementation: deletes the encrypted Room database file and
 * the persisted SQLCipher passphrase in one operation.
 *
 * The two deletions must happen together — removing only the passphrase would trip the
 * loss-protection invariant in [EncryptedDbPassphraseProvider.getOrCreatePassphrase] (database
 * present, passphrase gone), while removing only the database would leave a stale passphrase
 * that is harmless but misleading. `Context.deleteDatabase` also removes the `-wal` / `-shm`
 * journal companions.
 *
 * @property context Application context owning the database and preferences files.
 * @property passphraseProvider Owner of the passphrase store being reset.
 */
@Singleton
class DatabaseResetServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val passphraseProvider: EncryptedDbPassphraseProvider,
) : DatabaseResetService {

    /**
     * Deletes the database file (with journals) and the stored passphrase on [Dispatchers.IO].
     * Idempotent: deleting an already-absent file is a no-op.
     */
    override suspend fun wipeAllData() = withContext(Dispatchers.IO) {
        val deleted = context.deleteDatabase(AppDatabase.DATABASE_NAME)
        passphraseProvider.resetStoredPassphrase()
        Timber.w("User-confirmed data wipe executed (database deleted: $deleted).")
    }
}
