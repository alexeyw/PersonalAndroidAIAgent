package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.ModelDiscoveryConstants
import app.knotwork.android.domain.models.DiscoverableModelSummary
import app.knotwork.android.domain.repositories.ModelDiscoveryRepository
import javax.inject.Inject

/**
 * Lists or searches discoverable on-device models on the Hugging Face Hub.
 *
 * A thin pass-through over [ModelDiscoveryRepository] that fixes the default
 * page size, so the ViewModel does not need to know the limit. Returns a
 * [Result] so the caller renders an error state on failure.
 *
 * @property repository source of discoverable model metadata.
 */
class SearchDiscoverableModelsUseCase @Inject constructor(private val repository: ModelDiscoveryRepository) {

    /**
     * @param query optional free-text filter; `null`/blank lists the most
     *   downloaded compatible repositories.
     * @param limit maximum repositories to request (defaults to
     *   [ModelDiscoveryConstants.DEFAULT_SEARCH_LIMIT]).
     * @return [Result] with the compatible list or the network/parse error.
     */
    suspend operator fun invoke(
        query: String?,
        limit: Int = ModelDiscoveryConstants.DEFAULT_SEARCH_LIMIT,
    ): Result<List<DiscoverableModelSummary>> =
        repository.searchModels(query = query?.takeIf { it.isNotBlank() }, limit = limit)
}
