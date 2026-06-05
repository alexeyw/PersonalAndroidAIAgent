package ai.agent.android.data.repositories

import ai.agent.android.domain.models.Identity
import ai.agent.android.domain.repositories.IdentityRepository
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [IdentityRepository] implementation backed by
 * `Settings.Secure.ANDROID_ID` for the device fingerprint and a synchronous
 * probe of the `AndroidKeyStore` provider for the keystore-availability flag.
 *
 * The fingerprint is intentionally truncated to 8 hex characters formatted
 * as `XXXX-XXXX`. Two reasons: the full 16-character ANDROID_ID adds noise
 * to a UI label that is only meant to confirm "yes this is your device".
 * ANDROID_ID itself is already scoped per (app-signing-key, user, device)
 * on modern Android, so
 * we are NOT leaking a stable cross-app identifier.
 */
@Singleton
class IdentityRepositoryImpl @Inject constructor(@ApplicationContext private val context: Context) :
    IdentityRepository {

    override suspend fun getIdentity(anonymousLabel: String): Identity = withContext(Dispatchers.IO) {
        val rawId = readAndroidId()
        Identity(
            displayName = anonymousLabel,
            deviceId = formatDeviceId(rawId),
            keystoreAvailable = isAndroidKeystoreAvailable(),
        )
    }

    @Suppress("HardwareIds") // ANDROID_ID is per signing-key + user + device on modern Android.
    private fun readAndroidId(): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.onFailure { Timber.w(it, "Failed to read ANDROID_ID — falling back to placeholder") }
        .getOrNull()

    private fun isAndroidKeystoreAvailable(): Boolean = runCatching {
        KeyStore.getInstance(ANDROID_KEYSTORE).load(null)
        true
    }.onFailure { Timber.w(it, "AndroidKeyStore unavailable") }
        .getOrDefault(false)

    /**
     * Truncates ANDROID_ID to 8 hex characters formatted as `XXXX-XXXX`.
     * Returns the placeholder `xxxx-xxxx` when the raw id is unavailable
     * or shorter than 8 characters (extremely rare; possible only on
     * misconfigured emulators).
     */
    internal fun formatDeviceId(rawId: String?): String {
        if (rawId.isNullOrBlank() || rawId.length < FINGERPRINT_LENGTH) return PLACEHOLDER
        val head = rawId.take(FINGERPRINT_LENGTH).lowercase()
        return "${head.substring(0, FINGERPRINT_HALF)}-${head.substring(FINGERPRINT_HALF, FINGERPRINT_LENGTH)}"
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val FINGERPRINT_LENGTH = 8
        const val FINGERPRINT_HALF = 4
        const val PLACEHOLDER = "xxxx-xxxx"
    }
}
