package ai.agent.android.domain.repositories

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing application-wide settings and user preferences.
 * 
 * Provides abstraction over the underlying persistence mechanism (e.g., DataStore, SharedPreferences).
 */
interface SettingsRepository {
    
    /**
     * A [Flow] representing the current state of the first launch flag.
     * Emits `true` if it's the user's first time launching the app, `false` otherwise.
     */
    val isFirstLaunch: Flow<Boolean>

    /**
     * Updates the first launch flag.
     * 
     * @param isFirstLaunch The new value to set.
     */
    suspend fun setFirstLaunch(isFirstLaunch: Boolean)
}
