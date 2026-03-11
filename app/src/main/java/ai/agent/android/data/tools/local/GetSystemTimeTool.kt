package ai.agent.android.data.tools.local

import androidx.appfunctions.service.AppFunction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A class containing application functions related to system time.
 * This class provides tools that the AI agent can use to query time.
 */
class GetSystemTimeTool {

    /**
     * Gets the current system time formatted as a string.
     * Use this function whenever you need to know the current time.
     *
     * @param context The application function context.
     * @return The current system time in "HH:mm:ss" format.
     */
    @AppFunction
    fun getCurrentTime(context: androidx.appfunctions.AppFunctionContext): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }
}
