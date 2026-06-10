package app.knotwork.android.data.local.crypto

import android.content.SharedPreferences

/**
 * Minimal map-backed [SharedPreferences] for JVM unit tests.
 *
 * Only the surface used by [KeystoreBackedPrefsStore] is meaningfully
 * exercised (string values, remove, clear, synchronous and asynchronous
 * edits); the remaining typed getters are implemented for interface
 * completeness. Listeners are not supported.
 */
class InMemorySharedPreferences : SharedPreferences {

    /** Backing storage, exposed so tests can assert on raw stored values. */
    val values: MutableMap<String, Any?> = mutableMapOf()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    /** Editor that buffers mutations and applies them on [commit] / [apply]. */
    inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = also {
            pending[key] = value
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = also {
            pending[key] = values
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = also { pending[key] = value }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = also { pending[key] = value }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = also { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = also {
            pending[key] = value
        }

        override fun remove(key: String): SharedPreferences.Editor = also { removals += key }

        override fun clear(): SharedPreferences.Editor = also { clearRequested = true }

        override fun commit(): Boolean {
            applyPending()
            return true
        }

        override fun apply() {
            applyPending()
        }

        private fun applyPending() {
            if (clearRequested) {
                values.clear()
            }
            removals.forEach { values.remove(it) }
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
