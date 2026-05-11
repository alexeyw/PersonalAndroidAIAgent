package ai.agent.android.data.logging

import ai.agent.android.domain.repositories.CrashReportingRepository
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Timber tree that funnels `Log.WARN` / `Log.ERROR` entries into
 * [CrashReportingRepository] so they show up in Crashlytics alongside
 * fatal crashes. Lower priorities (`VERBOSE`, `DEBUG`, `INFO`) are
 * dropped to keep the upload budget tight and to avoid leaking routine
 * agent traces (prompts, tool inputs, etc.) into the cloud.
 *
 * The tree is only planted in release builds *after* the user opts in to
 * crash reporting (see [ai.agent.android.App]). The repository itself
 * additionally short-circuits when the opt-in flag is `false`, providing
 * a belt-and-braces guarantee that nothing ever leaves the device while
 * collection is disabled.
 *
 * Crashlytics calls are dispatched via the supplied [CoroutineScope]
 * because the repository methods are `suspend` (they read the persisted
 * opt-in flag from DataStore). The scope is application-lifetime, so the
 * launched job survives the calling thread.
 *
 * @property crashReportingRepository Sink that forwards records to Crashlytics.
 * @property scope Application-scoped coroutine scope used to bridge the
 *                 synchronous Timber callback to the `suspend` repository API.
 */
class CrashlyticsTimberTree(
    private val crashReportingRepository: CrashReportingRepository,
    private val scope: CoroutineScope,
) : Timber.Tree() {

    /**
     * Allows `WARN` and `ERROR` records to pass through this tree.
     * The repository-level opt-in check still applies; this method only
     * filters out low-severity noise before it reaches [log].
     */
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    /**
     * Forwards the record to Crashlytics. Messages without an attached
     * [Throwable] are wrapped in a synthetic exception whose message
     * preserves the log tag + body so Crashlytics still has something to
     * stack-trace and group on.
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val throwable = t ?: SyntheticLogException(
            buildString {
                if (!tag.isNullOrBlank()) {
                    append("[")
                    append(tag)
                    append("] ")
                }
                append(message)
            },
        )
        scope.launch {
            crashReportingRepository.recordException(throwable)
        }
    }

    /**
     * Synthetic exception used when a Timber message has no underlying
     * [Throwable]. Kept as a named subclass so Crashlytics groups
     * message-only events together rather than mixing them with real bugs.
     */
    private class SyntheticLogException(message: String) : Exception(message)
}
