package app.knotwork.android.data.local

import android.content.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies that [DatabaseResetServiceImpl] deletes both halves of the
 * encrypted-database state — the database file and the stored passphrase —
 * in a single quiesced wipe operation, and refuses to destroy the passphrase
 * while the database file survives.
 */
class DatabaseResetServiceImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var passphraseProvider: EncryptedDbPassphraseProvider
    private lateinit var openHelperFactory: DeferredPassphraseOpenHelperFactory
    private lateinit var dbFile: File
    private lateinit var service: DatabaseResetServiceImpl

    @Before
    fun setup() {
        dbFile = File(tempFolder.root, AppDatabase.DATABASE_NAME)
        context = mockk()
        every { context.getDatabasePath(AppDatabase.DATABASE_NAME) } returns dbFile
        every { context.deleteDatabase(AppDatabase.DATABASE_NAME) } answers { dbFile.delete() }
        passphraseProvider = mockk()
        every { passphraseProvider.resetStoredPassphrase() } just Runs
        openHelperFactory = DeferredPassphraseOpenHelperFactory(passphraseProvider) { mockk(relaxed = true) }
        service = DatabaseResetServiceImpl(context, passphraseProvider, openHelperFactory)
    }

    @Test
    fun `given wipe requested when wipeAllData then deletes database and passphrase`() = runTest {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))

        service.wipeAllData()

        verify(exactly = 1) { context.deleteDatabase(AppDatabase.DATABASE_NAME) }
        verify(exactly = 1) { passphraseProvider.resetStoredPassphrase() }
    }

    @Test
    fun `given database already absent when wipeAllData then still resets passphrase`() = runTest {
        service.wipeAllData()

        verify(exactly = 1) { passphraseProvider.resetStoredPassphrase() }
    }

    @Test
    fun `given database file survives deletion when wipeAllData then aborts and keeps passphrase`() = runTest {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))
        // Simulate a failed unlink: deleteDatabase reports false and the file remains.
        every { context.deleteDatabase(AppDatabase.DATABASE_NAME) } returns false

        val thrown = runCatching { service.wipeAllData() }.exceptionOrNull()

        assertTrue(thrown is DatabaseWipeFailedException)
        // The stored passphrase may still be the valid key for the surviving file —
        // destroying it would manufacture the exact data-loss state the wipe escapes.
        verify(exactly = 0) { passphraseProvider.resetStoredPassphrase() }
    }
}
