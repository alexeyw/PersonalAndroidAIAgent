package app.knotwork.android.data.local

import android.content.Context
import app.knotwork.android.data.local.crypto.FakeAeadCipher
import app.knotwork.android.data.local.crypto.InMemorySharedPreferences
import app.knotwork.android.domain.constants.SettingsDefaults
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies [ApiKeyManager]'s storage round-trip and its re-enterable-secret
 * recovery policy: values that can no longer be decrypted are reported as
 * absent and dropped, never propagated as errors.
 */
class ApiKeyManagerTest {

    private companion object {
        const val PREFS_NAME = "secure_api_keys_v2"
        const val OPENAI_KEY = "openai_api_key"
        const val OLLAMA_CONTEXT = "ollama_context"
    }

    private lateinit var context: Context
    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var cipher: FakeAeadCipher

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        prefs = InMemorySharedPreferences()
        cipher = FakeAeadCipher()
        every { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
    }

    private fun manager() = ApiKeyManager(context, cipher)

    @Test
    fun `given key saved when read through a fresh instance then round-trips`() = runTest {
        manager().setOpenAIKey("sk-test-123")

        assertEquals("sk-test-123", manager().getOpenAIKey().first())
    }

    @Test
    fun `given key saved when raw prefs inspected then value is not plaintext`() = runTest {
        manager().setOpenAIKey("sk-test-123")

        val raw = prefs.values[OPENAI_KEY] as String
        assertFalse(raw.contains("sk-test-123"))
    }

    @Test
    fun `given key set to null when read then returns null and entry is removed`() = runTest {
        val subject = manager()
        subject.setOpenAIKey("sk-test-123")

        subject.setOpenAIKey(null)

        assertNull(subject.getOpenAIKey().first())
        assertFalse(prefs.values.containsKey(OPENAI_KEY))
    }

    @Test
    fun `given undecryptable stored key when read then treated as unset and dropped`() = runTest {
        manager().setOpenAIKey("sk-test-123")
        cipher.failDecrypt = true

        // A lost Keystore key must surface as "no key configured", not crash:
        // API keys are user re-enterable, unlike the database passphrase.
        assertNull(manager().getOpenAIKey().first())
        assertFalse(prefs.values.containsKey(OPENAI_KEY))
    }

    @Test
    fun `given no stored ollama context when read then returns default`() = runTest {
        assertEquals(
            SettingsDefaults.OLLAMA_CONTEXT_WINDOW_DEFAULT,
            manager().getOllamaContextWindowSize().first(),
        )
    }

    @Test
    fun `given ollama context saved when read through a fresh instance then round-trips`() = runTest {
        manager().setOllamaContextWindowSize(8192)

        assertEquals(8192, manager().getOllamaContextWindowSize().first())
        assertTrue(prefs.values.containsKey(OLLAMA_CONTEXT))
    }

    @Test
    fun `given updated key when flow observed then emits the new value`() = runTest {
        val subject = manager()
        subject.setAnthropicKey("first")

        subject.setAnthropicKey("second")

        assertEquals("second", subject.getAnthropicKey().first())
    }
}
