package ai.agent.android.di

import ai.agent.android.data.engine.LiteRTLlmEngine
import ai.agent.android.data.local.SettingsManager
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.data.network.AndroidModelDownloadManager
import ai.agent.android.domain.repositories.ModelDownloadManager
import ai.agent.android.data.repositories.LocalModelRepositoryImpl
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.data.repositories.ChatRepositoryImpl
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.data.repositories.MemoryRepositoryImpl
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.data.engine.MediaPipeTextEmbeddingEngine
import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.data.local.ApiKeyManager
import ai.agent.android.domain.repositories.ApiKeyRepository
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
     * Binds the [ApiKeyManager] implementation to the [ApiKeyRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindApiKeyRepository(
        apiKeyManager: ApiKeyManager
    ): ApiKeyRepository

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
     * Binds the [MediaPipeTextEmbeddingEngine] implementation to the [TextEmbeddingEngine] interface.
     */
    @Binds
    @Singleton
    abstract fun bindTextEmbeddingEngine(
        engine: MediaPipeTextEmbeddingEngine
    ): TextEmbeddingEngine

    /**
     * Binds the [MemoryRepositoryImpl] implementation to the [MemoryRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindMemoryRepository(
        repository: MemoryRepositoryImpl
    ): MemoryRepository

    /**
     * Binds the [AndroidModelDownloadManager] implementation to the [ModelDownloadManager] interface.
     */
    @Binds
    @Singleton
    abstract fun bindModelDownloadManager(
        downloadManager: AndroidModelDownloadManager
    ): ModelDownloadManager

    /**
     * Binds the [ChatRepositoryImpl] implementation to the [ChatRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindChatRepository(
        repository: ChatRepositoryImpl
    ): ChatRepository

    /**
     * Binds the [ai.agent.android.data.repositories.ToolRepositoryImpl] implementation to the [ai.agent.android.domain.repositories.ToolRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindToolRepository(
        repository: ai.agent.android.data.repositories.ToolRepositoryImpl
    ): ai.agent.android.domain.repositories.ToolRepository

    /**
     * Binds the [ai.agent.android.data.mcp.KoogMcpClientFactory] implementation to the [ai.agent.android.data.mcp.McpClientFactory] interface.
     */
    @Binds
    @Singleton
    abstract fun bindMcpClientFactory(
        factory: ai.agent.android.data.mcp.KoogMcpClientFactory
    ): ai.agent.android.data.mcp.McpClientFactory

    /**
     * Binds the [ai.agent.android.data.repositories.MetricsRepositoryImpl] implementation to the [ai.agent.android.domain.repositories.MetricsRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindMetricsRepository(
        repository: ai.agent.android.data.repositories.MetricsRepositoryImpl
    ): ai.agent.android.domain.repositories.MetricsRepository

    /**
     * Binds the [ai.agent.android.data.repositories.PowerStateRepositoryImpl] implementation to the [ai.agent.android.domain.repositories.PowerStateRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindPowerStateRepository(
        repository: ai.agent.android.data.repositories.PowerStateRepositoryImpl
    ): ai.agent.android.domain.repositories.PowerStateRepository

    /**
     * Binds the [ai.agent.android.data.repositories.NetworkStateRepositoryImpl] implementation to the [ai.agent.android.domain.repositories.NetworkStateRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindNetworkStateRepository(
        repository: ai.agent.android.data.repositories.NetworkStateRepositoryImpl
    ): ai.agent.android.domain.repositories.NetworkStateRepository

    /**
     * Binds the [ai.agent.android.data.engine.TaskQueueManagerImpl] implementation to the [ai.agent.android.domain.engine.TaskQueueManager] interface.
     */
    @Binds
    @Singleton
    abstract fun bindTaskQueueManager(
        taskQueueManager: ai.agent.android.data.engine.TaskQueueManagerImpl
    ): ai.agent.android.domain.engine.TaskQueueManager

    /**
     * Binds the [ai.agent.android.data.repositories.LocalPipelineRepositoryImpl] implementation to the [ai.agent.android.domain.repositories.PipelineRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindPipelineRepository(
        repository: ai.agent.android.data.repositories.LocalPipelineRepositoryImpl
    ): ai.agent.android.domain.repositories.PipelineRepository

    /**
     * Binds the [ai.agent.android.data.repositories.PromptRepositoryImpl] implementation to the [ai.agent.android.domain.repositories.PromptRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindPromptRepository(
        repository: ai.agent.android.data.repositories.PromptRepositoryImpl
    ): ai.agent.android.domain.repositories.PromptRepository
}

