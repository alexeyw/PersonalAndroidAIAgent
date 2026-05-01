package ai.agent.android.di

import ai.agent.android.domain.prompt.PromptVariableProvider
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the multibinding container for [PromptVariableProvider] implementations.
 *
 * Concrete providers (e.g. `DateVariableProvider`, `ToolsVariableProvider`) are added in
 * subsequent phase-14 tasks via `@Provides @IntoSet`. Declaring the multibinding with
 * [Multibinds] here ensures that consumers can inject `Set<PromptVariableProvider>` even
 * when no providers are bound yet — the set is simply empty.
 *
 * The [ai.agent.android.domain.prompt.PromptTemplateEngine] itself is provided by Hilt
 * through its `@Inject` constructor, so no `@Provides` for the engine is required here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PromptTemplateModule {

    /**
     * Declares an (initially empty) Hilt multibinding into which every
     * [PromptVariableProvider] implementation will be contributed via
     * `@Provides @IntoSet`.
     */
    @Multibinds
    abstract fun providePromptVariableProviders(): Set<PromptVariableProvider>
}
