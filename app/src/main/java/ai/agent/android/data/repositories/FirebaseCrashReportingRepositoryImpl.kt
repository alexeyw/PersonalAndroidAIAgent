package ai.agent.android.data.repositories

import ai.agent.android.domain.repositories.CrashReportingRepository
import ai.agent.android.domain.repositories.SettingsRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-backed implementation of [CrashReportingRepository].
 *
 * Every method reads the current value of
 * [SettingsRepository.crashReportingEnabled] before touching the Firebase
 * SDK; when the flag is `false` the call short-circuits to a no-op so no
 * data leaves the device. This matches the project's opt-in privacy
 * contract: the manifest disables auto-collection at boot, and the runtime
 * gate here guarantees we cannot accidentally upload anything from code
 * paths that were authored before the user opted in.
 *
 * The implementation also toggles `FirebaseAnalytics.setAnalyticsCollectionEnabled`
 * alongside Crashlytics, because Crashlytics requires Analytics and the
 * consent dialog covers both data flows.
 *
 * @property settingsRepository Single source of truth for the user's
 *                              opt-in flag.
 * @property crashlytics Firebase Crashlytics singleton (injected for tests).
 * @property analytics Firebase Analytics singleton (injected for tests).
 */
@Singleton
class FirebaseCrashReportingRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val crashlytics: FirebaseCrashlytics,
    private val analytics: FirebaseAnalytics,
) : CrashReportingRepository {

    override suspend fun setEnabled(enabled: Boolean) {
        runCatching {
            crashlytics.isCrashlyticsCollectionEnabled = enabled
            analytics.setAnalyticsCollectionEnabled(enabled)
        }.onFailure { error ->
            Timber.e(error, "Failed to toggle Crashlytics collection to $enabled")
        }
    }

    override suspend fun recordException(throwable: Throwable, extras: Map<String, String>) {
        if (!settingsRepository.crashReportingEnabled.first()) return
        runCatching {
            extras.forEach { (key, value) -> crashlytics.setCustomKey(key, value) }
            crashlytics.recordException(throwable)
        }.onFailure { error ->
            Timber.e(error, "Failed to record exception to Crashlytics")
        }
    }

    override suspend fun setCustomKey(key: String, value: String) {
        if (!settingsRepository.crashReportingEnabled.first()) return
        runCatching {
            crashlytics.setCustomKey(key, value)
        }.onFailure { error ->
            Timber.e(error, "Failed to set Crashlytics custom key $key")
        }
    }
}
