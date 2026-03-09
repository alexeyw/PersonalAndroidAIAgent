package ai.agent.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import ai.agent.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Concrete implementation of [SettingsRepository] utilizing Androidx DataStore Preferences.
 * 
 * @property dataStore The underlying DataStore instance for persistence.
 */
class SettingsManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val HUGGING_FACE_TOKEN = stringPreferencesKey("hugging_face_token")
    }

    override val isFirstLaunch: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.IS_FIRST_LAUNCH] ?: true
        }

    override suspend fun setFirstLaunch(isFirstLaunch: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FIRST_LAUNCH] = isFirstLaunch
        }
    }

    override val huggingFaceAuthToken: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.e(exception, "Error reading preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.HUGGING_FACE_TOKEN]
        }

    override suspend fun setHuggingFaceAuthToken(token: String?) {
        dataStore.edit { preferences ->
            if (token == null) {
                preferences.remove(PreferencesKeys.HUGGING_FACE_TOKEN)
            } else {
                preferences[PreferencesKeys.HUGGING_FACE_TOKEN] = token
            }
        }
    }
}
