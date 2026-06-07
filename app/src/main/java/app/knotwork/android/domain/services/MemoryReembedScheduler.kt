package app.knotwork.android.domain.services

/**
 * Domain seam for scheduling a background re-embedding pass over the memory
 * chunks flagged `needsReembedding` (those imported under a different embedding
 * provider).
 *
 * Keeping this an interface lets the domain ([app.knotwork.android.domain.usecases.MemoryImportUseCase])
 * request the repair without depending on Android / WorkManager. The data-layer
 * implementation enqueues a `WorkManager` job that runs
 * [app.knotwork.android.domain.usecases.RecomputePendingEmbeddingsUseCase] off the
 * hot path, with the framework's own retry/backoff — so a large or
 * temporarily-failing re-embed never blocks a retrieval.
 */
interface MemoryReembedScheduler {

    /**
     * Enqueues a one-off background re-embedding pass. Idempotent: implementations
     * coalesce so a pass already queued (or running) is not duplicated.
     */
    fun schedule()

    /**
     * Re-arms the re-embed pass iff chunks are still flagged `needsReembedding` —
     * a self-heal for a one-off pass that was lost (process killed before
     * WorkManager persisted the enqueue) or exhausted its retries.
     *
     * Owning this check here (rather than in a single UI entry point) keeps
     * recovery independent of which surface — `MainActivity`, the foreground
     * service, … — happens to start the process. A no-op (one cheap `COUNT`,
     * nothing enqueued) when nothing is pending.
     */
    suspend fun rearmIfPending()
}
