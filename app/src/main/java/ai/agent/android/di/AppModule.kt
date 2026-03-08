package ai.agent.android.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Global application-level dependency injection module.
 * 
 * This module is installed in the SingletonComponent, meaning the dependencies
 * provided here will live as long as the application itself.
 * Use this module to provide system-wide singletons, such as application context
 * providers, database instances, network clients, etc.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Add global @Provides functions here
}
