package app.knotwork.android.domain.models

/**
 * One step in the application's cold-start initialization sequence, surfaced
 * by `AppInitializationUseCase` to the splash screen so the user can see
 * which heavy resource is being loaded right now.
 *
 * Stages are ordered: each non-terminal stage represents work currently in
 * progress; [Done] is emitted exactly once after every step succeeds; [Failed]
 * carries the underlying cause and the stage that broke so the UI can offer a
 * retry button instead of silently freezing.
 */
sealed interface InitStage {
    /** Application-level setup — first-launch defaults, prompt seeding. */
    data object Initializing : InitStage

    /** LiteRT-LM weights are being loaded into memory. */
    data object LoadingModel : InitStage

    /** Pipelines are being prefetched from Room into the in-memory cache. */
    data object LoadingPipelines : InitStage

    /** Chat sessions are being prefetched from Room. */
    data object LoadingChats : InitStage

    /** Long-term memory summaries are being prefetched. */
    data object LoadingMemory : InitStage

    /** All steps succeeded; the splash screen can hand off to the chat UI. */
    data object Done : InitStage

    /**
     * A non-recoverable error stopped initialization. The UI shows [cause] and
     * a retry button that re-runs the entire pipeline.
     *
     * @property cause Human-readable failure message.
     * @property failedStage The stage that failed, useful for diagnostics.
     */
    data class Failed(val cause: String, val failedStage: InitStage) : InitStage
}

/**
 * Snapshot of initialization progress emitted by `AppInitializationUseCase`
 * on every stage transition. The pair `(completedSteps, totalSteps)` lets
 * the UI render a determinate `LinearProgressIndicator`.
 *
 * @property stage Current [InitStage].
 * @property message Human-readable label rendered under the progress bar
 *   (e.g. "Loading on-device model…").
 * @property completedSteps Number of stages already finished — `0` when
 *   the very first stage is in flight, `totalSteps` once [InitStage.Done]
 *   is emitted.
 * @property totalSteps Total number of stages, fixed for the duration of a
 *   single initialization attempt. Renderer uses this as the denominator.
 */
data class InitProgress(val stage: InitStage, val message: String, val completedSteps: Int, val totalSteps: Int)
