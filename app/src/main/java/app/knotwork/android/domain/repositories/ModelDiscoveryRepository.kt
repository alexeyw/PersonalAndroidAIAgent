package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.DiscoverableModelDetail
import app.knotwork.android.domain.models.DiscoverableModelSummary

/**
 * Repository for discovering on-device LLM models published on the Hugging
 * Face Hub (the curated `litert-community` organisation). Read-only: it never
 * mutates the Hub and issues a network call only in direct response to a user
 * action (open the Discover screen, search, open a card). Installation reuses
 * the existing [ModelDownloadManager]; this repository only surfaces what is
 * available.
 *
 * Both methods return a [Result] rather than throwing so the presentation
 * layer can render a graceful error state with a retry action instead of
 * crashing on a flaky network.
 */
interface ModelDiscoveryRepository {

    /**
     * Lists (or searches) compatible models in the curated organisation.
     * Results are restricted to repositories that publish at least one
     * engine-compatible `.litertlm` file.
     *
     * @param query optional free-text filter applied by the Hub; `null` or
     *   blank lists the most-downloaded repositories.
     * @param limit maximum number of repositories to request.
     * @return [Result.success] with the (possibly empty) compatible list, or
     *   [Result.failure] carrying the network/parse error.
     */
    suspend fun searchModels(query: String?, limit: Int): Result<List<DiscoverableModelSummary>>

    /**
     * Fetches the full detail of a single repository, including its
     * `.litertlm` files with sizes and per-file "already installed" flags.
     *
     * @param repoId fully-qualified repository id (`"author/name"`).
     * @return [Result.success] with the detail, or [Result.failure] carrying
     *   the network/parse error.
     */
    suspend fun getModelDetail(repoId: String): Result<DiscoverableModelDetail>
}
