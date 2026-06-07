package app.knotwork.android.di

import app.knotwork.android.data.services.embedding.CloudEmbeddingProvider
import app.knotwork.android.data.services.embedding.DefaultKoogEmbedderFactory
import app.knotwork.android.data.services.embedding.KoogEmbedderFactory
import app.knotwork.android.data.services.embedding.OllamaEmbeddingProvider
import app.knotwork.android.data.services.embedding.UseEmbeddingProvider
import app.knotwork.android.domain.services.EmbeddingProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

/**
 * DI module wiring the [EmbeddingProvider] multibinding map and the
 * [KoogEmbedderFactory] seam.
 *
 * Each provider registers under its stable [EmbeddingProvider.id]; consumers
 * inject `Map<String, EmbeddingProvider>` (resolved at call time by
 * `EmbeddingProviderResolver`). A new provider is added by appending one
 * `@Binds @IntoMap @StringKey(...)` line — no edits to the resolver.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EmbeddingModule {

    /** On-device Universal Sentence Encoder — the default provider. */
    @Binds
    @IntoMap
    @StringKey(EmbeddingProvider.ID_USE)
    abstract fun bindUseEmbeddingProvider(provider: UseEmbeddingProvider): EmbeddingProvider

    /** OpenAI `text-embedding-3-small` (cloud). */
    @Binds
    @IntoMap
    @StringKey(EmbeddingProvider.ID_OPENAI_3_SMALL)
    abstract fun bindCloudEmbeddingProvider(provider: CloudEmbeddingProvider): EmbeddingProvider

    /** Ollama `nomic-embed-text` (local network). */
    @Binds
    @IntoMap
    @StringKey(EmbeddingProvider.ID_OLLAMA)
    abstract fun bindOllamaEmbeddingProvider(provider: OllamaEmbeddingProvider): EmbeddingProvider

    /** Binds the real Koog-backed embedder factory. */
    @Binds
    @Singleton
    abstract fun bindKoogEmbedderFactory(factory: DefaultKoogEmbedderFactory): KoogEmbedderFactory
}
