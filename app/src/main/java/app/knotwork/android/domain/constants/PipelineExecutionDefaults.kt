package app.knotwork.android.domain.constants

/**
 * Engine-level timing and log-size constants consumed by
 * [app.knotwork.android.domain.engine.GraphExecutionEngine] and the
 * `*NodeExecutor` implementations.
 *
 * These values are intentionally **not** user-tunable: they encode
 * implementation-side trade-offs between UI responsiveness and resource use
 * (e.g. giving the LiteRT runtime a brief moment to release native buffers
 * before the next node starts inference, or capping how much text a node
 * dumps into the agent log to keep `Timber` callsites under control).
 */
object PipelineExecutionDefaults {
    /**
     * Delay inserted by an LLM-backed `NodeExecutor` between emitting its
     * `Result` and resuming the engine's main loop, in milliseconds. The
     * pause lets the orchestrator coroutine flush the result to the UI
     * before the next node starts contending for CPU/GPU.
     */
    const val NODE_RESULT_EMIT_DELAY_MS: Long = 1_000L

    /**
     * Delay inserted before a CPU-/GPU-heavy LiteRT inference call, in
     * milliseconds. Prevents the upstream node's final UI emission from
     * being starved by the inference workload on lower-end devices.
     */
    const val LITE_RT_PREWARM_DELAY_MS: Long = 500L

    /**
     * Maximum number of characters of a node's input or output that is
     * mirrored into the structured `Timber` log. Larger payloads are
     * truncated with a `…` suffix to keep logcat readable.
     */
    const val NODE_IO_LOG_CHAR_LIMIT: Int = 1_000
}
