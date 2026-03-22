package ai.agent.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ai.agent.android.domain.repositories.ApiKeyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.content.edit
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
class ApiKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ApiKeyRepository {

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
            Timber.e(e, "Failed to initialize EncryptedSharedPreferences. Attempting recovery by clearing corrupt data.")
            // Recovery path: if data is corrupted (e.g. key lost during backup/restore), delete the file and recreate
            deleteSharedPreferences(prefsName)
            createEncryptedSharedPreferences()
        }
    }

    private fun createEncryptedSharedPreferences(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

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
        const val ANTHROPIC_KEY = "anthropic_api_key"
        const val GOOGLE_KEY = "google_api_key"
        const val DEEPSEEK_KEY = "deepseek_api_key"
        const val OLLAMA_URL = "ollama_base_url"
    }

    // Mutable state flows to allow reactive observing of key changes
    private val _openAIKeyFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.OPENAI_KEY, null)) }
    private val _anthropicKeyFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.ANTHROPIC_KEY, null)) }
    private val _googleKeyFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.GOOGLE_KEY, null)) }
    private val _deepSeekKeyFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.DEEPSEEK_KEY, null)) }
    private val _ollamaUrlFlow by lazy { MutableStateFlow(sharedPreferences.getString(Keys.OLLAMA_URL, null)) }

    override fun getOpenAIKey(): Flow<String?> = _openAIKeyFlow.asStateFlow()

    override suspend fun setOpenAIKey(key: String?) {
        saveString(Keys.OPENAI_KEY, key)
        _openAIKeyFlow.value = key
    }

    override fun getAnthropicKey(): Flow<String?> = _anthropicKeyFlow.asStateFlow()

    override suspend fun setAnthropicKey(key: String?) {
        saveString(Keys.ANTHROPIC_KEY, key)
        _anthropicKeyFlow.value = key
    }

    override fun getGoogleKey(): Flow<String?> = _googleKeyFlow.asStateFlow()

    override suspend fun setGoogleKey(key: String?) {
        saveString(Keys.GOOGLE_KEY, key)
        _googleKeyFlow.value = key
    }

    override fun getDeepSeekKey(): Flow<String?> = _deepSeekKeyFlow.asStateFlow()

    override suspend fun setDeepSeekKey(key: String?) {
        saveString(Keys.DEEPSEEK_KEY, key)
        _deepSeekKeyFlow.value = key
    }

    override fun getOllamaBaseUrl(): Flow<String?> = _ollamaUrlFlow.asStateFlow()

    override suspend fun setOllamaBaseUrl(url: String?) {
        saveString(Keys.OLLAMA_URL, url)
        _ollamaUrlFlow.value = url
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
