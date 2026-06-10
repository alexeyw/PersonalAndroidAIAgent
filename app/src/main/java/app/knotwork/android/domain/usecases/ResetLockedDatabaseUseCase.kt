package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.services.DatabaseResetService
import javax.inject.Inject

/**
 * Wipes the encrypted database and its stored passphrase after the user has
 * explicitly confirmed a full data reset on the splash recovery screen.
 *
 * Thin orchestration wrapper around [DatabaseResetService] so the
 * presentation layer depends on a use case rather than a service interface,
 * consistent with the rest of the domain API surface.
 */
class ResetLockedDatabaseUseCase @Inject constructor(private val databaseResetService: DatabaseResetService) {

    /**
     * Executes the wipe. Once it returns, re-running the application
     * initialization sequence creates a fresh database with a new passphrase.
     */
    suspend operator fun invoke() = databaseResetService.wipeAllData()
}
