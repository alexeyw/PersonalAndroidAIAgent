package app.knotwork.android.data.local

import android.content.Context
import app.knotwork.android.data.local.crypto.AeadCipher
import app.knotwork.android.data.local.crypto.KeystoreBackedPrefsStore
import app.knotwork.android.data.local.crypto.SecureValueUnreadableException
import app.knotwork.android.domain.constants.SettingsDefaults
import app.knotwork.android.domain.repositories.ApiKeyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [ApiKeyRepository] that securely stores API keys
 * in a [KeystoreBackedPrefsStore] — values encrypted with AES-GCM under a
 * dedicated, non-exportable Android Keystore key.
 *
 * **Recovery semantics — deliberately different from [EncryptedDbPassphraseProvider].**
 * A stored value that can no longer be decrypted (e.g. the Keystore key was lost during a
 * backup/restore) is treated as absent: the corrupt entry is removed and the getter returns
 * `null`. That is safe here because API keys are user-re-enterable — the worst outcome is the
 * user pasting their keys again. The database passphrase provider must never do this: its
 * secret cannot be re-derived, and destroying it would render the encrypted database
 * permanently unreadable.
 *
 * Earlier releases kept the keys in `EncryptedSharedPreferences` (deprecated upstream and
 * removed from this project without a data migration, as permitted by the pre-release storage
 * policy); a leftover legacy file is ignored — previously stored keys simply have to be
 * re-entered once.
 *
 * @property context The application context used to create the shared preferences.
 * @param cipher The AEAD boundary used to protect the stored values.
 */
@Singleton
class ApiKeyManager @Inject constructor(@ApplicationContext private val context: Context, cipher: AeadCipher) :
    ApiKeyRepository {

    private val store = KeystoreBackedPrefsStore(
        context = context,
        prefsName = PREFS_NAME,
        keyAlias = KEY_ALIAS,
        cipher = cipher,
    )

    private object Keys {
        const val OPENAI_KEY = "openai_api_key"
        const val OPENAI_MODEL = "openai_model"
        const val ANTHROPIC_KEY = "anthropic_api_key"
        const val ANTHROPIC_MODEL = "anthropic_model"
        const val GOOGLE_KEY = "google_api_key"
        const val GOOGLE_MODEL = "google_model"
        const val DEEPSEEK_KEY = "deepseek_api_key"
        const val DEEPSEEK_MODEL = "deepseek_model"
        const val OLLAMA_URL = "ollama_base_url"
        const val OLLAMA_MODEL = "ollama_model"
        const val OLLAMA_CONTEXT = "ollama_context"
    }

    // Mutable state flows to allow reactive observing of key changes
    private val _openAIKeyFlow by lazy { MutableStateFlow(readOrNull(Keys.OPENAI_KEY)) }
    private val _openAIModelFlow by lazy { MutableStateFlow(readOrNull(Keys.OPENAI_MODEL)) }
    private val _anthropicKeyFlow by lazy { MutableStateFlow(readOrNull(Keys.ANTHROPIC_KEY)) }
    private val _anthropicModelFlow by lazy { MutableStateFlow(readOrNull(Keys.ANTHROPIC_MODEL)) }
    private val _googleKeyFlow by lazy { MutableStateFlow(readOrNull(Keys.GOOGLE_KEY)) }
    private val _googleModelFlow by lazy { MutableStateFlow(readOrNull(Keys.GOOGLE_MODEL)) }
    private val _deepSeekKeyFlow by lazy { MutableStateFlow(readOrNull(Keys.DEEPSEEK_KEY)) }
    private val _deepSeekModelFlow by lazy { MutableStateFlow(readOrNull(Keys.DEEPSEEK_MODEL)) }
    private val _ollamaUrlFlow by lazy { MutableStateFlow(readOrNull(Keys.OLLAMA_URL)) }
    private val _ollamaModelFlow by lazy { MutableStateFlow(readOrNull(Keys.OLLAMA_MODEL)) }
    private val _ollamaContextFlow by lazy {
        MutableStateFlow(
            readOrNull(Keys.OLLAMA_CONTEXT)?.toIntOrNull() ?: SettingsDefaults.OLLAMA_CONTEXT_WINDOW_DEFAULT,
        )
    }

    override fun getOpenAIKey(): Flow<String?> = _openAIKeyFlow.asStateFlow()

    override suspend fun setOpenAIKey(key: String?) {
        saveString(Keys.OPENAI_KEY, key)
        _openAIKeyFlow.value = key
    }

    override fun getOpenAIModel(): Flow<String?> = _openAIModelFlow.asStateFlow()

    override suspend fun setOpenAIModel(model: String?) {
        saveString(Keys.OPENAI_MODEL, model)
        _openAIModelFlow.value = model
    }

    override fun getAnthropicKey(): Flow<String?> = _anthropicKeyFlow.asStateFlow()

    override suspend fun setAnthropicKey(key: String?) {
        saveString(Keys.ANTHROPIC_KEY, key)
        _anthropicKeyFlow.value = key
    }

    override fun getAnthropicModel(): Flow<String?> = _anthropicModelFlow.asStateFlow()

    override suspend fun setAnthropicModel(model: String?) {
        saveString(Keys.ANTHROPIC_MODEL, model)
        _anthropicModelFlow.value = model
    }

    override fun getGoogleKey(): Flow<String?> = _googleKeyFlow.asStateFlow()

    override suspend fun setGoogleKey(key: String?) {
        saveString(Keys.GOOGLE_KEY, key)
        _googleKeyFlow.value = key
    }

    override fun getGoogleModel(): Flow<String?> = _googleModelFlow.asStateFlow()

    override suspend fun setGoogleModel(model: String?) {
        saveString(Keys.GOOGLE_MODEL, model)
        _googleModelFlow.value = model
    }

    override fun getDeepSeekKey(): Flow<String?> = _deepSeekKeyFlow.asStateFlow()

    override suspend fun setDeepSeekKey(key: String?) {
        saveString(Keys.DEEPSEEK_KEY, key)
        _deepSeekKeyFlow.value = key
    }

    override fun getDeepSeekModel(): Flow<String?> = _deepSeekModelFlow.asStateFlow()

    override suspend fun setDeepSeekModel(model: String?) {
        saveString(Keys.DEEPSEEK_MODEL, model)
        _deepSeekModelFlow.value = model
    }

    override fun getOllamaBaseUrl(): Flow<String?> = _ollamaUrlFlow.asStateFlow()

    override suspend fun setOllamaBaseUrl(url: String?) {
        saveString(Keys.OLLAMA_URL, url)
        _ollamaUrlFlow.value = url
    }

    override fun getOllamaModelName(): Flow<String?> = _ollamaModelFlow.asStateFlow()

    override suspend fun setOllamaModelName(model: String?) {
        saveString(Keys.OLLAMA_MODEL, model)
        _ollamaModelFlow.value = model
    }

    override fun getOllamaContextWindowSize(): Flow<Int> = _ollamaContextFlow.asStateFlow()

    override suspend fun setOllamaContextWindowSize(size: Int) {
        store.putString(Keys.OLLAMA_CONTEXT, size.toString())
        _ollamaContextFlow.value = size
    }

    /**
     * Reads a stored value, applying the re-enterable-secret recovery policy: an entry that
     * cannot be decrypted is dropped and reported as absent instead of propagating the error.
     */
    private fun readOrNull(key: String): String? = try {
        store.getString(key)
    } catch (e: SecureValueUnreadableException) {
        Timber.e(e, "Stored API-key entry '%s' is unreadable; treating it as unset.", key)
        store.remove(key)
        null
    }

    private fun saveString(key: String, value: String?) {
        if (value == null) {
            store.remove(key)
        } else {
            store.putString(key, value)
        }
    }

    private companion object {
        /** Name of the [KeystoreBackedPrefsStore] preferences file holding the API keys. */
        const val PREFS_NAME = "secure_api_keys_v2"

        /** Android Keystore alias of the AEAD key dedicated to the API-key store. */
        const val KEY_ALIAS = "knotwork.api_keys"
    }
}
