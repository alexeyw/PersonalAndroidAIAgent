package ai.agent.android.di

import ai.agent.android.data.engine.LiteRTLlmEngine
import ai.agent.android.data.local.SettingsManager
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.data.network.AndroidModelDownloadManager
import ai.agent.android.domain.repositories.ModelDownloadManager
import ai.agent.android.data.repositories.LocalModelRepositoryImpl
import ai.agent.android.domain.repositories.LocalModelRepository
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
     * Binds the [ai.agent.android.data.repositories.LocalModelRepositoryImpl] implementation to the [ai.agent.android.domain.repositories.LocalModelRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindLocalModelRepository(
        repository: LocalModelRepositoryImpl
    ): LocalModelRepository

    /**
     * Binds the [SettingsManager] implementation to the [SettingsRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsManager: SettingsManager
    ): SettingsRepository

    /**
     * Binds the [LiteRTLlmEngine] implementation to the [LlmInferenceEngine] interface.
     */
    @Binds
    @Singleton
    abstract fun bindLlmInferenceEngine(
        engine: LiteRTLlmEngine
    ): LlmInferenceEngine

    /**
     * Binds the [AndroidModelDownloadManager] implementation to the [ModelDownloadManager] interface.
     */
    @Binds
    @Singleton
    abstract fun bindModelDownloadManager(
        downloadManager: AndroidModelDownloadManager
    ): ModelDownloadManager
}

