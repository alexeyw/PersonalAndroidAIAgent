package app.knotwork.android.data.local

import android.content.Context
import app.knotwork.android.data.local.crypto.FakeAeadCipher
import app.knotwork.android.data.local.crypto.InMemorySharedPreferences
import app.knotwork.android.domain.models.DbPassphraseUnavailableException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    private companion object {
        const val PREFS_NAME = "secure_db_passphrase_v2"
        const val LEGACY_PREFS_NAME = "secure_db_passphrase"
        const val PASSPHRASE_KEY = "db_passphrase_hex"
        const val KEY_ALIAS = "knotwork.db_passphrase"
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var cipher: FakeAeadCipher
    private lateinit var dbFile: File

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        prefs = InMemorySharedPreferences()
        cipher = FakeAeadCipher()
        every { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns prefs

        dbFile = File(tempFolder.root, AppDatabase.DATABASE_NAME)
        every { context.getDatabasePath(AppDatabase.DATABASE_NAME) } returns dbFile
    }

    private fun provider() = EncryptedDbPassphraseProvider(context, cipher)

    /** Seeds the backing prefs with [hex] sealed exactly the way the production store seals it. */
    private fun storeHex(hex: String) {
        val blob = cipher.encrypt(KEY_ALIAS, "$PREFS_NAME/$PASSPHRASE_KEY".toByteArray(), hex.toByteArray())
        prefs.values[PASSPHRASE_KEY] = java.util.Base64.getEncoder().encodeToString(blob)
    }

    @Test
    fun `given undecryptable passphrase and existing database when read then throws and keeps store`() {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))
        storeHex("ab".repeat(32))
        cipher.failDecrypt = true

        val thrown = assertThrows(DbPassphraseUnavailableException::class.java) {
            provider().getOrCreatePassphrase()
        }

        assertEquals(DbPassphraseUnavailableException.Reason.DECRYPTION_FAILED, thrown.reason)
        // The old ESP recovery path deleted and recreated the store — that must never happen
        // here: the stored blob and the Keystore key both survive for a later retry.
        assertTrue(prefs.values.containsKey(PASSPHRASE_KEY))
        assertTrue(cipher.deletedAliases.isEmpty())
        verify(exactly = 0) { context.deleteSharedPreferences(any()) }
    }

    @Test
    fun `given malformed passphrase and existing database when getOrCreatePassphrase then no regeneration`() {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))
        storeHex("not-valid-hex!!")

        val thrown = assertThrows(DbPassphraseUnavailableException::class.java) {
            provider().getOrCreatePassphrase()
        }

        assertEquals(DbPassphraseUnavailableException.Reason.PASSPHRASE_MALFORMED, thrown.reason)
        assertEquals(1, prefs.values.size)
    }

    @Test
    fun `given missing passphrase and existing database when getOrCreatePassphrase then throws without regenerating`() {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))

        val thrown = assertThrows(DbPassphraseUnavailableException::class.java) {
            provider().getOrCreatePassphrase()
        }

        assertEquals(DbPassphraseUnavailableException.Reason.PASSPHRASE_MISSING, thrown.reason)
        assertTrue(prefs.values.isEmpty())
    }

    @Test
    fun `given fresh install without database when getOrCreatePassphrase then generates and persists`() {
        val passphrase = provider().getOrCreatePassphrase()

        assertEquals(32, passphrase.size)
        assertTrue(prefs.values.containsKey(PASSPHRASE_KEY))
    }

    @Test
    fun `given fresh generation when legacy store file exists then it is deleted`() {
        provider().getOrCreatePassphrase()

        // A fresh passphrase begins a fresh data lifetime; the pre-migration
        // EncryptedSharedPreferences file no longer guards anything usable.
        verify(exactly = 1) { context.deleteSharedPreferences(LEGACY_PREFS_NAME) }
    }

    @Test
    fun `given failure path when database exists then legacy store file is kept`() {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))

        assertThrows(DbPassphraseUnavailableException::class.java) {
            provider().getOrCreatePassphrase()
        }

        // Downgrading the APK is the remaining manual escape hatch for a
        // pre-migration database — the legacy file must survive failures.
        verify(exactly = 0) { context.deleteSharedPreferences(LEGACY_PREFS_NAME) }
    }

    @Test
    fun `given malformed passphrase without database when getOrCreatePassphrase then regenerates safely`() {
        storeHex("zz-not-hex")

        val passphrase = provider().getOrCreatePassphrase()

        assertEquals(32, passphrase.size)
    }

    @Test
    fun `given transient decrypt failure when retried after recovery then returns original passphrase`() {
        dbFile.writeBytes(byteArrayOf(1, 2, 3))
        storeHex("ab".repeat(32))
        val subject = provider()

        // First attempt: transient Keystore failure surfaces as a typed exception.
        cipher.failDecrypt = true
        assertThrows(DbPassphraseUnavailableException::class.java) {
            subject.getOrCreatePassphrase()
        }

        // Second attempt (after Keystore recovered): the SAME stored passphrase
        // is returned — the database remains openable.
        cipher.failDecrypt = false
        val recovered = subject.getOrCreatePassphrase()
        assertArrayEquals(ByteArray(32) { 0xAB.toByte() }, recovered)
    }

    @Test
    fun `given stored valid passphrase when getOrCreatePassphrase then returns decoded bytes`() {
        storeHex("01".repeat(32))

        val passphrase = provider().getOrCreatePassphrase()

        assertArrayEquals(ByteArray(32) { 1 }, passphrase)
    }

    @Test
    fun `given returned passphrase mutated when read again then stored value is unaffected`() {
        val subject = provider()
        val first = subject.getOrCreatePassphrase()
        val expected = first.copyOf()

        first.fill(0)

        assertArrayEquals(expected, subject.getOrCreatePassphrase())
    }

    @Test
    fun `given reset requested when resetStoredPassphrase then destroys store and legacy file`() {
        val subject = provider()
        subject.getOrCreatePassphrase()

        subject.resetStoredPassphrase()

        assertTrue(prefs.values.isEmpty())
        assertEquals(listOf(KEY_ALIAS), cipher.deletedAliases)
        verify(exactly = 1) { context.deleteSharedPreferences(PREFS_NAME) }
        verify(exactly = 2) { context.deleteSharedPreferences(LEGACY_PREFS_NAME) }
    }

    @Test
    fun `given undecryptable store without database when getOrCreatePassphrase then self-heals and generates`() {
        // No DB file: nothing can be orphaned, so the unreadable store may be
        // destroyed and recreated — e.g. an Auto-Backup-restored prefs file on
        // a device whose Keystore key did not travel with it.
        storeHex("ab".repeat(32))
        cipher.failDecrypt = true

        val passphrase = provider().getOrCreatePassphrase()

        assertEquals(32, passphrase.size)
        assertEquals(listOf(KEY_ALIAS), cipher.deletedAliases)
        verify(exactly = 1) { context.deleteSharedPreferences(PREFS_NAME) }
    }

    @Test
    fun `given reset then next generation produces a different persisted passphrase`() {
        val subject = provider()
        val first = subject.getOrCreatePassphrase()

        subject.resetStoredPassphrase()
        val second = subject.getOrCreatePassphrase()

        assertEquals(32, second.size)
        assertFalse(first.contentEquals(second))
    }
}
