package app.knotwork.android.data.tools.local.executors

import app.knotwork.android.domain.repositories.LocalToolExecutor
import app.knotwork.android.domain.usecases.ScheduleTaskUseCase
import org.json.JSONObject
import javax.inject.Inject

/**
 * [LocalToolExecutor] implementation for the built-in `schedule_task` tool.
 *
 * Parses the JSON arguments emitted by the LLM (`prompt`, optional `intervalHours`,
 * optional `delayMinutes`) and forwards them to [ScheduleTaskUseCase] which enqueues
 * the task with `WorkManager`.
 */
class ScheduleTaskExecutor @Inject constructor(private val scheduleTaskUseCase: ScheduleTaskUseCase) :
    LocalToolExecutor {

    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String): String {
        val json = JSONObject(arguments)
        val prompt = json.getString("prompt")
        val intervalHours = if (json.has("intervalHours")) json.getLong("intervalHours") else 0L
        val delayMinutes = if (json.has("delayMinutes")) json.getLong("delayMinutes") else 0L
        return scheduleTaskUseCase(prompt, intervalHours, delayMinutes)
    }

    companion object {
        const val TOOL_NAME = "schedule_task"
    }
}
