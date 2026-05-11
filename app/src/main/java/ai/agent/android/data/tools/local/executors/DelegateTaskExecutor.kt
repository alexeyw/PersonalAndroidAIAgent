package ai.agent.android.data.tools.local.executors

import ai.agent.android.data.tools.local.DelegateTaskTool
import ai.agent.android.domain.repositories.LocalToolExecutor
import org.json.JSONObject
import javax.inject.Inject

/**
 * [LocalToolExecutor] implementation for the built-in `delegate_task` tool.
 *
 * Parses the JSON arguments (`taskDescription`, optional `targetModel`) and delegates
 * to [DelegateTaskTool], which forwards the prompt to the configured cloud provider
 * and persists the response in long-term memory.
 */
class DelegateTaskExecutor @Inject constructor(private val delegateTaskTool: DelegateTaskTool) : LocalToolExecutor {

    override val toolName: String = TOOL_NAME

    override suspend fun execute(arguments: String): String {
        val json = JSONObject(arguments)
        val taskDescription = json.getString("taskDescription")
        val targetModel = if (json.has("targetModel")) json.getString("targetModel") else "anthropic"
        return delegateTaskTool.executeDelegation(taskDescription, targetModel)
    }

    companion object {
        const val TOOL_NAME = "delegate_task"
    }
}
