package app.knotwork.android.data.local.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import timber.log.Timber
import java.io.File
import java.util.Base64

/**
 * Signals that a stored value exists but cannot be decrypted back into
 * plaintext — the base64 framing is broken, the AEAD tag does not verify,
 * or the Keystore key behind the store is gone.
 *
 * Callers distinguish this from an *absent* value (`null` return): an absent
 * value is a normal state, while an unreadable one means the store has lost
 * integrity and the caller must apply its own recovery policy (regenerate,
 * surface an error screen, treat as unset, …).
 *
 * @param key The preferences entry whose value could not be read back.
 * @param cause The underlying decoding or cryptographic failure.
 */
class SecureValueUnreadableException(key: String, cause: Throwable) :
    Exception("Stored secure value '$key' cannot be decrypted", cause)

/**
 * A small key-value store for secrets: a plain [SharedPreferences] file whose
 * values are encrypted with an [AeadCipher] under a dedicated Keystore key.
 *
 * This is the project's replacement for the deprecated
 * `EncryptedSharedPreferences`. Differences that matter to callers:
 *
 *  - **Opening the store never fails.** The preferences file is plaintext
 *    XML holding base64 blobs; there is no wrapped keyset that can fail to
 *    unwrap at open time. Failures move to individual value reads, where
 *    each caller can apply its own policy.
 *  - **Entry keys are not encrypted.** The names (`db_passphrase_hex`,
 *    `openai_api_key`, …) are static identifiers baked into the app — only
 *    the values are secret.
 *  - **Values cannot be swapped between entries.** Every value is
 *    authenticated with associated data derived from the store name and the
 *    entry key, so a blob copied from one entry to another fails the tag
 *    check instead of decrypting under the wrong label.
 *
 * @property context Application context owning the preferences file.
 * @property prefsName Name of the backing [SharedPreferences] file.
 * @property keyAlias Keystore alias of the AEAD key dedicated to this store.
 * @property cipher The AEAD implementation (Keystore-backed in production).
 */
class KeystoreBackedPrefsStore(
    private val context: Context,
    private val prefsName: String,
    private val keyAlias: String,
    private val cipher: AeadCipher,
) {

    /**
     * Cached prefs instance. Not a `lazy`: [destroy] must drop the cache,
     * because `Context.deleteSharedPreferences` declares the results
     * undefined when a live instance for the same name is retained — a
     * post-wipe rewrite has to go through a freshly opened file.
     */
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(): SharedPreferences =
        cachedPrefs ?: context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).also { cachedPrefs = it }

    /**
     * Reads and decrypts the value stored under [key].
     *
     * @param key The entry to read.
     * @return The plaintext value, or `null` when no value is stored.
     * @throws SecureValueUnreadableException When a value is present but
     *   cannot be decoded or does not pass authenticated decryption.
     */
    fun getString(key: String): String? {
        val encoded = prefs().getString(key, null) ?: return null
        return try {
            val blob = Base64.getDecoder().decode(encoded)
            String(cipher.decrypt(keyAlias, associatedDataFor(key), blob), Charsets.UTF_8)
        } catch (e: Exception) {
            throw SecureValueUnreadableException(key, e)
        }
    }

    /**
     * Encrypts and stores [value] under [key].
     *
     * @param key The entry to write.
     * @param value The plaintext to protect.
     * @param synchronous When `true`, the write is committed synchronously
     *   (fsync before returning) — required when the caller must guarantee
     *   the secret hit disk before it is used, e.g. a database passphrase
     *   that is about to encrypt a freshly created database.
     */
    fun putString(key: String, value: String, synchronous: Boolean = false) {
        val blob = cipher.encrypt(keyAlias, associatedDataFor(key), value.toByteArray(Charsets.UTF_8))
        val encoded = Base64.getEncoder().encodeToString(blob)
        prefs().edit(commit = synchronous) {
            putString(key, encoded)
        }
    }

    /**
     * Removes the value stored under [key], if any.
     *
     * @param key The entry to clear.
     */
    fun remove(key: String) {
        prefs().edit {
            remove(key)
        }
    }

    /**
     * Destroys the store: clears all entries synchronously, deletes the
     * backing preferences file, and deletes the Keystore key — after this
     * call nothing previously stored can be recovered. Used by the explicit
     * user-confirmed wipe flows.
     */
    fun destroy() {
        prefs().edit(commit = true) { clear() }
        cachedPrefs = null
        deletePrefsFile(prefsName)
        cipher.deleteKey(keyAlias)
    }

    /**
     * Binds a value to its slot: the associated data commits to both the
     * store identity and the entry key, so authentication fails if a blob is
     * replayed in a different slot.
     */
    private fun associatedDataFor(key: String): ByteArray = "$prefsName/$key".toByteArray(Charsets.UTF_8)

    private fun deletePrefsFile(name: String) {
        try {
            context.deleteSharedPreferences(name)
        } catch (e: Exception) {
            Timber.w(e, "deleteSharedPreferences failed; falling back to direct file removal.")
            val file = File(File(context.applicationInfo.dataDir, "shared_prefs"), "$name.xml")
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
