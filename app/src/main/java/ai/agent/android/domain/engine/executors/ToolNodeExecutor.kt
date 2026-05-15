package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.constants.DefaultPrompts
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executor for [NodeType.TOOL][ai.agent.android.domain.models.NodeType.TOOL] nodes.
 *
 * Resolves which tool to run (either the explicit `node.toolName` or LLM-driven auto
 * selection via [DefaultPrompts.Tool.AUTO_SELECT_TEMPLATE]), builds its arguments, and
 * dispatches the call through [ToolRepository]. Before dispatching, the executor goes
 * through a Human-in-the-Loop (HITL) gate driven by the resolved tool's
 * [ToolRisk][ai.agent.android.domain.models.ToolRisk] (canonical source:
 * [ToolRepository.getRisk][ai.agent.android.domain.repositories.ToolRepository.getRisk]):
 *
 *  - [READ_ONLY][ai.agent.android.domain.models.ToolRisk.READ_ONLY] — runs without a
 *    prompt unless the user has globally enabled `requiresUserConfirmation`, which
 *    acts as an "ask on every tool call" override.
 *  - [SENSITIVE][ai.agent.android.domain.models.ToolRisk.SENSITIVE] — always prompts.
 *  - [DESTRUCTIVE][ai.agent.android.domain.models.ToolRisk.DESTRUCTIVE] — always
 *    prompts; the notification fallback uses a distinct high-importance channel.
 *
 * When the gate fires, the executor:
 *
 * 1. publishes [WaitingForApproval][ai.agent.android.domain.models.AgentOrchestratorState.WaitingForApproval];
 * 2. sends a notification via [ApprovalNotifier] (channel / icon picked by risk);
 * 3. suspends on a [CompletableDeferred] keyed by `sessionId`, bounded by
 *    [SettingsRepository.toolCallTimeoutMs][ai.agent.android.domain.repositories.SettingsRepository.toolCallTimeoutMs];
 * 4. resumes when [resumeWithApproval] is called from `MainActivity` /
 *    `AgentApprovalReceiver`, or fails fast on timeout.
 *
 * Tool observations and "Execution denied by user" markers are persisted as
 * non-final [SYSTEM][ai.agent.android.domain.models.Role.SYSTEM] chat messages so the
 * agent console can replay them without polluting the user-facing chat list.
 *
 * Marked `@Singleton` because [activeApprovalDeferreds] holds per-session pending
 * approval requests that must outlive any individual executor invocation.
 */
@Singleton
class ToolNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase,
    private val toolRepository: ToolRepository,
    private val settingsRepository: SettingsRepository,
    private val approvalNotifier: ApprovalNotifier,
    private val chatRepository: ChatRepository,
) : NodeExecutor {

    private val activeApprovalDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /**
     * Completes the suspended approval request for [sessionId] with the user's decision.
     *
     * Invoked from the UI layer (`MainActivity`) and the notification-action receiver
     * (`AgentApprovalReceiver`). No-ops silently when there is no pending request for
     * the given session — duplicate dispatches (e.g. user taps the notification action
     * after the dialog already resumed the executor) cannot corrupt state.
     *
     * @param sessionId chat session id used as the lookup key in [activeApprovalDeferreds].
     * @param isApproved `true` if the user approved tool execution, `false` to deny it.
     */
    fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        val deferred = activeApprovalDeferreds.remove(sessionId)
        deferred?.complete(isApproved)
    }

    /**
     * Test-only readiness probe: returns `true` once [execute] has registered a
     * `CompletableDeferred` for [sessionId] and is suspended awaiting the user's
     * decision. Instrumented tests use this to synchronise their `resumeWithApproval`
     * call deterministically — without it the test would have to rely on a fixed
     * `delay(...)`, which races the deferred registration on slow / overloaded
     * emulators (the producer reaches the `WaitingForApproval` emit point before
     * registering its deferred; a `resumeWithApproval` call observed by the map in
     * that window would be silently dropped).
     *
     * Intentionally not part of the public surface: production callers reach the
     * gate through state emissions, never through the executor's internal map.
     */
    @androidx.annotation.VisibleForTesting
    internal fun hasPendingApproval(sessionId: String): Boolean = activeApprovalDeferreds.containsKey(sessionId)

    // Reason: typed tool dispatcher with explicit branches for
    // LocalToolExecutor, AppFunction, and MCP delegation, each with its own
    // pre-flight (risk → approval suspension) and post-flight (`Result.success`
    // vs typed error) handling. Extracting helpers fragments the per-tool
    // recovery semantics.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
    ): Flow<NodeOutput> = flow {
        val toolNameConfig = node.toolName
        if (toolNameConfig.isNullOrBlank()) {
            emit(NodeOutput.Result(NodeExecutionResult(error = "Tool node is missing toolName configuration.")))
            return@flow
        }

        emit(NodeOutput.State(AgentOrchestratorState.Thinking("Analyzing task for tool execution...")))

        val loadResult = loadModelUseCase(node.modelPath)
        if (loadResult is Result.Error) {
            val errorMsg = "Error loading local model for tool node"
            emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
            emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
            return@flow
        }

        val resolvedToolName: String
        val resolvedToolArgs: String

        if (toolNameConfig.equals("auto", ignoreCase = true)) {
            val availableTools = toolRepository.getAvailableTools()
            if (availableTools.isEmpty()) {
                val errorMsg = "No tools available for auto selection"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            val toolsDescriptions = availableTools.joinToString("\n\n") {
                "Tool: ${it.name}\nDescription: ${it.description}\nParameters: ${it.parameters}"
            }

            val prompt = DefaultPrompts.renderTemplate(
                DefaultPrompts.Tool.AUTO_SELECT_TEMPLATE,
                mapOf(
                    "AVAILABLE_TOOLS" to toolsDescriptions,
                    "INPUT_TEXT" to inputText,
                ),
            )

            val responseStream = llmEngine.generateResponseStream(prompt)
            val accumulatedResponse = StringBuilder()
            try {
                responseStream.collect { token ->
                    accumulatedResponse.append(token)
                }
            } catch (e: CancellationException) {
                // Preserve structured-concurrency cancellation: a broad `catch (Exception)`
                // would silently swallow cancellation and leave the parent coroutine running.
                throw e
            } catch (e: Exception) {
                Timber.tag("PipelineDebug").e(e, "Error generating tool selection via LLM")
                val errorMsg = "Error generating tool selection: ${e.message}"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            val parsedSelection = parseToolSelection(accumulatedResponse.toString())
            if (parsedSelection == null) {
                val errorMsg = "Failed to parse tool selection JSON from LLM output"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            resolvedToolName = parsedSelection.first
            resolvedToolArgs = parsedSelection.second
        } else {
            val tools = toolRepository.getAvailableTools()
            val selectedTool = tools.find { it.name == toolNameConfig }
            if (selectedTool == null) {
                val errorMsg = "Tool $toolNameConfig not found in available tools"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            val prompt = DefaultPrompts.renderTemplate(
                DefaultPrompts.Tool.ARGUMENT_GENERATION_TEMPLATE,
                mapOf(
                    "TOOL_NAME" to selectedTool.name,
                    "TOOL_DESCRIPTION" to selectedTool.description,
                    "TOOL_PARAMETERS" to selectedTool.parameters,
                    "INPUT_TEXT" to inputText,
                ),
            )

            val responseStream = llmEngine.generateResponseStream(prompt)
            val accumulatedResponse = StringBuilder()
            try {
                responseStream.collect { token ->
                    accumulatedResponse.append(token)
                }
            } catch (e: CancellationException) {
                // Preserve structured-concurrency cancellation: a broad `catch (Exception)`
                // would silently swallow cancellation and leave the parent coroutine running.
                throw e
            } catch (e: Exception) {
                Timber.tag("PipelineDebug").e(e, "Error generating tool arguments via LLM")
                val errorMsg = "Error generating tool arguments: ${e.message}"
                emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
                emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg)))
                return@flow
            }

            val parsedSelection = parseToolSelection(accumulatedResponse.toString())
            if (parsedSelection != null && parsedSelection.first == toolNameConfig) {
                resolvedToolName = parsedSelection.first
                resolvedToolArgs = parsedSelection.second
            } else {
                resolvedToolName = toolNameConfig
                resolvedToolArgs =
                    parseToolArguments(accumulatedResponse.toString()) ?: accumulatedResponse.toString().trim()
            }
        }

        // `getRisk` throws `IllegalArgumentException` when the tool isn't in the
        // catalogue — this is reachable in auto-select mode if the LLM
        // hallucinates a tool name, or if a tool was unregistered between
        // discovery and execution. Surface a structured `NodeExecutionResult`
        // error instead of letting the exception terminate the pipeline.
        val risk = try {
            toolRepository.getRisk(resolvedToolName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("PipelineDebug").e(e, "Risk lookup failed for tool '$resolvedToolName'")
            val errorMsg = "Risk lookup failed for tool $resolvedToolName: ${e.message}"
            emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
            emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg, resolvedToolName = resolvedToolName)))
            return@flow
        }
        val globalConfirmationOverride = settingsRepository.requiresUserConfirmation.first()
        val needsApproval = when (risk) {
            ToolRisk.READ_ONLY -> globalConfirmationOverride
            ToolRisk.SENSITIVE, ToolRisk.DESTRUCTIVE -> true
        }
        var isApproved = true

        if (needsApproval) {
            emit(
                NodeOutput.State(
                    AgentOrchestratorState.WaitingForApproval(resolvedToolName, resolvedToolArgs, risk),
                ),
            )
            approvalNotifier.sendApprovalRequest(sessionId, resolvedToolName, resolvedToolArgs, risk)

            // Register deferred before any suspension point so a fast approval is not dropped
            val deferred = CompletableDeferred<Boolean>()
            activeApprovalDeferreds[sessionId] = deferred
            val timeoutMs = settingsRepository.toolCallTimeoutMs.first()
            isApproved = try {
                withTimeout(timeoutMs) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                activeApprovalDeferreds.remove(sessionId)
                Timber.tag("PipelineDebug").w("Approval timed out for session: $sessionId")
                emit(NodeOutput.State(AgentOrchestratorState.Error("Approval request timed out")))
                emit(NodeOutput.Result(NodeExecutionResult(error = "Approval request timed out")))
                return@flow
            }

            if (!isApproved) {
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.SYSTEM,
                        content = "User denied execution of tool: $resolvedToolName",
                        timestamp = System.currentTimeMillis(),
                        isFinal = false,
                    ),
                )
                emit(
                    NodeOutput.State(
                        AgentOrchestratorState.ObservationResult(resolvedToolName, "Execution denied by user"),
                    ),
                )
                emit(
                    NodeOutput.Result(
                        NodeExecutionResult(
                            outputText = "Execution denied by user",
                            resolvedToolName = resolvedToolName,
                        ),
                    ),
                )
                return@flow
            }
        }

        emit(NodeOutput.State(AgentOrchestratorState.ExecutingTool(resolvedToolName, resolvedToolArgs)))
        val result = try {
            toolRepository.executeTool(resolvedToolName, resolvedToolArgs)
        } catch (e: CancellationException) {
            // Preserve structured-concurrency cancellation: tool execution may suspend,
            // and a broad `catch (Exception)` would silently swallow the cancel.
            throw e
        } catch (e: Exception) {
            Timber.tag(
                "PipelineDebug",
            ).e(e, "[NODE_ERR] type=${node.type.name} id=${node.id} error executing tool: $resolvedToolName")
            "Error executing $resolvedToolName: ${e.message}"
        }

        emit(NodeOutput.State(AgentOrchestratorState.ObservationResult(resolvedToolName, result)))
        chatRepository.saveMessage(
            ChatMessage(
                sessionId = sessionId,
                role = Role.SYSTEM,
                content = "Observation from $resolvedToolName: $result",
                timestamp = System.currentTimeMillis(),
                isFinal = false,
            ),
        )
        emit(NodeOutput.Result(NodeExecutionResult(outputText = result, resolvedToolName = resolvedToolName)))
    }

    @androidx.annotation.VisibleForTesting
    internal fun parseToolSelection(response: String): Pair<String, String>? {
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
            val tool = if (jsonObject.has("tool")) jsonObject.getString("tool") else null
            val args = if (jsonObject.has("arguments")) {
                val argsObj = jsonObject.get("arguments")
                if (argsObj is org.json.JSONObject || argsObj is org.json.JSONArray) {
                    argsObj.toString()
                } else {
                    argsObj.toString()
                }
            } else {
                null
            }

            if (tool != null && args != null) {
                Pair(tool, args)
            } else {
                null
            }
        } catch (e: org.json.JSONException) {
            Timber.tag("PipelineDebug").e(e, "Error parsing tool selection JSON")
            null
        }
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
            Timber.tag("PipelineDebug").e(e, "Error parsing tool arguments JSON")
            null
        }
    }
}
