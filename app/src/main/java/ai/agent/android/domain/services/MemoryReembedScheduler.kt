package ai.agent.android.domain.services

/**
 * Domain seam for scheduling a background re-embedding pass over the memory
 * chunks flagged `needsReembedding` (those imported under a different embedding
 * provider).
 *
 * Keeping this an interface lets the domain ([ai.agent.android.domain.usecases.MemoryImportUseCase])
 * request the repair without depending on Android / WorkManager. The data-layer
 * implementation enqueues a `WorkManager` job that runs
 * [ai.agent.android.domain.usecases.RecomputePendingEmbeddingsUseCase] off the
 * hot path, with the framework's own retry/backoff — so a large or
 * temporarily-failing re-embed never blocks a retrieval.
 */
interface MemoryReembedScheduler {

    /**
     * Enqueues a one-off background re-embedding pass. Idempotent: implementations
     * coalesce so a pass already queued (or running) is not duplicated.
     */
    fun schedule()
}
