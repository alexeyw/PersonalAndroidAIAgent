package app.knotwork.android.domain.services

/**
 * Destroys the encrypted database together with its stored passphrase so the
 * application can start over with a fresh, empty data set.
 *
 * This is the explicit, user-confirmed escape hatch for the
 * [app.knotwork.android.domain.models.DbPassphraseUnavailableException]
 * recovery screen: when the passphrase is irrecoverably lost, the only way
 * forward is wiping the unreadable database and generating a new secret. The
 * operation must never run automatically — callers are required to gate it
 * behind a typed-confirm interaction (the same pattern used for DESTRUCTIVE
 * tool calls).
 */
interface DatabaseResetService {

    /**
     * Deletes the database file (including its journal/WAL companions) and
     * the persisted passphrase. After this call the next database access
     * generates a fresh passphrase and creates an empty schema.
     */
    suspend fun wipeAllData()
}
