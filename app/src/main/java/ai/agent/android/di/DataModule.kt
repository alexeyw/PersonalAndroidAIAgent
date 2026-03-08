package ai.agent.android.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
    // Add @Binds functions here to map implementations to interfaces
}
