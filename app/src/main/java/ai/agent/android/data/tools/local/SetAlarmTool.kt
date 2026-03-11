package ai.agent.android.data.tools.local

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.appfunctions.service.AppFunction

/**
 * A class containing application functions related to the system alarm.
 * This class provides tools that the AI agent can use to set alarms.
 */
class SetAlarmTool(private val context: Context) {

    /**
     * Sets a system alarm for a specific hour and minute.
     * Use this function to schedule an alarm or reminder for the user.
     *
     * @param appContext The application function context.
     * @param hour The hour of the alarm (0-23).
     * @param minute The minute of the alarm (0-59).
     * @param message An optional message or label for the alarm.
     * @return A string indicating the result of the operation.
     */
    @AppFunction
    fun setAlarm(appContext: androidx.appfunctions.AppFunctionContext, hour: Int, minute: Int, message: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        return try {
            context.startActivity(intent)
            "Alarm successfully set for $hour:$minute with message '$message'."
        } catch (e: Exception) {
            "Failed to set alarm: ${e.message}"
        }
    }
}
