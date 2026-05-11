package ai.agent.android.data.local

import ai.agent.android.domain.constants.SettingsDefaults
import ai.agent.android.domain.repositories.ApiKeyRepository
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [ApiKeyRepository] that securely stores API keys
 * using Android's EncryptedSharedPreferences.
 *
 * @property context The application context used to create the shared preferences.
 */
@Singleton
class ApiKeyManager @Inject constructor(@ApplicationContext private val context: Context) : ApiKeyRepository {

    private val prefsName = "secure_api_keys"

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedSharedPreferences()
        } catch (e: Exception) {
            Timber.e(
                e,
                "Failed to initialize EncryptedSharedPreferences. Attempting recovery by clearing corrupt data.",
            )
            // Recovery path: if data is corrupted (e.g. key lost during backup/restore), delete the file and recreate
            deleteSharedPreferences(prefsName)
            createEncryptedSharedPreferences()
        }
    }

    private fun createEncryptedSharedPreferences(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        prefsName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun deleteSharedPreferences(name: String) {
        try {
            // Context.deleteSharedPreferences is available in API 24+
            context.deleteSharedPreferences(name)
        } catch (e: Exception) {
            // Fallback for older APIs or if deleteSharedPreferences fails
            val dir = File(context.applicationInfo.dataDir, "shared_prefs")
            val file = File(dir, "$name.xml")
            if (file.exists()) {
                file.delete()
            }
        }
    }

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
    private val _openAIKeyFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.OPENAI_KEY, null)) }
    private val _openAIModelFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.OPENAI_MODEL, null)) }
    private val _anthropicKeyFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.ANTHROPIC_KEY, null)) }
    private val _anthropicModelFlow by lazy {
        MutableStateFlow(sharedPreferences.getString(Keys.ANTHROPIC_MODEL, null))
    }
    private val _googleKeyFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.GOOGLE_KEY, null)) }
    private val _googleModelFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.GOOGLE_MODEL, null)) }
    private val _deepSeekKeyFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.DEEPSEEK_KEY, null)) }
    private val _deepSeekModelFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.DEEPSEEK_MODEL, null)) }
    private val _ollamaUrlFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.OLLAMA_URL, null)) }
    private val _ollamaModelFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.OLLAMA_MODEL, null)) }
    private val _ollamaContextFlow by lazy {
        MutableStateFlow(sharedPreferences.getInt(Keys.OLLAMA_CONTEXT, SettingsDefaults.OLLAMA_CONTEXT_WINDOW_DEFAULT))
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
        sharedPreferences.edit {
            putInt(Keys.OLLAMA_CONTEXT, size)
        }
        _ollamaContextFlow.value = size
    }

    private fun saveString(key: String, value: String?) {
        sharedPreferences.edit {
            if (value == null) {
                remove(key)
            } else {
                putString(key, value)
            }
        }
    }
}
