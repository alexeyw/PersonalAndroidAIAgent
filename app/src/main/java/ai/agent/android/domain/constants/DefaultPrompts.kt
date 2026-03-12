package ai.agent.android.domain.constants

/**
 * Contains all default prompts used by the AI Agent.
 * These are saved to the app's settings on first launch.
 */
object DefaultPrompts {
    const val SYSTEM_PROMPT_PREFIX = "You are a helpful AI assistant running on an Android device."
    
    val TOOL_USAGE_INSTRUCTION = """
        You have access to the following tools:
        %s
        
        To use a tool, output a JSON block like this:
        ```json
        {
          "tool": "tool_name",
          "arguments": "{ \"param\": \"value\" }"
        }
        ```
        If you don't need to use a tool, just answer the user directly.
    """.trimIndent()
}
