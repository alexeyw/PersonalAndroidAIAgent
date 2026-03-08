package ai.agent.android.di

import ai.agent.android.data.local.SettingsManager
import ai.agent.android.domain.repositories.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Data layer dependency injection module.
 * 
 * This module is responsible for binding repository implementations from the
 * data layer to their corresponding interfaces in the domain layer.
 * It is installed in the SingletonComponent to ensure that repositories
 * act as single sources of truth throughout the application lifecycle.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /**
     * Binds the [SettingsManager] implementation to the [SettingsRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsManager: SettingsManager
    ): SettingsRepository
}

