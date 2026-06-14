package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.ExecutionScope
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.PendingDecision
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.models.ToolApprovalPolicy
import app.knotwork.android.domain.models.ToolExecutionContext
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.services.ApprovalNotifier
import app.knotwork.android.domain.usecases.LoadModelUseCase
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
 * Executor for [NodeType.TOOL][app.knotwork.android.domain.models.NodeType.TOOL] nodes.
 *
 * Resolves which tool to run (either the explicit `node.toolName` or LLM-driven auto
 * selection via [DefaultPrompts.Tool.AUTO_SELECT_TEMPLATE]), builds its arguments, and
 * dispatches the call through [ToolRepository]. Before dispatching, the executor goes
 * through a Human-in-the-Loop (HITL) gate driven by the resolved tool's
 * [ToolRisk][app.knotwork.android.domain.models.ToolRisk] (canonical source:
 * [ToolRepository.getRisk][app.knotwork.android.domain.repositories.ToolRepository.getRisk]):
 *
 *  - [READ_ONLY][app.knotwork.android.domain.models.ToolRisk.READ_ONLY] — runs without a
 *    prompt unless the user has globally enabled `requiresUserConfirmation`, which
 *    acts as an "ask on every tool call" override.
 *  - [SENSITIVE][app.knotwork.android.domain.models.ToolRisk.SENSITIVE] — always prompts.
 *  - [DESTRUCTIVE][app.knotwork.android.domain.models.ToolRisk.DESTRUCTIVE] — always
 *    prompts; the notification fallback uses a distinct high-importance channel.
 *
 * When the gate fires, the executor:
 *
 * 1. publishes [WaitingForApproval][app.knotwork.android.domain.models.AgentOrchestratorState.WaitingForApproval];
 * 2. sends a notification via [ApprovalNotifier] (channel / icon picked by risk);
 * 3. suspends on a [CompletableDeferred] keyed by `sessionId`, bounded by
 *    [SettingsRepository.toolCallTimeoutMs][app.knotwork.android.domain.repositories.SettingsRepository.toolCallTimeoutMs];
 * 4. resumes when [resumeWithApproval] is called from `MainActivity` /
 *    `AgentApprovalReceiver`.
 *
 * The live deferred is only the first phase of a two-phase wait. When the
 * live phase times out on a persisted run, the executor parks the run
 * instead of failing it: the request snapshot becomes a durable
 * [PendingInteraction], a persistent notification replaces the transient
 * one, and the flow ends with
 * [SuspendedInBackground][app.knotwork.android.domain.models.AgentOrchestratorState.SuspendedInBackground]
 * while the run record keeps its `WAITING_APPROVAL` status. The user's later
 * decision is recorded onto the pending record and the run is resumed from
 * its checkpoint; this executor then consumes the record one-shot before
 * raising a new gate. Consumption is TOCTOU-guarded: the recorded decision
 * only applies when the re-resolved tool name and arguments match the parked
 * snapshot exactly — anything else discards the stale record and raises a
 * fresh approval. Runs without a persistent record (editor test runs) keep
 * the legacy fail-fast timeout.
 *
 * Tool observations and "Execution denied by user" markers are persisted as
 * non-final [SYSTEM][app.knotwork.android.domain.models.Role.SYSTEM] chat messages so the
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
    private val pendingInteractionRepository: PendingInteractionRepository,
) : NodeExecutor {

    private val activeApprovalDeferreds = ConcurrentHashMap<String, PendingApprovalHolder>()

    /**
     * Pairs the suspension primitive of one pending approval with the request
     * snapshot it was raised for, so a UI re-attaching to a suspended run can
     * re-render the confirmation card from the authoritative source instead of
     * hoping the per-session state flow's replay cache still holds the
     * [AgentOrchestratorState.WaitingForApproval] emission (console events
     * emitted while the run waits overwrite it — the replay depth is 1).
     *
     * @property deferred completes with the user's decision via [resumeWithApproval].
     * @property request the exact approval request published to the UI.
     */
    private data class PendingApprovalHolder(
        val deferred: CompletableDeferred<Boolean>,
        val request: AgentOrchestratorState.WaitingForApproval,
    )

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
        val holder = activeApprovalDeferreds.remove(sessionId)
        holder?.deferred?.complete(isApproved)
    }

    /**
     * Returns the approval request the run of [sessionId] is currently suspended
     * on, or `null` when no approval gate is active for that session.
     *
     * Backs the chat reattach protocol: when a session is reopened while its
     * persistent run record reads `WAITING_APPROVAL`, the UI restores the HITL
     * confirmation card from this snapshot — the per-session state flow cannot
     * be relied on for it because its replay cache (depth 1) is overwritten by
     * console events emitted after the suspension.
     *
     * @param sessionId chat session id used as the lookup key.
     * @return the pending [AgentOrchestratorState.WaitingForApproval], or `null`.
     */
    fun pendingApprovalFor(sessionId: String): AgentOrchestratorState.WaitingForApproval? =
        activeApprovalDeferreds[sessionId]?.request

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

        // `getRisk` throws `IllegalArgumentException` when the tool isn't in the
        // catalogue — this is reachable in auto-select mode if the LLM
        // hallucinates a tool name, or if a tool was unregistered between
        // discovery and execution. Surface a structured `NodeExecutionResult`
        // error instead of letting the exception terminate the pipeline.
        val risk = try {
            toolRepository.getRisk(resolvedToolName, resolvedToolArgs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("PipelineDebug").e(e, "Risk lookup failed for tool '$resolvedToolName'")
            val errorMsg = "Risk lookup failed for tool $resolvedToolName: ${e.message}"
            emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
            emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg, resolvedToolName = resolvedToolName)))
            return@flow
        }
        // Hard-deny gate: when the user has opted to block destructive tools
        // outright, refuse the call before even staging the HITL prompt.
        //
        // The denial is surfaced as a `NodeExecutionResult.error` (NOT
        // `outputText`) so the orchestrator treats the node as failed and
        // the upstream planner sees a tool-failure signal rather than a
        // successful observation. Without that distinction the LLM would
        // hallucinate that the destructive action succeeded and could
        // loop back to retry the same call.
        if (risk == ToolRisk.DESTRUCTIVE && settingsRepository.blockDestructiveTools.first()) {
            val message = "Destructive tools are blocked by Settings — $resolvedToolName was not executed."
            chatRepository.saveMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = Role.SYSTEM,
                    content = message,
                    timestamp = System.currentTimeMillis(),
                    isFinal = false,
                ),
            )
            emit(NodeOutput.State(AgentOrchestratorState.Error(message)))
            emit(
                NodeOutput.Result(
                    NodeExecutionResult(
                        error = message,
                        resolvedToolName = resolvedToolName,
                    ),
                ),
            )
            return@flow
        }
        val approvalPolicy = settingsRepository.toolApprovalPolicy.first()
        val needsApproval = when (approvalPolicy) {
            ToolApprovalPolicy.AllCalls -> true
            ToolApprovalPolicy.NeverPrompt -> false
            ToolApprovalPolicy.SensitiveOrDestructive -> risk == ToolRisk.SENSITIVE || risk == ToolRisk.DESTRUCTIVE
        }
        var isApproved = true

        if (needsApproval) {
            val parkedDecision = consumeParkedDecision(runId, resolvedToolName, resolvedToolArgs)
            if (parkedDecision != null) {
                // A resumed run carries the user's one-shot decision for this
                // exact request snapshot — apply it without raising a new gate.
                isApproved = parkedDecision == PendingDecision.APPROVED
            } else {
                val approvalRequest =
                    AgentOrchestratorState.WaitingForApproval(resolvedToolName, resolvedToolArgs, risk)
                emit(NodeOutput.State(approvalRequest))
                approvalNotifier.sendApprovalRequest(sessionId, resolvedToolName, resolvedToolArgs, risk)

                // Register deferred before any suspension point so a fast approval is not dropped
                val deferred = CompletableDeferred<Boolean>()
                val holder = PendingApprovalHolder(deferred, approvalRequest)
                activeApprovalDeferreds[sessionId] = holder
                val timeoutMs = settingsRepository.toolCallTimeoutMs.first()
                isApproved = try {
                    withTimeout(timeoutMs) { deferred.await() }
                } catch (e: TimeoutCancellationException) {
                    Timber.tag("PipelineDebug").w("Live approval phase timed out for session: $sessionId")
                    if (runId != null && parkRun(runId, sessionId, resolvedToolName, resolvedToolArgs, risk)) {
                        // Two-phase wait, second phase: the run parks on its
                        // durable pending record instead of failing. No
                        // NodeOutput.Result on purpose — the engine stops the
                        // walk and the run record stays WAITING_APPROVAL.
                        emit(
                            NodeOutput.State(
                                AgentOrchestratorState.SuspendedInBackground(PendingInteractionKind.APPROVAL),
                            ),
                        )
                    } else {
                        // Non-persisted runs (editor test runs) and storage
                        // failures keep the legacy fail-fast semantics: a park
                        // without a durable record would be unrecoverable.
                        emit(NodeOutput.State(AgentOrchestratorState.Error("Approval request timed out")))
                        emit(NodeOutput.Result(NodeExecutionResult(error = "Approval request timed out")))
                    }
                    return@flow
                } finally {
                    // Covers every exit: timeout, resume (already removed — the
                    // two-arg remove is then a no-op), and plain cancellation of
                    // the suspended gate (scope teardown, an abandoned editor
                    // test run). Without this, a leaked holder would keep
                    // serving pendingApprovalFor a request no coroutine can
                    // ever settle. remove(key, value) leaves a newer
                    // registration for the same session untouched.
                    activeApprovalDeferreds.remove(sessionId, holder)
                }
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
            // The context carries the engine-known session id so tools that
            // bind follow-up work to the conversation (schedule_task) get it
            // from a source the LLM-emitted arguments cannot spoof.
            toolRepository.executeTool(resolvedToolName, resolvedToolArgs, ToolExecutionContext(sessionId))
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

    /**
     * Consumes the parked approval record of a resumed run, one-shot.
     *
     * The record never survives its first consumption attempt: whatever the
     * outcome, it is deleted so a stale decision can never authorise a later
     * call. The recorded decision applies only under the TOCTOU guard — the
     * re-resolved tool name and arguments must match the parked snapshot
     * exactly; the auto-select / argument-generation LLM passes are not
     * deterministic, and a decision the user gave for one concrete call must
     * not leak onto a different one.
     *
     * @param runId Id of the executing run, or `null` for non-persisted runs.
     * @param resolvedToolName Tool name resolved by this execution.
     * @param resolvedToolArgs Argument string resolved by this execution.
     * @return The user's decision when it may be applied, or `null` when a
     *   fresh approval gate must be raised (no record, undecided record, or
     *   TOCTOU mismatch).
     */
    private suspend fun consumeParkedDecision(
        runId: String?,
        resolvedToolName: String,
        resolvedToolArgs: String,
    ): PendingDecision? {
        if (runId == null) return null
        val parked = pendingInteractionRepository.getForRun(runId) ?: return null
        if (parked.kind != PendingInteractionKind.APPROVAL) return null
        pendingInteractionRepository.delete(runId)
        val argsMatch = parked.toolName == resolvedToolName && parked.toolArgs == resolvedToolArgs
        if (!argsMatch) {
            Timber.tag("PipelineDebug").w(
                "Parked approval of run %s resolved to a different call (%s) — raising a fresh gate",
                runId,
                resolvedToolName,
            )
        }
        return parked.decision?.takeIf { argsMatch }
    }

    /**
     * Parks the run in its persistent waiting phase: persists the approval
     * request snapshot as a [PendingInteraction] and, when durable, replaces
     * the transient approval notification with the persistent one.
     *
     * @param runId Id of the persisted run being parked.
     * @param sessionId Id of the owning chat session.
     * @param toolName Tool name of the staged call.
     * @param arguments Argument string of the staged call.
     * @param risk Risk classification of the staged call.
     * @return `true` when the park is durable; `false` when the caller must
     *   fall back to failing the run.
     */
    private suspend fun parkRun(
        runId: String,
        sessionId: String,
        toolName: String,
        arguments: String,
        risk: ToolRisk,
    ): Boolean {
        val saved = pendingInteractionRepository.save(
            PendingInteraction(
                runId = runId,
                sessionId = sessionId,
                kind = PendingInteractionKind.APPROVAL,
                toolName = toolName,
                toolArgs = arguments,
                risk = risk,
                requestedAt = System.currentTimeMillis(),
            ),
        )
        if (saved) {
            approvalNotifier.sendPersistentApprovalRequest(runId, sessionId, toolName, arguments, risk)
        }
        return saved
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
