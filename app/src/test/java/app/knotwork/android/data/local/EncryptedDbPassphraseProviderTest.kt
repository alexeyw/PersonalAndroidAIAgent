package app.knotwork.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import app.knotwork.android.domain.models.DbPassphraseUnavailableException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies the loss-protection invariant of [EncryptedDbPassphraseProvider]:
 * the passphrase is generated only when no database file exists, and any
 * failure to read it back while the database is present surfaces as
 * [DbPassphraseUnavailableException] instead of a silent regeneration that
 * would destroy the user's encrypted data.
 */
class EncryptedDbPassphraseProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var dbFile: File

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPrefs = mockk()
        editor = mockk(relaxed = true)
        every { sharedPrefs.edit() } returns editor

        dbFile = File(tempFolder.root, AppDatabase.DATABASE_NAME)
        every { context.getDatabasePath(AppDatabase.DATABASE_NAME) } returns dbFile

        mockkStatic(EncryptedSharedPreferences::class)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun stubPrefsCreate(result: () -> SharedPreferences) {
        every {
            EncryptedSharedPreferences.create(
                any<Context>(),
                any<String>(),
                any(),
                any(),
                any(),
            )
        } answers { result() }
    }

    private fun stubStoredHex(hex: String?) {
        every { sharedPrefs.getString("db_passphrase_hex", null) } returns hex
    }

    @Test
    fun `given prefs open failure and existing database when getOrCreatePassphrase then throws and keeps prefs file`() {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))
        stubPrefsCreate { throw SecurityException("Keystore unavailable") }

        val provider = EncryptedDbPassphraseProvider(context)

        val thrown = assertThrows(DbPassphraseUnavailableException::class.java) {
            provider.getOrCreatePassphrase()
        }
        assertEquals(DbPassphraseUnavailableException.Reason.PREFS_OPEN_FAILED, thrown.reason)
        // The old recovery path deleted and recreated the store — that must never happen here.
        verify(exactly = 0) { context.deleteSharedPreferences(any()) }
    }

    @Test
    fun `given malformed passphrase and existing database when getOrCreatePassphrase then no regeneration`() {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))
        stubPrefsCreate { sharedPrefs }
        stubStoredHex("not-valid-hex!!")

        val provider = EncryptedDbPassphraseProvider(context)

        val thrown = assertThrows(DbPassphraseUnavailableException::class.java) {
            provider.getOrCreatePassphrase()
        }
        assertEquals(DbPassphraseUnavailableException.Reason.PASSPHRASE_MALFORMED, thrown.reason)
        verify(exactly = 0) { editor.putString(any(), any()) }
    }

    @Test
    fun `given missing passphrase and existing database when getOrCreatePassphrase then throws without regenerating`() {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))
        stubPrefsCreate { sharedPrefs }
        stubStoredHex(null)

        val provider = EncryptedDbPassphraseProvider(context)

        val thrown = assertThrows(DbPassphraseUnavailableException::class.java) {
            provider.getOrCreatePassphrase()
        }
        assertEquals(DbPassphraseUnavailableException.Reason.PASSPHRASE_MISSING, thrown.reason)
        verify(exactly = 0) { editor.putString(any(), any()) }
    }

    @Test
    fun `given fresh install without database when getOrCreatePassphrase then generates and persists`() {
        stubPrefsCreate { sharedPrefs }
        stubStoredHex(null)
        every { editor.commit() } returns true

        val provider = EncryptedDbPassphraseProvider(context)
        val passphrase = provider.getOrCreatePassphrase()

        assertEquals(32, passphrase.size)
        verify(exactly = 1) { editor.putString("db_passphrase_hex", any()) }
    }

    @Test
    fun `given malformed passphrase without database when getOrCreatePassphrase then regenerates safely`() {
        stubPrefsCreate { sharedPrefs }
        stubStoredHex("zz-not-hex")
        every { editor.commit() } returns true

        val provider = EncryptedDbPassphraseProvider(context)
        val passphrase = provider.getOrCreatePassphrase()

        assertEquals(32, passphrase.size)
        verify(exactly = 1) { editor.putString("db_passphrase_hex", any()) }
    }

    @Test
    fun `given transient prefs failure when retried after recovery then returns original passphrase`() {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))
        val storedHex = "ab".repeat(32)
        var attempts = 0
        stubPrefsCreate {
            attempts++
            if (attempts == 1) throw SecurityException("Transient keystore failure") else sharedPrefs
        }
        stubStoredHex(storedHex)

        val provider = EncryptedDbPassphraseProvider(context)

        // First attempt: transient failure surfaces as a typed exception.
        assertThrows(DbPassphraseUnavailableException::class.java) {
            provider.getOrCreatePassphrase()
        }

        // Second attempt (after Keystore recovered): the SAME stored passphrase
        // is returned — the database remains openable.
        val recovered = provider.getOrCreatePassphrase()
        val expected = ByteArray(32) { 0xAB.toByte() }
        assertArrayEquals(expected, recovered)
        verify(exactly = 0) { editor.putString(any(), any()) }
        assertEquals(2, attempts)
    }

    @Test
    fun `given stored valid passphrase when getOrCreatePassphrase then returns decoded bytes`() {
        stubPrefsCreate { sharedPrefs }
        stubStoredHex("01".repeat(32))

        val provider = EncryptedDbPassphraseProvider(context)
        val passphrase = provider.getOrCreatePassphrase()

        assertArrayEquals(ByteArray(32) { 1 }, passphrase)
    }

    @Test
    fun `given reset requested when resetStoredPassphrase then deletes the prefs store`() {
        stubPrefsCreate { sharedPrefs }

        val provider = EncryptedDbPassphraseProvider(context)
        provider.resetStoredPassphrase()

        verify(exactly = 1) { context.deleteSharedPreferences("secure_db_passphrase") }
    }

    @Test
    fun `given prefs open failure without database when getOrCreatePassphrase then self-heals and generates`() {
        // No DB file: nothing can be orphaned, so the corrupt store may be recreated —
        // e.g. an Auto-Backup-restored prefs file on a device that has no data yet.
        var attempts = 0
        stubPrefsCreate {
            attempts++
            if (attempts == 1) throw SecurityException("Keyset undecryptable after restore") else sharedPrefs
        }
        stubStoredHex(null)
        every { editor.commit() } returns true

        val provider = EncryptedDbPassphraseProvider(context)
        val passphrase = provider.getOrCreatePassphrase()

        assertEquals(32, passphrase.size)
        verify(exactly = 1) { context.deleteSharedPreferences("secure_db_passphrase") }
        verify(exactly = 1) { editor.putString("db_passphrase_hex", any()) }
        assertEquals(2, attempts)
    }

    @Test
    fun `given cached prefs when reset then next access opens a fresh prefs instance`() {
        // Context.deleteSharedPreferences declares results undefined while a live instance
        // for the same name is retained — the reset must drop the cached holder so the
        // post-wipe regeneration goes through a freshly created store.
        var createCount = 0
        stubPrefsCreate {
            createCount++
            sharedPrefs
        }
        stubStoredHex("01".repeat(32))
        every { editor.commit() } returns true

        val provider = EncryptedDbPassphraseProvider(context)
        provider.getOrCreatePassphrase()
        assertEquals(1, createCount)

        provider.resetStoredPassphrase()
        provider.getOrCreatePassphrase()

        assertEquals(2, createCount)
    }
}
