package ai.agent.android.data.tools.local

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.appfunctions.AppFunctionContext
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetAlarmToolTest {

    @Test
    fun `setAlarm starts activity and returns success message`() {
        // Arrange
        val mockContext = mockk<Context>()
        val mockAppContext = mockk<AppFunctionContext>()
        val intentSlot = slot<Intent>()
        every { mockContext.startActivity(capture(intentSlot)) } just Runs
        val tool = SetAlarmTool(mockContext)

        val hour = 7
        val minute = 30
        val message = "Wake up"

        // Act
        val result = tool.setAlarm(mockAppContext, hour, minute, message)

        // Assert
        verify(exactly = 1) { mockContext.startActivity(any()) }
        
        assertEquals("Alarm successfully set for 7:30 with message 'Wake up'.", result)
    }

    @Test
    fun `setAlarm catches exception and returns error message`() {
        // Arrange
        val mockContext = mockk<Context>()
        val mockAppContext = mockk<AppFunctionContext>()
        val errorMessage = "Activity not found"
        every { mockContext.startActivity(any()) } throws Exception(errorMessage)
        val tool = SetAlarmTool(mockContext)

        // Act
        val result = tool.setAlarm(mockAppContext, 8, 0, "Test")

        // Assert
        assertEquals("Failed to set alarm: $errorMessage", result)
    }
}
