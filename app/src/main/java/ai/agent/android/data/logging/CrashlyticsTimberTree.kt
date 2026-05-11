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
     * Forwards the record to Crashlytics.
     *
     * When the caller supplied a [Throwable] (`Timber.e(t, "context %s", arg)`),
     * the exception is reported verbatim and the formatted [message] / [tag]
     * are attached as `extras` so the call-site context survives — otherwise
     * Crashlytics would only see the bare stack trace.
     *
     * Message-only records (no throwable) are wrapped in a synthetic exception
     * whose message preserves the original tag + body so Crashlytics still has
     * something to stack-trace and group on.
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t != null) {
            // Timber's base class appends `\n` + stack trace to `message` when a
            // throwable is present (Timber.Tree.prepareLog). The stack already lives on
            // the throwable itself — extract only the original call-site message before
            // the newline so the breadcrumb stays readable.
            val callSiteMessage = message.substringBefore('\n')
            val extras = buildMap {
                put(EXTRA_MESSAGE, callSiteMessage)
                if (!tag.isNullOrBlank()) put(EXTRA_TAG, tag)
            }
            scope.launch {
                crashReportingRepository.recordException(t, extras)
            }
            return
        }
        val synthetic = SyntheticLogException(
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
            crashReportingRepository.recordException(synthetic)
        }
    }

    /**
     * Synthetic exception used when a Timber message has no underlying
     * [Throwable]. Kept as a named subclass so Crashlytics groups
     * message-only events together rather than mixing them with real bugs.
     */
    private class SyntheticLogException(message: String) : Exception(message)

    private companion object {
        /** Extras key for the Timber call-site message attached to a throwable. */
        const val EXTRA_MESSAGE = "timber_message"

        /** Extras key for the Timber call-site tag attached to a throwable. */
        const val EXTRA_TAG = "timber_tag"
    }
}
