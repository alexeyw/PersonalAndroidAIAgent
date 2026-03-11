package ai.agent.android.data.tools.local

import android.content.Context
import androidx.appfunctions.AppFunctionContext

/**
 * Manager class responsible for coordinating and executing local AppFunctions.
 * It provides a unified interface for the AI agent's orchestrator to discover
 * and trigger application functions securely.
 */
class LocalAppFunctionManager(private val context: Context) {

    private val getSystemTimeTool = GetSystemTimeTool()
    private val setAlarmTool = SetAlarmTool(context)

    /**
     * Gets the current system time using the internal tool.
     *
     * @return Current time string.
     */
    fun executeGetSystemTime(): String {
        val appContext = object : AppFunctionContext {
            override val context: Context get() = this@LocalAppFunctionManager.context
        }
        return getSystemTimeTool.getCurrentTime(appContext)
    }

    /**
     * Sets an alarm using the internal tool.
     *
     * @param hour Hour of the day (0-23).
     * @param minute Minute of the hour (0-59).
     * @param message Alarm label.
     * @return Result message of the operation.
     */
    fun executeSetAlarm(hour: Int, minute: Int, message: String): String {
        val appContext = object : AppFunctionContext {
            override val context: Context get() = this@LocalAppFunctionManager.context
        }
        return setAlarmTool.setAlarm(appContext, hour, minute, message)
    }
}
