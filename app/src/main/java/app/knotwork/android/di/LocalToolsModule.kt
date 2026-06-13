package app.knotwork.android.di

import app.knotwork.android.data.engine.KoogClientFactory
import app.knotwork.android.data.engine.KoogCloudLlmModelResolver
import app.knotwork.android.data.tools.local.SearchTool
import app.knotwork.android.data.tools.local.executors.DelegateTaskExecutor
import app.knotwork.android.data.tools.local.executors.FindFilesExecutor
import app.knotwork.android.data.tools.local.executors.ListFilesExecutor
import app.knotwork.android.data.tools.local.executors.ReadFileExecutor
import app.knotwork.android.data.tools.local.executors.ScheduleTaskExecutor
import app.knotwork.android.data.tools.local.executors.SearchToolExecutor
import app.knotwork.android.domain.engine.CloudLlmClientFactory
import app.knotwork.android.domain.engine.CloudLlmModelResolver
import app.knotwork.android.domain.repositories.LocalToolExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

/**
 * DI module wiring the [LocalToolExecutor] multibinding map and the cloud LLM
 * abstractions used by `CloudLlmNodeExecutor` (`CloudLlmClientFactory`,
 * `CloudLlmModelResolver`).
 *
 * Each built-in tool registers its implementation under its tool name; consumers inject
 * `Map<String, LocalToolExecutor>` and dispatch by name. New built-ins are added by
 * appending another `@Binds @IntoMap @StringKey(...)` line — no edits to
 * `ToolRepositoryImpl`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocalToolsModule {

    @Binds
    @IntoMap
    @StringKey(ScheduleTaskExecutor.TOOL_NAME)
    abstract fun bindScheduleTaskExecutor(executor: ScheduleTaskExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(DelegateTaskExecutor.TOOL_NAME)
    abstract fun bindDelegateTaskExecutor(executor: DelegateTaskExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(SearchTool.TOOL_NAME)
    abstract fun bindSearchToolExecutor(executor: SearchToolExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(ReadFileExecutor.TOOL_NAME)
    abstract fun bindReadFileExecutor(executor: ReadFileExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(ListFilesExecutor.TOOL_NAME)
    abstract fun bindListFilesExecutor(executor: ListFilesExecutor): LocalToolExecutor

    @Binds
    @IntoMap
    @StringKey(FindFilesExecutor.TOOL_NAME)
    abstract fun bindFindFilesExecutor(executor: FindFilesExecutor): LocalToolExecutor

    /**
     * Domain-level binding for the cloud client factory. The data-layer impl
     * [KoogClientFactory] keeps its public per-provider helpers for `DelegateTaskTool`
     * while exposing the provider-keyed [CloudLlmClientFactory.createClient] entry-point
     * to domain consumers.
     */
    @Binds
    @Singleton
    abstract fun bindCloudLlmClientFactory(impl: KoogClientFactory): CloudLlmClientFactory

    /**
     * Domain-level binding for the cloud model resolver.
     */
    @Binds
    @Singleton
    abstract fun bindCloudLlmModelResolver(impl: KoogCloudLlmModelResolver): CloudLlmModelResolver
}
