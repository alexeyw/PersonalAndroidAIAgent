package app.knotwork.android.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.knotwork.android.domain.models.DbPassphraseUnavailableException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Verifies the deferral contract of [DeferredPassphraseOpenHelperFactory]:
 * no passphrase access during factory/helper construction (i.e. during Hilt
 * provision), lazy delegate creation on first database access, no caching of
 * failed construction (Retry support), and WAL-flag replay.
 */
class DeferredPassphraseOpenHelperFactoryTest {

    private lateinit var passphraseProvider: EncryptedDbPassphraseProvider
    private lateinit var delegateHelper: SupportSQLiteOpenHelper
    private lateinit var delegateFactory: SupportSQLiteOpenHelper.Factory
    private lateinit var configuration: SupportSQLiteOpenHelper.Configuration
    private lateinit var writableDb: SupportSQLiteDatabase

    @Before
    fun setup() {
        passphraseProvider = mockk()
        writableDb = mockk()
        delegateHelper = mockk(relaxed = true)
        every { delegateHelper.writableDatabase } returns writableDb
        delegateFactory = mockk()
        every { delegateFactory.create(any()) } returns delegateHelper
        // Configuration exposes its fields as @JvmField (not getters), so it cannot be
        // stubbed with MockK — build a real instance through the public builder instead.
        configuration = SupportSQLiteOpenHelper.Configuration.builder(mockk(relaxed = true))
            .name(AppDatabase.DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
    }

    private fun createFactory(): DeferredPassphraseOpenHelperFactory =
        DeferredPassphraseOpenHelperFactory(passphraseProvider) { delegateFactory }

    @Test
    fun `given helper created when no database access then passphrase is never fetched`() {
        val helper = createFactory().create(configuration)

        assertEquals(AppDatabase.DATABASE_NAME, helper.databaseName)
        helper.setWriteAheadLoggingEnabled(true)
        helper.close()

        verify(exactly = 0) { passphraseProvider.getOrCreatePassphrase() }
    }

    @Test
    fun `given first database access when writableDatabase then fetches passphrase and delegates`() {
        every { passphraseProvider.getOrCreatePassphrase() } returns ByteArray(32)

        val helper = createFactory().create(configuration)
        val db = helper.writableDatabase

        assertSame(writableDb, db)
        verify(exactly = 1) { passphraseProvider.getOrCreatePassphrase() }
        verify(exactly = 1) { delegateFactory.create(configuration) }
    }

    @Test
    fun `given repeated access when delegate exists then passphrase fetched only once`() {
        every { passphraseProvider.getOrCreatePassphrase() } returns ByteArray(32)

        val helper = createFactory().create(configuration)
        helper.writableDatabase
        helper.writableDatabase

        verify(exactly = 1) { passphraseProvider.getOrCreatePassphrase() }
    }

    @Test
    fun `given passphrase failure when accessed again then construction is retried`() {
        var attempts = 0
        every { passphraseProvider.getOrCreatePassphrase() } answers {
            attempts++
            if (attempts == 1) {
                throw DbPassphraseUnavailableException(DbPassphraseUnavailableException.Reason.PREFS_OPEN_FAILED)
            }
            ByteArray(32)
        }

        val helper = createFactory().create(configuration)

        assertThrows(DbPassphraseUnavailableException::class.java) { helper.writableDatabase }
        // A failed construction must not be cached — the retry path depends on it.
        val db = helper.writableDatabase
        assertSame(writableDb, db)
        assertEquals(2, attempts)
    }

    @Test
    fun `given WAL enabled before first open when delegate created then flag is replayed`() {
        every { passphraseProvider.getOrCreatePassphrase() } returns ByteArray(32)

        val helper = createFactory().create(configuration)
        helper.setWriteAheadLoggingEnabled(true)
        helper.writableDatabase

        verifyOrder {
            delegateFactory.create(configuration)
            delegateHelper.setWriteAheadLoggingEnabled(true)
        }
    }

    @Test
    fun `given open delegate when closed then delegate is closed and reopened on next access`() {
        every { passphraseProvider.getOrCreatePassphrase() } returns ByteArray(32)

        val helper = createFactory().create(configuration)
        helper.writableDatabase
        helper.close()

        verify(exactly = 1) { delegateHelper.close() }

        helper.writableDatabase
        verify(exactly = 2) { delegateFactory.create(configuration) }
    }

    @Test
    fun `given open delegate when runExclusive then delegate is quiesced and action runs`() {
        every { passphraseProvider.getOrCreatePassphrase() } returns ByteArray(32)

        val factory = createFactory()
        val helper = factory.create(configuration)
        helper.writableDatabase

        var actionRan = false
        factory.runExclusive { actionRan = true }

        assertEquals(true, actionRan)
        verify(exactly = 1) { delegateHelper.close() }

        // The access after the exclusive section performs a full fresh open.
        helper.writableDatabase
        verify(exactly = 2) { delegateFactory.create(configuration) }
        verify(exactly = 2) { passphraseProvider.getOrCreatePassphrase() }
    }

    @Test
    fun `given wrong-key open failure when writableDatabase then rethrows as KEY_MISMATCH`() {
        every { passphraseProvider.getOrCreatePassphrase() } returns ByteArray(32)
        every { delegateHelper.writableDatabase } throws
            RuntimeException("file is not a database: , while compiling: select count(*) from sqlite_master")

        val helper = createFactory().create(configuration)

        val thrown = assertThrows(DbPassphraseUnavailableException::class.java) { helper.writableDatabase }
        assertEquals(DbPassphraseUnavailableException.Reason.KEY_MISMATCH, thrown.reason)
    }

    @Test
    fun `given unrelated open failure when writableDatabase then exception passes through unchanged`() {
        every { passphraseProvider.getOrCreatePassphrase() } returns ByteArray(32)
        every { delegateHelper.writableDatabase } throws RuntimeException("disk I/O error")

        val helper = createFactory().create(configuration)

        val thrown = assertThrows(RuntimeException::class.java) { helper.writableDatabase }
        assertEquals("disk I/O error", thrown.message)
    }
}
