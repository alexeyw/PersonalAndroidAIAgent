package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.PendingDecision
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.models.ToolApprovalPolicy
import app.knotwork.android.domain.models.ToolExecutionContext
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.services.ApprovalNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared Human-in-the-Loop (HITL) tool-dispatch gate. Owns the single seam
 * through which any node that wants to *run* a resolved tool call must pass —
 * currently [ToolNodeExecutor] (which resolves the tool via LLM selection) and
 * [SkillNodeExecutor] (which dispatches a tool a skill initiated, after an
 * allowlist pre-check).
 *
 * Extracting the gate keeps the risk / approval / parking / reattach
 * semantics in one place so the two callers cannot diverge: a SKILL node can
 * never weaken the tool risk / confirmation contract, because it goes through
 * exactly the same code path as a TOOL node.
 *
 * Given an already-resolved `(toolName, arguments)` pair the gate:
 *
 *  1. resolves the effective [ToolRisk] (canonical source: [ToolRepository.getRisk]);
 *  2. hard-denies the call when it is [ToolRisk.DESTRUCTIVE] and the user has
 *     blocked destructive tools in Settings;
 *  3. raises a HITL approval gate according to the [ToolApprovalPolicy], using
 *     the two-phase live-deferred → durable-park protocol;
 *  4. executes the tool through [ToolRepository] and emits the observation.
 *
 * Marked `@Singleton` because [activeApprovalDeferreds] holds per-session
 * pending approval requests that must outlive any individual node execution
 * and be reachable from the UI / notification resume path regardless of which
 * executor raised the gate.
 */
@Singleton
class ToolInvocationGate @Inject constructor(
    private val toolRepository: ToolRepository,
    private val settingsRepository: SettingsRepository,
    private val approvalNotifier: ApprovalNotifier,
    private val chatRepository: ChatRepository,
    private val pendingInteractionRepository: PendingInteractionRepository,
) {

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
        // Settle any approval notification for this session. When the decision is
        // made from the in-chat card (the common case) the live-phase notification
        // — posted while the app was backgrounded — would otherwise keep hanging in
        // the shade offering a choice that has already been made. Idempotent: a
        // no-op when nothing was posted (e.g. the request was answered while the
        // chat was on screen and the notification was suppressed to begin with).
        approvalNotifier.cancelApprovalNotification(sessionId)
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
     * Test-only readiness probe: returns `true` once the gate has registered a
     * `CompletableDeferred` for [sessionId] and is suspended awaiting the user's
     * decision. Instrumented tests use this to synchronise their `resumeWithApproval`
     * call deterministically — without it the test would have to rely on a fixed
     * `delay(...)`, which races the deferred registration on slow / overloaded
     * emulators.
     */
    @androidx.annotation.VisibleForTesting
    internal fun hasPendingApproval(sessionId: String): Boolean = activeApprovalDeferreds.containsKey(sessionId)

    /**
     * Runs a resolved tool call through the full HITL gate and emits the outcome
     * onto [collector].
     *
     * Emits intermediate [NodeOutput.State] progress and terminates with exactly
     * one [NodeOutput.Result] — except when the run parks in its persistent
     * waiting phase, in which case the flow ends after a
     * [AgentOrchestratorState.SuspendedInBackground] state without a result
     * (the engine then stops the walk and the run record stays `WAITING_APPROVAL`).
     *
     * @param collector the executor's flow collector to emit states / results into.
     * @param nodeType node type name, used only for error log attribution.
     * @param nodeId node id, used only for error log attribution.
     * @param sessionId chat session the run belongs to.
     * @param runId persistent run id, or `null` for non-persisted (editor test) runs.
     * @param resolvedToolName the tool to run.
     * @param resolvedToolArgs the tool's argument JSON string.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    suspend fun dispatch(
        collector: FlowCollector<NodeOutput>,
        nodeType: String,
        nodeId: String,
        sessionId: String,
        runId: String?,
        resolvedToolName: String,
        resolvedToolArgs: String,
    ) = with(collector) {
        // `getRisk` throws `IllegalArgumentException` when the tool isn't in the
        // catalogue — this is reachable if the LLM hallucinates a tool name, or
        // if a tool was unregistered between discovery and execution. Surface a
        // structured `NodeExecutionResult` error instead of letting the
        // exception terminate the pipeline.
        val risk = try {
            toolRepository.getRisk(resolvedToolName, resolvedToolArgs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("PipelineDebug").e(e, "Risk lookup failed for tool '$resolvedToolName'")
            val errorMsg = "Risk lookup failed for tool $resolvedToolName: ${e.message}"
            emit(NodeOutput.State(AgentOrchestratorState.Error(errorMsg)))
            emit(NodeOutput.Result(NodeExecutionResult(error = errorMsg, resolvedToolName = resolvedToolName)))
            return@with
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
            return@with
        }
        val approvalPolicy = settingsRepository.toolApprovalPolicy.first()
        val needsApproval = when (approvalPolicy) {
            ToolApprovalPolicy.AllCalls -> true
            ToolApprovalPolicy.NeverPrompt -> false
            ToolApprovalPolicy.SensitiveOrDestructive -> risk == ToolRisk.SENSITIVE || risk == ToolRisk.DESTRUCTIVE
        }
        var isApproved = true

        // Consume any parked approval decision up-front, regardless of the
        // current policy. A run can park under SensitiveOrDestructive/AllCalls
        // and then be resumed after the user relaxed the policy to NeverPrompt —
        // then [needsApproval] is false and the durable record would otherwise
        // never be deleted (orphaned until the maintenance sweep). Consuming it
        // here clears the record in that case; when approval IS still required
        // the captured decision drives the gate exactly as before.
        val parkedDecision = consumeParkedDecision(runId, resolvedToolName, resolvedToolArgs)

        if (needsApproval) {
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
                    return@with
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
                return@with
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
            ).e(e, "[NODE_ERR] type=$nodeType id=$nodeId error executing tool: $resolvedToolName")
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
}
