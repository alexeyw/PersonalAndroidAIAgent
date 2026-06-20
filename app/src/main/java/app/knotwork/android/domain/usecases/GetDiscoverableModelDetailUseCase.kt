package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.DiscoverableModelDetail
import app.knotwork.android.domain.repositories.ModelDiscoveryRepository
import javax.inject.Inject

/**
 * Fetches the full detail of a single discoverable model (its `.litertlm`
 * files, sizes, installed flags and model-card link) when the user opens a
 * Discover card.
 *
 * @property repository source of discoverable model metadata.
 */
class GetDiscoverableModelDetailUseCase @Inject constructor(private val repository: ModelDiscoveryRepository) {

    /**
     * @param repoId fully-qualified repository id (`"author/name"`).
     * @return [Result] with the detail or the network/parse error.
     */
    suspend operator fun invoke(repoId: String): Result<DiscoverableModelDetail> = repository.getModelDetail(repoId)
}
