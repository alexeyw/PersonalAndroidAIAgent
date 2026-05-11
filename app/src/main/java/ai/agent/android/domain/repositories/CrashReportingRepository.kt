package ai.agent.android.domain.repositories

/**
 * Domain-level gateway for anonymous crash reporting.
 *
 * The project ships an on-device-first privacy posture, so every method
 * below must be a strict no-op until the user has explicitly opted in
 * through the in-app consent dialog. The opt-in flag lives in
 * [SettingsRepository.crashReportingEnabled]; the production implementation
 * reads that flag and short-circuits the entire surface to a no-op when it
 * is `false`.
 *
 * The interface intentionally has zero Android dependencies. The Firebase
 * Crashlytics integration is confined to the data-layer implementation,
 * keeping the domain layer pure Kotlin and the rest of the codebase
 * testable without the Firebase SDK on the classpath.
 */
interface CrashReportingRepository {

    /**
     * Toggles anonymous crash reporting (and the underlying Firebase
     * Analytics collection that Crashlytics requires) on or off.
     *
     * The implementation persists the choice through the Firebase SDK so it
     * survives process death even before the next [SettingsRepository] read.
     *
     * @param enabled `true` to enable Crashlytics + Analytics collection,
     *                `false` to disable it and prevent any further egress.
     */
    suspend fun setEnabled(enabled: Boolean)

    /**
     * Forwards a non-fatal [Throwable] to Crashlytics for aggregation, with
     * optional contextual key/value pairs attached as Crashlytics custom keys
     * for the duration of the report.
     *
     * When crash reporting is disabled the call must be a strict no-op —
     * the throwable is neither logged nor buffered for later upload.
     *
     * @param throwable The exception to record.
     * @param extras Optional custom keys attached to the report (e.g. the
     *               currently executing pipeline id, the active model name).
     */
    suspend fun recordException(throwable: Throwable, extras: Map<String, String> = emptyMap())

    /**
     * Attaches a Crashlytics custom key that will be included with every
     * subsequent fatal / non-fatal crash report from this process until the
     * key is overwritten.
     *
     * When crash reporting is disabled the call must be a strict no-op.
     *
     * @param key The custom-key identifier (Crashlytics-side string).
     * @param value The stringified value attached to the key.
     */
    suspend fun setCustomKey(key: String, value: String)
}
