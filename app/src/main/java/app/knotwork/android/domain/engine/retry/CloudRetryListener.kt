package app.knotwork.android.domain.engine.retry

/**
 * Observability seam invoked once per **retry** of a transient cloud-call
 * failure (HTTP 429 / 5xx / connection or read timeout) before the retry is
 * actually attempted.
 *
 * Koog's `RetryingLLMClient` only logs retries through its own logger and
 * exposes no per-attempt callback, so the data layer wraps the real client in a
 * thin counting decorator that reports each re-invocation through this listener.
 * The cloud node executor supplies an implementation that surfaces a
 * [app.knotwork.android.domain.models.ConsoleEventType.CloudRetry] console line
 * (symmetric to the structured-output repair line). Pass [NONE] when no
 * observability is desired (off-graph embeddings, the delegate-task tool, unit
 * tests of unrelated behaviour).
 *
 * The initial call is **not** a retry: the listener fires only for the 2nd and
 * later delegate invocations, so [attempt] counts retries (1-based), not total
 * attempts.
 */
fun interface CloudRetryListener {

    /**
     * Called once per retry, before the corrective cloud call runs.
     *
     * @param provider Display id of the cloud provider being retried (e.g.
     *   `"openai"`), used to compose the user-visible console line.
     * @param attempt 1-based index of this retry (retry 1 is the first re-try
     *   after the initial call failed).
     * @param maxRetries The configured retry ceiling (attempt budget minus the
     *   initial call), so the listener can render the `<attempt>/<maxRetries>`
     *   progress fraction.
     */
    fun onRetry(provider: String, attempt: Int, maxRetries: Int)

    /** Shared no-op instance. */
    companion object {
        /** No-op listener for callers that do not need retry observability. */
        val NONE: CloudRetryListener = CloudRetryListener { _, _, _ -> }
    }
}
