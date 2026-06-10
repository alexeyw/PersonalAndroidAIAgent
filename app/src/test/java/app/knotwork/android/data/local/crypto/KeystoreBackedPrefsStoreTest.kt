package app.knotwork.android.data.local.crypto

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the storage semantics of [KeystoreBackedPrefsStore] against a
 * [FakeAeadCipher]: framing, slot binding via associated data, the
 * absent-vs-unreadable distinction, and the destroy contract.
 */
class KeystoreBackedPrefsStoreTest {

    private companion object {
        const val PREFS_NAME = "test_store"
        const val KEY_ALIAS = "test_alias"
    }

    private lateinit var context: Context
    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var cipher: FakeAeadCipher
    private lateinit var store: KeystoreBackedPrefsStore

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        prefs = InMemorySharedPreferences()
        cipher = FakeAeadCipher()
        every { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
        store = KeystoreBackedPrefsStore(context, PREFS_NAME, KEY_ALIAS, cipher)
    }

    @Test
    fun `given stored value when getString then round-trips`() {
        store.putString("entry", "plain value")

        assertEquals("plain value", store.getString("entry"))
    }

    @Test
    fun `given no stored value when getString then returns null`() {
        assertNull(store.getString("absent"))
    }

    @Test
    fun `given stored value when raw prefs inspected then value is not plaintext`() {
        store.putString("entry", "plain value")

        val raw = prefs.values["entry"] as String
        assertTrue(raw.isNotEmpty())
        assertTrue(!raw.contains("plain value"))
    }

    @Test
    fun `given blob copied between entries when getString then fails authentication`() {
        store.putString("source", "secret")
        prefs.values["target"] = prefs.values["source"]

        // Same store, same key — but the associated data binds the blob to
        // its original entry name, so the swapped copy must not decrypt.
        assertEquals("secret", store.getString("source"))
        assertThrows(SecureValueUnreadableException::class.java) {
            store.getString("target")
        }
    }

    @Test
    fun `given undecryptable value when getString then throws SecureValueUnreadableException`() {
        store.putString("entry", "value")
        cipher.failDecrypt = true

        assertThrows(SecureValueUnreadableException::class.java) {
            store.getString("entry")
        }
    }

    @Test
    fun `given non-base64 stored value when getString then throws SecureValueUnreadableException`() {
        prefs.values["entry"] = "%%% not base64 %%%"

        assertThrows(SecureValueUnreadableException::class.java) {
            store.getString("entry")
        }
    }

    @Test
    fun `given stored value when removed then getString returns null`() {
        store.putString("entry", "value")
        store.remove("entry")

        assertNull(store.getString("entry"))
    }

    @Test
    fun `given destroy then entries cleared file deleted and key deleted`() {
        store.putString("entry", "value")

        store.destroy()

        assertTrue(prefs.values.isEmpty())
        verify(exactly = 1) { context.deleteSharedPreferences(PREFS_NAME) }
        assertEquals(listOf(KEY_ALIAS), cipher.deletedAliases)
    }

    @Test
    fun `given destroy then next write opens a fresh prefs instance`() {
        store.putString("entry", "value")
        store.destroy()
        store.putString("entry", "fresh")

        // deleteSharedPreferences declares results undefined while a live
        // instance is retained — the store must re-resolve after destroy.
        verify(exactly = 2) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
        assertEquals("fresh", store.getString("entry"))
    }
}
