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
 * Two safeguards keep the wipe from manufacturing the very state it exists to escape:
 * - The whole operation runs inside [DeferredPassphraseOpenHelperFactory.runExclusive], so a
 *   concurrent Room consumer (background worker, watch flow) cannot open — and thereby
 *   recreate — the database between the two deletions.
 * - The passphrase is deleted **only after** the database file is verifiably gone. If the
 *   file deletion fails, the stored passphrase is left untouched (it may still be the valid
 *   key for the surviving file) and the wipe aborts with [DatabaseWipeFailedException].
 *
 * @property context Application context owning the database and preferences files.
 * @property passphraseProvider Owner of the passphrase store being reset.
 * @property openHelperFactory The deferred open-helper factory the wipe must quiesce.
 */
@Singleton
class DatabaseResetServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val passphraseProvider: EncryptedDbPassphraseProvider,
    private val openHelperFactory: DeferredPassphraseOpenHelperFactory,
) : DatabaseResetService {

    /**
     * Deletes the database file (with journals) and the stored passphrase on [Dispatchers.IO],
     * serialized against every concurrent database open. Idempotent: deleting an
     * already-absent file is a no-op.
     *
     * @throws DatabaseWipeFailedException When the database file survives the deletion
     *   attempt; the passphrase is intentionally left in place in that case.
     */
    override suspend fun wipeAllData(): Unit = withContext(Dispatchers.IO) {
        openHelperFactory.runExclusive {
            context.deleteDatabase(AppDatabase.DATABASE_NAME)
            if (context.getDatabasePath(AppDatabase.DATABASE_NAME).exists()) {
                // Deleting the passphrase now would turn a still-present (possibly openable)
                // database into a guaranteed-unreadable one. Keep the key and abort.
                Timber.e("Data wipe aborted: database file survived deletion; passphrase left intact.")
                throw DatabaseWipeFailedException()
            }
            passphraseProvider.resetStoredPassphrase()
            Timber.w("User-confirmed data wipe executed.")
        }
    }
}

/**
 * Signals that the user-confirmed data wipe could not delete the database file. The stored
 * passphrase is deliberately preserved in this case so a later retry (or wipe re-attempt)
 * still has the original key available.
 */
class DatabaseWipeFailedException : Exception("Database file could not be deleted; wipe aborted.")
