package ai.agent.android.di

import ai.agent.android.data.engine.DefaultTextEmbedderFactory
import ai.agent.android.data.engine.LiteRTLlmEngine
import ai.agent.android.data.engine.MediaPipeTextEmbeddingEngine
import ai.agent.android.data.engine.TaskQueueManagerImpl
import ai.agent.android.data.engine.TextEmbedderFactory
import ai.agent.android.data.local.ApiKeyManager
import ai.agent.android.data.local.SettingsManager
import ai.agent.android.data.mcp.KoogMcpClientFactory
import ai.agent.android.data.mcp.McpClientFactory
import ai.agent.android.data.network.AndroidModelDownloadManager
import ai.agent.android.data.repositories.ChatRepositoryImpl
import ai.agent.android.data.repositories.ClarificationRepositoryImpl
import ai.agent.android.data.repositories.FirebaseCrashReportingRepositoryImpl
import ai.agent.android.data.repositories.LocalModelRepositoryImpl
import ai.agent.android.data.repositories.LocalPipelineRepositoryImpl
import ai.agent.android.data.repositories.MemoryRepositoryImpl
import ai.agent.android.data.repositories.MetricsRepositoryImpl
import ai.agent.android.data.repositories.NetworkStateRepositoryImpl
import ai.agent.android.data.repositories.PowerStateRepositoryImpl
import ai.agent.android.data.repositories.PromptRepositoryImpl
import ai.agent.android.data.repositories.ToolRepositoryImpl
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.engine.TaskQueueManager
import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.CrashReportingRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.ModelDownloadManager
import ai.agent.android.domain.repositories.NetworkStateRepository
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.PowerStateRepository
import ai.agent.android.domain.repositories.PromptRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
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
    abstract fun bindApiKeyRepository(apiKeyManager: ApiKeyManager): ApiKeyRepository

    /**
     * Binds the [LocalModelRepositoryImpl] implementation to the [LocalModelRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindLocalModelRepository(repository: LocalModelRepositoryImpl): LocalModelRepository

    /**
     * Binds the [SettingsManager] implementation to the [SettingsRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(settingsManager: SettingsManager): SettingsRepository

    /**
     * Binds the [LiteRTLlmEngine] implementation to the [LlmInferenceEngine] interface.
     */
    @Binds
    @Singleton
    abstract fun bindLlmInferenceEngine(engine: LiteRTLlmEngine): LlmInferenceEngine

    /**
     * Binds the [MediaPipeTextEmbeddingEngine] implementation to the [TextEmbeddingEngine] interface.
     */
    @Binds
    @Singleton
    abstract fun bindTextEmbeddingEngine(engine: MediaPipeTextEmbeddingEngine): TextEmbeddingEngine

    /**
     * Binds the [DefaultTextEmbedderFactory] implementation to the [TextEmbedderFactory] interface.
     */
    @Binds
    @Singleton
    abstract fun bindTextEmbedderFactory(factory: DefaultTextEmbedderFactory): TextEmbedderFactory

    /**
     * Binds the [MemoryRepositoryImpl] implementation to the [MemoryRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindMemoryRepository(repository: MemoryRepositoryImpl): MemoryRepository

    /**
     * Binds the [AndroidModelDownloadManager] implementation to the [ModelDownloadManager] interface.
     */
    @Binds
    @Singleton
    abstract fun bindModelDownloadManager(downloadManager: AndroidModelDownloadManager): ModelDownloadManager

    /**
     * Binds the [ChatRepositoryImpl] implementation to the [ChatRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindChatRepository(repository: ChatRepositoryImpl): ChatRepository

    /**
     * Binds the [ToolRepositoryImpl] implementation to the [ToolRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindToolRepository(repository: ToolRepositoryImpl): ToolRepository

    /**
     * Binds the [KoogMcpClientFactory] implementation to the [McpClientFactory] interface.
     */
    @Binds
    @Singleton
    abstract fun bindMcpClientFactory(factory: KoogMcpClientFactory): McpClientFactory

    /**
     * Binds the [MetricsRepositoryImpl] implementation to the [MetricsRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindMetricsRepository(repository: MetricsRepositoryImpl): MetricsRepository

    /**
     * Binds the [PowerStateRepositoryImpl] implementation to the [PowerStateRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindPowerStateRepository(repository: PowerStateRepositoryImpl): PowerStateRepository

    /**
     * Binds the [NetworkStateRepositoryImpl] implementation to the [NetworkStateRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindNetworkStateRepository(repository: NetworkStateRepositoryImpl): NetworkStateRepository

    /**
     * Binds the [TaskQueueManagerImpl] implementation to the [TaskQueueManager] interface.
     */
    @Binds
    @Singleton
    abstract fun bindTaskQueueManager(taskQueueManager: TaskQueueManagerImpl): TaskQueueManager

    /**
     * Binds the [LocalPipelineRepositoryImpl] implementation to the [PipelineRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindPipelineRepository(repository: LocalPipelineRepositoryImpl): PipelineRepository

    /**
     * Binds the [PromptRepositoryImpl] implementation to the [PromptRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindPromptRepository(repository: PromptRepositoryImpl): PromptRepository

    /**
     * Binds the [ClarificationRepositoryImpl] implementation to the [ClarificationRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindClarificationRepository(repository: ClarificationRepositoryImpl): ClarificationRepository

    /**
     * Binds [FirebaseCrashReportingRepositoryImpl] to [CrashReportingRepository]. The implementation
     * gates every method on [SettingsRepository.crashReportingEnabled], so the binding is safe to
     * provide unconditionally — no data leaves the device until the user opts in.
     */
    @Binds
    @Singleton
    abstract fun bindCrashReportingRepository(
        repository: FirebaseCrashReportingRepositoryImpl,
    ): CrashReportingRepository
}
