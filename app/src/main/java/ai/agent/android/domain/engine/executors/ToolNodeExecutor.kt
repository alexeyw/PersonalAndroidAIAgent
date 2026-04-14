package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.services.ApprovalNotifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolNodeExecutor @Inject constructor(
    private val toolRepository: ToolRepository,
    private val settingsRepository: SettingsRepository,
    private val approvalNotifier: ApprovalNotifier,
    private val chatRepository: ChatRepository
) : NodeExecutor {

    private val activeApprovalDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        val deferred = activeApprovalDeferreds.remove(sessionId)
        deferred?.complete(isApproved)
    }

    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String
    ): Flow<Any> = flow {
        val toolName = node.toolName
        if (toolName.isNullOrBlank()) {
            emit(NodeExecutionResult(error = "Tool node is missing toolName configuration."))
            return@flow
        }

        val toolArgs = parseToolArguments(inputText) ?: inputText
        val requiresUserConfirmation = settingsRepository.requiresUserConfirmation.first()
        var isApproved = true

        if (requiresUserConfirmation) {
            emit(AgentOrchestratorState.WaitingForApproval(toolName, toolArgs))
            approvalNotifier.sendApprovalRequest(sessionId, toolName, toolArgs)

            val deferred = CompletableDeferred<Boolean>()
            activeApprovalDeferreds[sessionId] = deferred
            isApproved = deferred.await()

            if (!isApproved) {
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.SYSTEM,
                        content = "User denied execution of tool: $toolName",
                        timestamp = System.currentTimeMillis()
                    )
                )
                emit(AgentOrchestratorState.ObservationResult(toolName, "Execution denied by user"))
                emit(NodeExecutionResult(outputText = "Execution denied by user"))
                return@flow
            }
        }

        emit(AgentOrchestratorState.ExecutingTool(toolName, toolArgs))
        val result = try {
            toolRepository.executeTool(toolName, toolArgs)
        } catch (e: Exception) {
            Timber.e(e, "Error executing tool: $toolName")
            "Error executing $toolName: ${e.message}"
        }

        emit(AgentOrchestratorState.ObservationResult(toolName, result))
        chatRepository.saveMessage(
            ChatMessage(
                sessionId = sessionId,
                role = Role.SYSTEM,
                content = "Observation from $toolName: $result",
                timestamp = System.currentTimeMillis()
            )
        )
        emit(NodeExecutionResult(outputText = result))
    }

    @androidx.annotation.VisibleForTesting
    internal fun parseToolArguments(response: String): String? {
        val blockRegex = """```json\s*(\{.*?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        var jsonBlock = blockRegex.find(response)?.groups?.get(1)?.value
        
        if (jsonBlock == null) {
            val startIndex = response.indexOf('{')
            val endIndex = response.lastIndexOf('}')
            jsonBlock = if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                response.substring(startIndex, endIndex + 1)
            } else {
                response
            }
        }
        
        return try {
            val jsonObject = org.json.JSONObject(jsonBlock)
            if (!jsonObject.has("arguments")) return null
            
            jsonObject.get("arguments").toString()
        } catch (e: org.json.JSONException) {
            Timber.e(e, "Error parsing tool arguments JSON")
            null
        }
    }
}