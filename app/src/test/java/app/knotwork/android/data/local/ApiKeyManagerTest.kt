package app.knotwork.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ApiKeyManagerTest {
    private lateinit var context: Context
    private lateinit var sharedPrefs: SharedPreferences

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPrefs = mockk(relaxed = true)

        mockkStatic(EncryptedSharedPreferences::class)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `recovers from EncryptedSharedPreferences exception by deleting corrupted file`() {
        // Arrange
        // Simulate an exception thrown on first create
        var createCount = 0
        every {
            EncryptedSharedPreferences.create(
                any<Context>(),
                any<String>(),
                any(),
                any(),
                any(),
            )
        } answers {
            createCount++
            if (createCount == 1) {
                throw SecurityException("Simulated corrupted keys exception")
            }
            sharedPrefs
        }

        // Act
        val manager = ApiKeyManager(context)
        // Accessing a property triggers the lazy initialization
        val flow = manager.getOpenAIKey()

        // Assert
        assertNotNull(flow)
        verify { context.deleteSharedPreferences("secure_api_keys") }
        // Should have been called twice (once failed, once successful)
        assertEquals(2, createCount)
    }
}
