package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.Identity

/**
 * Repository surfacing the device-local identity snapshot rendered inside
 * the Settings identity card.
 *
 * Stateless and idempotent — the implementation reads
 * `Settings.Secure.ANDROID_ID` plus an `AndroidKeyStore` probe on every
 * call. There is no in-flight mutation, so this surface is read-only.
 */
interface IdentityRepository {

    /**
     * Returns the current identity snapshot. The deviceId is truncated
     * to a fixed length so the card remains readable; the probe portion
     * checks whether the Android Keystore provider can be loaded — a
     * `false` value warns the user that API keys would fall back to
     * encrypted preferences instead of hardware-backed storage.
     *
     * @param anonymousLabel Localized label rendered in the identity card
     *   ("Anonymous · this device"). Kept as a parameter so the
     *   data-layer impl stays free of string-resource dependencies.
     */
    suspend fun getIdentity(anonymousLabel: String): Identity
}
