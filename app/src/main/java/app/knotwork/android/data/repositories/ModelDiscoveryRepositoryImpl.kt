package app.knotwork.android.data.repositories

import app.knotwork.android.data.mappers.HuggingFaceModelMapper.toDetail
import app.knotwork.android.data.mappers.HuggingFaceModelMapper.toSummary
import app.knotwork.android.data.network.huggingface.HuggingFaceModelApi
import app.knotwork.android.domain.constants.ModelDiscoveryConstants
import app.knotwork.android.domain.models.DiscoverableModelDetail
import app.knotwork.android.domain.models.DiscoverableModelSummary
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.ModelDiscoveryRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * [ModelDiscoveryRepository] backed by the read-only [HuggingFaceModelApi].
 *
 * Maps Hub DTOs to domain models through the pure [HuggingFaceModelMapper] and
 * enriches the detail with local installed-state from [LocalModelRepository].
 * Both methods convert success/failure into a [Result]; the suspend network
 * calls are guarded by a dedicated `CancellationException` re-throw before the
 * generic mapping (never `runCatching` — the cancellation detekt gate forbids
 * wrapping suspend calls).
 *
 * @property api read-only Hugging Face Hub client.
 * @property localModelRepository source of "already installed" file names.
 */
class ModelDiscoveryRepositoryImpl @Inject constructor(
    private val api: HuggingFaceModelApi,
    private val localModelRepository: LocalModelRepository,
) : ModelDiscoveryRepository {

    override suspend fun searchModels(query: String?, limit: Int): Result<List<DiscoverableModelSummary>> = try {
        val compatible = api.listModels(query = query, limit = limit)
            .map { it.toSummary() }
            // Only surface repositories that actually ship an engine-loadable
            // `.litertlm` bundle — a repo of, say, only tokenizer assets is noise.
            .filter { it.litertFileCount > 0 }
        Result.success(compatible)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Failed to list discoverable models (query=%s)", query)
        Result.failure(e)
    }

    override suspend fun getModelDetail(repoId: String): Result<DiscoverableModelDetail> = try {
        val dto = api.getModel(repoId)
        val installedNames = dto.siblings
            .map { it.rfilename }
            .filter { it.endsWith(suffix = ModelDiscoveryConstants.LITERTLM_EXTENSION, ignoreCase = true) }
            .filter { localModelRepository.isInstalled(it) }
            .toSet()
        Result.success(dto.toDetail(installedFileNames = installedNames))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Failed to fetch discoverable model detail (repoId=%s)", repoId)
        Result.failure(e)
    }
}
