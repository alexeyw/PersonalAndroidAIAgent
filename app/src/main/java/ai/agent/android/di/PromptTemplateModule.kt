package ai.agent.android.di

import ai.agent.android.data.prompt.DateVariableProvider
import ai.agent.android.data.prompt.DeviceVariableProvider
import ai.agent.android.data.prompt.LangVariableProvider
import ai.agent.android.data.prompt.LocationVariableProvider
import ai.agent.android.data.prompt.MemorySummaryVariableProvider
import ai.agent.android.data.prompt.ModelVariableProvider
import ai.agent.android.data.prompt.TimeVariableProvider
import ai.agent.android.data.prompt.ToolsVariableProvider
import ai.agent.android.data.prompt.UserVariableProvider
import ai.agent.android.domain.prompt.PromptVariableProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

/**
 * Declares the multibinding container for [PromptVariableProvider] implementations
 * and registers the built-in providers shipped with the app.
 *
 * Each `@Binds @IntoSet` method contributes one concrete provider into the Hilt
 * `Set<PromptVariableProvider>`. Consumers — currently [ai.agent.android.domain.engine.GraphExecutionEngine]
 * and the prompt-preview UI — inject this set and pass it to
 * [ai.agent.android.domain.prompt.PromptTemplateEngine.render] verbatim.
 *
 * The [ai.agent.android.domain.prompt.PromptTemplateEngine] itself is provided
 * by Hilt through its `@Inject` constructor, so no `@Provides` for the engine
 * is required here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PromptTemplateModule {

    /**
     * Declares the (now non-empty) Hilt multibinding into which every
     * [PromptVariableProvider] implementation is contributed via
     * `@Binds @IntoSet` below. Kept explicit so removing all `@Binds` entries
     * still leaves a usable empty set for consumers.
     */
    @Multibinds
    abstract fun providePromptVariableProviders(): Set<PromptVariableProvider>

    /**
     * Contributes [DateVariableProvider] — resolves `$DATE` to the current
     * device-local date in `dd MMMM yyyy` format.
     */
    @Binds
    @IntoSet
    abstract fun bindDateVariableProvider(impl: DateVariableProvider): PromptVariableProvider

    /**
     * Contributes [TimeVariableProvider] — resolves `$TIME` to the current
     * device-local time in `HH:mm` 24-hour format.
     */
    @Binds
    @IntoSet
    abstract fun bindTimeVariableProvider(impl: TimeVariableProvider): PromptVariableProvider

    /**
     * Contributes [ToolsVariableProvider] — resolves `$TOOLS` to a newline-
     * separated `name — description` listing of currently available tools.
     */
    @Binds
    @IntoSet
    abstract fun bindToolsVariableProvider(impl: ToolsVariableProvider): PromptVariableProvider

    /**
     * Contributes [ModelVariableProvider] — resolves `$MODEL` to the display
     * name of the currently active local model.
     */
    @Binds
    @IntoSet
    abstract fun bindModelVariableProvider(impl: ModelVariableProvider): PromptVariableProvider

    /**
     * Contributes [MemorySummaryVariableProvider] — resolves `$MEMORY_SUMMARY`
     * to a numbered list of the most recent long-term memory chunks.
     */
    @Binds
    @IntoSet
    abstract fun bindMemorySummaryVariableProvider(impl: MemorySummaryVariableProvider): PromptVariableProvider

    /**
     * Contributes [LangVariableProvider] — resolves `$LANG` to the device's
     * current BCP-47 language tag (e.g. `en-US`).
     */
    @Binds
    @IntoSet
    abstract fun bindLangVariableProvider(impl: LangVariableProvider): PromptVariableProvider

    /**
     * Contributes [LocationVariableProvider] — resolves `$LOCATION` to the
     * device's coarse region (Locale country code, e.g. `US`).
     */
    @Binds
    @IntoSet
    abstract fun bindLocationVariableProvider(impl: LocationVariableProvider): PromptVariableProvider

    /**
     * Contributes [UserVariableProvider] — resolves `$USER` to the
     * identity card's display name (currently the literal "Anonymous").
     */
    @Binds
    @IntoSet
    abstract fun bindUserVariableProvider(impl: UserVariableProvider): PromptVariableProvider

    /**
     * Contributes [DeviceVariableProvider] — resolves `$DEVICE` to a short
     * "manufacturer · model · Android version" descriptor.
     */
    @Binds
    @IntoSet
    abstract fun bindDeviceVariableProvider(impl: DeviceVariableProvider): PromptVariableProvider
}
