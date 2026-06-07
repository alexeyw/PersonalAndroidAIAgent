package app.knotwork.android.data.repositories

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the static-format portion of [IdentityRepositoryImpl].
 * The ANDROID_ID / KeyStore probe paths require Robolectric (covered
 * by the integration suite), so this JVM-only test focuses on the
 * deterministic [IdentityRepositoryImpl.formatDeviceId] helper.
 */
class IdentityRepositoryImplTest {

    private val repository = IdentityRepositoryImpl(mockk<Context>(relaxed = true))

    @Test
    fun `formatDeviceId truncates to 8 hex characters with a single hyphen`() = runTest {
        val formatted = repository.formatDeviceId("4f3a92d1abcdef00")
        assertEquals("4f3a-92d1", formatted)
    }

    @Test
    fun `formatDeviceId returns placeholder for null`() {
        assertEquals("xxxx-xxxx", repository.formatDeviceId(null))
    }

    @Test
    fun `formatDeviceId returns placeholder for too-short input`() {
        assertEquals("xxxx-xxxx", repository.formatDeviceId("4f3a"))
    }

    @Test
    fun `formatDeviceId lowercases mixed-case hex`() {
        assertEquals("4f3a-92d1", repository.formatDeviceId("4F3A92D1ABCDEF"))
    }
}
