package app.knotwork.android.di

import app.knotwork.android.data.engine.DefaultTextEmbedderFactory
import app.knotwork.android.data.engine.LiteRTLlmEngine
import app.knotwork.android.data.engine.MediaPipeTextEmbeddingEngine
import app.knotwork.android.data.engine.TaskQueueManagerImpl
import app.knotwork.android.data.engine.TextEmbedderFactory
import app.knotwork.android.data.local.ApiKeyManager
import app.knotwork.android.data.local.DatabaseResetServiceImpl
import app.knotwork.android.data.local.SettingsManager
import app.knotwork.android.data.local.crypto.AeadCipher
import app.knotwork.android.data.local.crypto.AndroidKeystoreAeadCipher
import app.knotwork.android.data.mcp.KoogMcpClientFactory
import app.knotwork.android.data.mcp.McpClientFactory
import app.knotwork.android.data.network.AndroidModelDownloadManager
import app.knotwork.android.data.repositories.ChatRepositoryImpl
import app.knotwork.android.data.repositories.ClarificationRepositoryImpl
import app.knotwork.android.data.repositories.FirebaseCrashReportingRepositoryImpl
import app.knotwork.android.data.repositories.IdentityRepositoryImpl
import app.knotwork.android.data.repositories.LocalModelRepositoryImpl
import app.knotwork.android.data.repositories.LocalPipelinePresetRepositoryImpl
import app.knotwork.android.data.repositories.LocalPipelineRepositoryImpl
import app.knotwork.android.data.repositories.LocalPromptPresetRepositoryImpl
import app.knotwork.android.data.repositories.McpServerRepositoryImpl
import app.knotwork.android.data.repositories.MemoryRepositoryImpl
import app.knotwork.android.data.repositories.MetricsRepositoryImpl
import app.knotwork.android.data.repositories.NetworkActivityTrackerImpl
import app.knotwork.android.data.repositories.NetworkStateRepositoryImpl
import app.knotwork.android.data.repositories.PipelineRunRepositoryImpl
import app.knotwork.android.data.repositories.PowerStateRepositoryImpl
import app.knotwork.android.data.repositories.PromptRepositoryImpl
import app.knotwork.android.data.repositories.RunTraceRepositoryImpl
import app.knotwork.android.data.repositories.ToolRepositoryImpl
import app.knotwork.android.data.services.LongRunningTaskNotifierImpl
import app.knotwork.android.data.services.WorkManagerMemoryReembedScheduler
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.engine.TextEmbeddingEngine
import app.knotwork.android.domain.repositories.ApiKeyRepository
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.ClarificationRepository
import app.knotwork.android.domain.repositories.CrashReportingRepository
import app.knotwork.android.domain.repositories.IdentityRepository
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.McpServerRepository
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.MetricsRepository
import app.knotwork.android.domain.repositories.ModelDownloadManager
import app.knotwork.android.domain.repositories.NetworkActivityTracker
import app.knotwork.android.domain.repositories.NetworkStateRepository
import app.knotwork.android.domain.repositories.PipelinePresetRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.PowerStateRepository
import app.knotwork.android.domain.repositories.PromptPresetRepository
import app.knotwork.android.domain.repositories.PromptRepository
import app.knotwork.android.domain.repositories.RunTraceRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.services.DatabaseResetService
import app.knotwork.android.domain.services.LongRunningTaskNotifier
import app.knotwork.android.domain.services.MemoryReembedScheduler
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
@Suppress("TooManyFunctions") // Binding-only module: one @Binds per data-layer implementation.
abstract class DataModule {

    /**
     * Binds the Android-Keystore-backed [AeadCipher] implementation used by the secret
     * stores ([ApiKeyManager], [app.knotwork.android.data.local.EncryptedDbPassphraseProvider]).
     */
    @Binds
    @Singleton
    abstract fun bindAeadCipher(cipher: AndroidKeystoreAeadCipher): AeadCipher

    /**
     * Binds the [ApiKeyManager] implementation to the [ApiKeyRepository] interface.
     */
    @Binds
    @Singleton
    abstract fun bindApiKeyRepository(apiKeyManager: ApiKeyManager): ApiKeyRepository

    /**
     * Binds the [DatabaseResetServiceImpl] implementation to the [DatabaseResetService] interface.
     */
    @Binds
    @Singleton
    abstract fun bindDatabaseResetService(service: DatabaseResetServiceImpl): DatabaseResetService

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
     * Binds the [PipelineRunRepositoryImpl] implementation to the
     * [PipelineRunRepository] interface backing the persistent
     * pipeline-run records.
     */
    @Binds
    @Singleton
    abstract fun bindPipelineRunRepository(repository: PipelineRunRepositoryImpl): PipelineRunRepository

    /**
     * Binds the [RunTraceRepositoryImpl] implementation to the
     * [RunTraceRepository] interface backing the buffered persistent
     * pipeline-run trace.
     */
    @Binds
    @Singleton
    abstract fun bindRunTraceRepository(repository: RunTraceRepositoryImpl): RunTraceRepository

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
     * Binds [McpServerRepositoryImpl] to [McpServerRepository] — owns per-server
     * MCP connections, tool-list caching, and the connection-status flows
     * consumed by `ToolsViewModel`.
     */
    @Binds
    @Singleton
    abstract fun bindMcpServerRepository(repository: McpServerRepositoryImpl): McpServerRepository

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
     * Binds [NetworkActivityTrackerImpl] to [NetworkActivityTracker]. Records every outbound
     * cloud-LLM and MCP call so the More tab can render the "no network calls in last N m"
     * privacy indicator.
     */
    @Binds
    @Singleton
    abstract fun bindNetworkActivityTracker(tracker: NetworkActivityTrackerImpl): NetworkActivityTracker

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
     * Binds [LocalPipelinePresetRepositoryImpl] to [PipelinePresetRepository] — composes the
     * bundled assets/presets/pipelines catalogue with the user-saved Room rows.
     */
    @Binds
    @Singleton
    abstract fun bindPipelinePresetRepository(repository: LocalPipelinePresetRepositoryImpl): PipelinePresetRepository

    /**
     * Binds [LocalPromptPresetRepositoryImpl] to [PromptPresetRepository] — composes the
     * bundled assets/presets/prompts catalogue with the user-saved Room rows.
     */
    @Binds
    @Singleton
    abstract fun bindPromptPresetRepository(repository: LocalPromptPresetRepositoryImpl): PromptPresetRepository

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

    /**
     * Binds [IdentityRepositoryImpl] to [IdentityRepository]. Surfaces the
     * Settings identity card snapshot (device-id + Keystore probe).
     */
    @Binds
    @Singleton
    abstract fun bindIdentityRepository(repository: IdentityRepositoryImpl): IdentityRepository

    /**
     * Binds [LongRunningTaskNotifierImpl] to [LongRunningTaskNotifier].
     * The implementation gates every `notify` call on the user's
     * `Long-running tasks` toggle so the binding is safe to provide
     * unconditionally.
     */
    @Binds
    @Singleton
    abstract fun bindLongRunningTaskNotifier(notifier: LongRunningTaskNotifierImpl): LongRunningTaskNotifier

    /**
     * Binds [WorkManagerMemoryReembedScheduler] to [MemoryReembedScheduler] —
     * enqueues the background re-embed pass that repairs chunks imported under a
     * different embedding provider.
     */
    @Binds
    @Singleton
    abstract fun bindMemoryReembedScheduler(scheduler: WorkManagerMemoryReembedScheduler): MemoryReembedScheduler
}
