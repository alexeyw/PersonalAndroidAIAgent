package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ExecutionScope
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.usecases.LoadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executor for [NodeType.TOOL][app.knotwork.android.domain.models.NodeType.TOOL] nodes.
 *
 * Resolves which tool to run (either the explicit `node.toolName` or LLM-driven auto
 * selection via [DefaultPrompts.Tool.AUTO_SELECT_TEMPLATE]), builds its arguments, and
 * hands the resolved call to the shared [ToolInvocationGate], which owns the
 * Human-in-the-Loop (HITL) risk / approval / parking contract and the actual dispatch
 * through [ToolRepository]. The gate is shared with [SkillNodeExecutor] so a skill that
 * initiates a tool call can never weaken that contract.
 *
 * Tool observations and "Execution denied by user" markers are persisted by the gate as
 * non-final [SYSTEM][app.knotwork.android.domain.models.Role.SYSTEM] chat messages so the
 * agent console can replay them without polluting the user-facing chat list.
 *
 * Marked `@Singleton` for parity with its previous lifetime; all per-session suspension
 * state now lives in the [ToolInvocationGate] singleton.
 */
@Singleton
class ToolNodeExecutor @Inject constructor(
    private val llmEngine: LlmInferenceEngine,
    private val loadModelUseCase: LoadModelUseCase,
    private val toolRepository: ToolRepository,
    private val toolInvocationGate: ToolInvocationGate,
) : NodeExecutor {

    /**
     * Completes the suspended approval request for [sessionId] with the user's decision.
     * Delegates to the shared [ToolInvocationGate]; kept here so existing callers
     * (`GraphExecutionEngine`, the AppFunctions E2E entry point) keep working unchanged.
     *
     * @param sessionId chat session id whose pending approval is being resolved.
     * @param isApproved `true` if the user approved tool execution, `false` to deny it.
     */
    fun resumeWithApproval(sessionId: String, isApproved: Boolean) =
        toolInvocationGate.resumeWithApproval(sessionId, isApproved)

    /**
     * Returns the approval request the run of [sessionId] is currently suspended on, or
     * `null` when no approval gate is active. Delegates to the shared [ToolInvocationGate].
     *
     * @param sessionId chat session id used as the lookup key.
     * @return the pending [AgentOrchestratorState.WaitingForApproval], or `null`.
     */
    fun pendingApprovalFor(sessionId: String): AgentOrchestratorState.WaitingForApproval? =
        toolInvocationGate.pendingApprovalFor(sessionId)

    /**
     * Test-only readiness probe; delegates to the shared [ToolInvocationGate].
     */
    @androidx.annotation.VisibleForTesting
    internal fun hasPendingApproval(sessionId: String): Boolean = toolInvocationGate.hasPendingApproval(sessionId)

    @Suppress("LongMethod")
    override fun execute(
        node: NodeModel,
        inputText: String,
        sessionId: String,
        originalPrompt: String,
        runId: String?,
        scope: ExecutionScope,
    ): Flow<NodeOutput> = flow {
        val toolNameConfig = node.toolName
        // A blank / null tool name is the "Auto" selection — the editor's empty
        // tool option is persisted as `null` (see `NodeConfigCodec.apply`), and
        // it means the same thing as the explicit "auto" sentinel: let the LLM
        // pick a tool from the registry at runtime. Only a *configured but
        // unknown* tool name is a real error, handled in the else branch below.
        val isAutoSelect = toolNameConfig.isNullOrBlank() || toolNameConfig.equals("auto", ignoreCase = true)

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

        if (isAutoSelect) {
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

        toolInvocationGate.dispatch(
            collector = this,
            nodeType = node.type.name,
            nodeId = node.id,
            sessionId = sessionId,
            runId = runId,
            resolvedToolName = resolvedToolName,
            resolvedToolArgs = resolvedToolArgs,
        )
    }

    @androidx.annotation.VisibleForTesting
    internal fun parseToolSelection(response: String): Pair<String, String>? =
        ToolCallParser.parseToolSelection(response)

    @androidx.annotation.VisibleForTesting
    internal fun parseToolArguments(response: String): String? = ToolCallParser.parseToolArguments(response)
}
