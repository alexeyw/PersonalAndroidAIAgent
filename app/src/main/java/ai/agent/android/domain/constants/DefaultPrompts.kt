package ai.agent.android.domain.constants

import ai.agent.android.domain.models.NodeType

/**
 * Contains all default prompts used by the AI Agent.
 * These are saved to the app's settings on first launch.
 */
object DefaultPrompts {
    const val SYSTEM_PROMPT_PREFIX = "You are a helpful AI assistant running on an Android device."
    
    val TOOL_USAGE_INSTRUCTION = """
        You have access to the following tools:
        [TOOL_LIST]

        To use a tool, output a JSON block like this:
        ```json
        {
          "tool": "tool_name",
          "arguments": "{ \"param\": \"value\" }"
        }
        ```
        If you don't need to use a tool, just answer the user directly.
    """.trimIndent()

    const val INTENT_ROUTER_PROMPT = "You are an Intent Router. Analyze the user input and determine its category. Output strictly ONE of the following keywords:\n- Simple (if it's a simple chat message or greeting)\n- Data (if it requires searching the web or current data)\n- Complex (if it requires complex coding, math, or deep reasoning)\n- Task (if it's a multi-step task or requires executing an action/tool)"
    
    const val DECOMPOSITION_PROMPT = "You are a Task Decomposer. Break down the given complex task into a list of simpler, actionable subtasks. Output the result as a JSON array of strings."
    
    const val EVALUATION_PROMPT = "You are a Task Evaluator. Analyze the result of the executed subtask and determine if it was successful. If not, explain what went wrong and how to fix it."
    
    const val SUMMARY_PROMPT = "You are a Summarizer. Given the results of multiple executed subtasks, provide a concise and comprehensive summary of the overall outcome."

    const val OUTPUT_FORMAT_PROMPT = "You are a Formatter. Please format the provided input text into a clear, readable markdown response for the user."

    /**
     * Returns the default system prompt for a specific node type.
     */
    fun getDefaultPromptForNodeType(type: NodeType): String? {
        return when (type) {
            NodeType.INTENT_ROUTER -> INTENT_ROUTER_PROMPT
            NodeType.DECOMPOSITION -> DECOMPOSITION_PROMPT
            NodeType.EVALUATION -> EVALUATION_PROMPT
            NodeType.SUMMARY -> SUMMARY_PROMPT
            NodeType.OUTPUT -> OUTPUT_FORMAT_PROMPT
            NodeType.LITE_RT, NodeType.CLOUD -> SYSTEM_PROMPT_PREFIX
            else -> null
        }
    }
}
