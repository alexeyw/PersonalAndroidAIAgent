package ai.agent.android.data.tools.local

import android.content.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class LocalAppFunctionManagerTest {

    @Test
    fun `executeGetSystemTime calls tool and returns time`() {
        // Arrange
        val mockContext = mockk<Context>()
        val manager = LocalAppFunctionManager(mockContext)

        // Act
        val result = manager.executeGetSystemTime()

        // Assert
        val timePattern = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$")
        assertTrue("Result '$result' should match HH:mm:ss format", timePattern.matcher(result).matches())
    }

    @Test
    fun `executeSetAlarm calls tool and returns success`() {
        // Arrange
        val mockContext = mockk<Context>()
        every { mockContext.startActivity(any()) } just Runs
        val manager = LocalAppFunctionManager(mockContext)

        // Act
        val result = manager.executeSetAlarm(10, 15, "Meeting")

        // Assert
        assertEquals("Alarm successfully set for 10:15 with message 'Meeting'.", result)
    }
}
