package ai.agent.android.domain.models

/**
 * Snapshot of the device-local identity surfaced inside the Settings
 * identity card.
 *
 * v0.1 ships with anonymous, on-device-only identity — no signed-in
 * account, no cloud profile. The card is informational and helps the user
 * confirm two things at a glance:
 *
 *  1. There is no remote account behind their agent — the display name is
 *     always "Anonymous · this device".
 *  2. Their API keys (when configured) are stored in the Android Keystore,
 *     not in plain DataStore — [keystoreAvailable] flips to `true` once the
 *     repository confirms the Keystore is reachable.
 *
 * @property displayName Localized "Anonymous · this device"-style label
 *   shown beneath the avatar.
 * @property deviceId Short device fingerprint derived from
 *   `Settings.Secure.ANDROID_ID`. Truncated to 8 hex characters formatted
 *   as `XXXX-XXXX` so the card stays readable while keeping enough entropy
 *   to disambiguate the device on a multi-device account in the future.
 * @property keystoreAvailable `true` once the impl successfully loaded the
 *   `AndroidKeyStore` provider. A `false` value means the device is in an
 *   unusual state (e.g. work profile with restrictions) and the user's
 *   keys would fall back to encrypted preferences.
 */
data class Identity(val displayName: String, val deviceId: String, val keystoreAvailable: Boolean)
