package ai.agent.android.domain.services

import ai.agent.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the currently active [EmbeddingProvider] from the Hilt-registered
 * provider map and the user's persisted selection.
 *
 * The memory pipeline asks this resolver for "the" embedding provider on every
 * embed/search call rather than holding a fixed provider reference, so that a
 * mid-session change to `SettingsRepository.activeEmbeddingProviderId` takes
 * effect immediately without restarting any long-lived component.
 *
 * @property providers All registered providers, keyed by [EmbeddingProvider.id]
 *   (supplied by Hilt multibinding). Always contains at least the on-device
 *   [EmbeddingProvider.ID_USE] entry.
 * @property settingsRepository Source of the active provider id.
 */
@Singleton
class EmbeddingProviderResolver @Inject constructor(
    private val providers: Map<String, @JvmSuppressWildcards EmbeddingProvider>,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Returns the provider matching the persisted active id.
     *
     * If the persisted id does not match any registered provider — e.g. a
     * provider was removed in an app update, or the stored value is stale —
     * this falls back to the always-present on-device default
     * ([EmbeddingProvider.ID_USE]) and logs a warning, rather than throwing,
     * so memory operations degrade gracefully instead of crashing.
     *
     * @return The active [EmbeddingProvider]; never null.
     */
    suspend fun resolve(): EmbeddingProvider {
        val activeId = settingsRepository.activeEmbeddingProviderId.first()
        return providers[activeId] ?: run {
            Timber.w(
                "Unknown embedding provider id '%s'; falling back to '%s'",
                activeId,
                EmbeddingProvider.ID_USE,
            )
            providers.getValue(EmbeddingProvider.ID_USE)
        }
    }
}
