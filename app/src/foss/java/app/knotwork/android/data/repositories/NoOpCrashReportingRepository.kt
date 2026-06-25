package app.knotwork.android.data.repositories

import app.knotwork.android.domain.repositories.CrashReportingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op [CrashReportingRepository] for the `foss` (F-Droid) distribution.
 *
 * The F-Droid channel rejects proprietary dependencies, so the `foss` flavour
 * ships with no Firebase/Crashlytics/Analytics on its classpath. This
 * implementation satisfies the domain contract while doing precisely nothing:
 * every method is a strict no-op, so no crash data is ever collected, buffered,
 * or transmitted. There is no network path here at all.
 *
 * The companion `full` flavour binds `FirebaseCrashReportingRepositoryImpl`
 * instead (under `src/full`). The user-facing crash-reporting consent toggle is
 * hidden in `foss` builds (driven by `BuildConfig.CRASH_REPORTING_AVAILABLE`),
 * so this no-op is never reachable from a visible control — it exists only to
 * keep the shared `main` call sites (e.g. `App`, `GraphExecutionEngine`) wired
 * against a single interface regardless of flavour.
 */
@Singleton
class NoOpCrashReportingRepository @Inject constructor() : CrashReportingRepository {

    /** No-op: the `foss` build has no crash collector to toggle. */
    override suspend fun setEnabled(enabled: Boolean) = Unit

    /** No-op: exceptions are neither recorded nor buffered for later upload. */
    override suspend fun recordException(throwable: Throwable, extras: Map<String, String>) = Unit

    /** No-op: there is no crash report to attach custom keys to. */
    override suspend fun setCustomKey(key: String, value: String) = Unit
}
