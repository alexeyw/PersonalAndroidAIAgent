package ai.agent.android.data.tools.local

import androidx.appfunctions.AppFunctionContext
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class GetSystemTimeToolTest {

    @Test
    fun `getCurrentTime returns valid time format`() {
        // Arrange
        val tool = GetSystemTimeTool()
        val mockContext = mockk<AppFunctionContext>()

        // Act
        val result = tool.getCurrentTime(mockContext)

        // Assert
        assertNotNull(result)
        val timePattern = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$")
        assertTrue("Result '$result' should match HH:mm:ss format", timePattern.matcher(result).matches())
    }
}
