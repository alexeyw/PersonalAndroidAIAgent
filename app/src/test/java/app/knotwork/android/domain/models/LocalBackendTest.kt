package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [LocalBackend] — the typed identifier for the on-device LiteRT backend.
 */
class LocalBackendTest {

    @Test
    fun `given each enum value when its key is parsed then the same value is returned`() {
        LocalBackend.entries.forEach { backend ->
            assertEquals(backend, LocalBackend.fromKey(backend.key))
        }
    }

    @Test
    fun `given unknown key when fromKey is called then null is returned`() {
        assertNull(LocalBackend.fromKey("TPU"))
    }

    @Test
    fun `given null when fromKey is called then null is returned`() {
        assertNull(LocalBackend.fromKey(null))
    }

    @Test
    fun `given lowercase key when fromKey is called then null is returned`() {
        // Wire-form is case-sensitive on purpose: DataStore values are written through
        // LocalBackend.key (always uppercase) so any other casing means a corrupt write.
        assertNull(LocalBackend.fromKey("cpu"))
    }
}
