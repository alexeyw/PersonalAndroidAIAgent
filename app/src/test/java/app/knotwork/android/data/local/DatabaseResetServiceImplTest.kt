package app.knotwork.android.data.local

import android.content.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Verifies that [DatabaseResetServiceImpl] deletes both halves of the
 * encrypted-database state — the database file and the stored passphrase —
 * in a single wipe operation.
 */
class DatabaseResetServiceImplTest {

    private lateinit var context: Context
    private lateinit var passphraseProvider: EncryptedDbPassphraseProvider
    private lateinit var service: DatabaseResetServiceImpl

    @Before
    fun setup() {
        context = mockk()
        every { context.deleteDatabase(AppDatabase.DATABASE_NAME) } returns true
        passphraseProvider = mockk()
        every { passphraseProvider.resetStoredPassphrase() } just Runs
        service = DatabaseResetServiceImpl(context, passphraseProvider)
    }

    @Test
    fun `given wipe requested when wipeAllData then deletes database and passphrase`() = runTest {
        service.wipeAllData()

        verify(exactly = 1) { context.deleteDatabase(AppDatabase.DATABASE_NAME) }
        verify(exactly = 1) { passphraseProvider.resetStoredPassphrase() }
    }

    @Test
    fun `given database already absent when wipeAllData then still resets passphrase`() = runTest {
        every { context.deleteDatabase(AppDatabase.DATABASE_NAME) } returns false

        service.wipeAllData()

        verify(exactly = 1) { passphraseProvider.resetStoredPassphrase() }
    }
}
