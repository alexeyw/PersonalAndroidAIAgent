package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ToolRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case that orchestrates the ReAct (Reasoning and Acting) cycle of the AI Agent.
 * It manages the loop of generating thoughts, deciding on actions, executing tools,
 * and feeding observations back to the LLM.
 */
class AgentOrchestratorUseCase @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val toolRepository: ToolRepository,
    private val chatRepository: ChatRepository,
    private val getContextWindowUseCase: GetContextWindowUseCase
) {
    companion object {
        const val MAX_ITERATIONS = 5
        private const val SYSTEM_PROMPT_PREFIX = "You are a helpful AI assistant running on an Android device."
    }

    /**
     * Starts the orchestration cycle for a given user prompt.
     *
     * @param sessionId The current chat session ID.
     * @param userPrompt The new prompt from the user.
     * @return A [Flow] of [AgentOrchestratorState] emitting the progress of the agent.
     */
    operator fun invoke(sessionId: String, userPrompt: String): Flow<AgentOrchestratorState> = flow {
        emit(AgentOrchestratorState.Loading)

        // 1. Save the user's message
        val userMessage = ChatMessage(
            sessionId = sessionId,
            role = Role.USER,
            content = userPrompt,
            timestamp = System.currentTimeMillis()
        )
        chatRepository.saveMessage(userMessage)

        // 2. Load available tools and construct the system prompt
        val tools = toolRepository.getAvailableTools()
        val toolsDescription = tools.joinToString("\n") { 
            "- ${it.name}: ${it.description} | Params: ${it.parameters}" 
        }

        val baseSystemPrompt = """
            $SYSTEM_PROMPT_PREFIX
            You have access to the following tools:
            $toolsDescription
            
            To use a tool, output a JSON block like this:
            ```json
            {
              "tool": "tool_name",
              "arguments": "{ \"param\": \"value\" }"
            }
            ```
            If you don't need to use a tool, just answer the user directly.
        """.trimIndent()

        // ReAct loop
        var currentIteration = 0
        var isCompleted = false

        while (currentIteration < MAX_ITERATIONS && !isCompleted) {
            currentIteration++

            // 3. Get the context window (history + previous thoughts/observations)
            val contextWindow = getContextWindowUseCase(sessionId)
            val fullPrompt = "$baseSystemPrompt\n\n$contextWindow\nAGENT: "

            // 4. Request generation from the local LLM
            val responseStream = llmEngine.generateResponseStream(fullPrompt)
            
            val accumulatedResponse = StringBuilder()
            var emittedThinking = false

            try {
                responseStream.collect { token ->
                    accumulatedResponse.append(token)
                    
                    if (!emittedThinking) {
                        emit(AgentOrchestratorState.Thinking(accumulatedResponse.toString()))
                        emittedThinking = true
                    } else {
                        emit(AgentOrchestratorState.Answering(accumulatedResponse.toString()))
                    }
                }
            } catch (e: Exception) {
                emit(AgentOrchestratorState.Error(e.message ?: "Unknown error during LLM generation"))
                return@flow
            }

            val fullResponseText = accumulatedResponse.toString().trim()
            
            // 5. Save the agent's raw response to history
            chatRepository.saveMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = Role.AGENT,
                    content = fullResponseText,
                    timestamp = System.currentTimeMillis()
                )
            )

            // 6. Parse the response to check if a tool needs to be called
            val toolCall = parseToolCall(fullResponseText)
            
            if (toolCall != null) {
                val (toolName, toolArgs) = toolCall
                emit(AgentOrchestratorState.ExecutingTool(toolName, toolArgs))

                // Execute the tool
                val result = try {
                    toolRepository.executeTool(toolName, toolArgs)
                } catch (e: Exception) {
                    "Error executing $toolName: ${e.message}"
                }

                emit(AgentOrchestratorState.ObservationResult(toolName, result))

                // Save the observation to the history as a SYSTEM message so the LLM sees it
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.SYSTEM,
                        content = "Observation from $toolName: $result",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                // No tool called, the agent answered directly
                isCompleted = true
                emit(AgentOrchestratorState.Completed(fullResponseText))
            }
        }

        if (!isCompleted) {
            emit(AgentOrchestratorState.Error("Reached maximum iterations ($MAX_ITERATIONS) without a final answer."))
        }
    }

    /**
     * Extremely simple parser to find a JSON block for tool calling.
     * Real implementation might need more robust regex or a JSON parser.
     */
    private fun parseToolCall(response: String): Pair<String, String>? {
        val blockRegex = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val blockMatch = blockRegex.find(response) ?: return null
        val jsonBlock = blockMatch.groups[1]?.value ?: return null
        
        val toolMatch = """"tool"\s*:\s*"([^"]+)"""".toRegex().find(jsonBlock)
        val toolName = toolMatch?.groups?.get(1)?.value ?: return null
        
        val argsIndex = jsonBlock.indexOf("\"arguments\"")
        if (argsIndex == -1) return null
        
        val colonIndex = jsonBlock.indexOf(":", argsIndex)
        if (colonIndex == -1) return null
        
        val valueStart = jsonBlock.substring(colonIndex + 1).trimStart()
        
        var arguments = ""
        if (valueStart.startsWith("\"")) {
            // It's a JSON string, find the end of it, ignoring escaped quotes
            var endQuoteIndex = -1
            for (i in 1 until valueStart.length) {
                if (valueStart[i] == '"' && valueStart[i - 1] != '\\') {
                    endQuoteIndex = i
                    break
                }
            }
            if (endQuoteIndex != -1) {
                arguments = valueStart.substring(1, endQuoteIndex)
                arguments = arguments.replace("\\\"", "\"").replace("\\\\", "\\")
            }
        } else if (valueStart.startsWith("{")) {
            // It's a JSON object
            val endBraceIndex = valueStart.lastIndexOf("}")
            if (endBraceIndex != -1) {
                arguments = valueStart.substring(0, endBraceIndex + 1)
            }
        } else {
            return null
        }
        
        return Pair(toolName, arguments)
    }
}
