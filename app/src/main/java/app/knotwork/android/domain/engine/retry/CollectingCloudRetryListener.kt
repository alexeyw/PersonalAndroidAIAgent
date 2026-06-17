package app.knotwork.android.domain.engine.retry

/**
 * [CloudRetryListener] that buffers every retry instead of acting on it
 * immediately.
 *
 * Retries happen deep inside the cloud client's suspend call, but a node
 * executor can only surface a console line by `emit`-ing a
 * [app.knotwork.android.domain.models.NodeOutput.Console] (a suspend operation)
 * into its flow. This listener bridges the two: it records each retry while the
 * cloud call runs, and the executor drains [attempts] afterwards — emitting one
 * console line per retry. Mirrors
 * [app.knotwork.android.domain.engine.structured.CollectingRepairListener].
 */
class CollectingCloudRetryListener : CloudRetryListener {

    private val buffer = mutableListOf<RetryAttempt>()

    /** The retries recorded so far, in occurrence order. */
    val attempts: List<RetryAttempt> get() = buffer.toList()

    override fun onRetry(provider: String, attempt: Int, maxRetries: Int) {
        buffer += RetryAttempt(provider = provider, attempt = attempt, maxRetries = maxRetries)
    }

    /**
     * One buffered retry.
     *
     * @property provider Display id of the retried cloud provider.
     * @property attempt 1-based index of this retry.
     * @property maxRetries Configured retry ceiling, for the progress fraction.
     */
    data class RetryAttempt(val provider: String, val attempt: Int, val maxRetries: Int) {
        /**
         * The user-visible console line for this retry, e.g.
         * `"Cloud retry 1/2 for openai"`.
         */
        fun consoleMessage(): String = "Cloud retry $attempt/$maxRetries for $provider"
    }
}
