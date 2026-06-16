package app.knotwork.android.domain.engine

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.constants.PipelineExecutionDefaults
import app.knotwork.android.domain.engine.executors.NodeExecutorFactory
import app.knotwork.android.domain.engine.executors.ToolNodeExecutor
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ConsoleEvent
import app.knotwork.android.domain.models.ConsoleEventType
import app.knotwork.android.domain.models.ExecutionScope
import app.knotwork.android.domain.models.MemoryChunk
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.ResumeContext
import app.knotwork.android.domain.models.RunStepBudget
import app.knotwork.android.domain.models.RunTraceRecord
import app.knotwork.android.domain.models.ToolInvocationResult
import app.knotwork.android.domain.models.usesContextConfig
import app.knotwork.android.domain.prompt.PromptTemplateEngine
import app.knotwork.android.domain.prompt.PromptVariableProvider
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.CrashReportingRepository
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.MetricsRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.RunTraceRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.RetrieveRelevantMemoryUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine responsible for executing a given [PipelineGraph].
 * It traverses nodes starting from [NodeType.INPUT], evaluates conditions,
 * executes LLM inference, triggers tools, and reaches [NodeType.OUTPUT].
 */
@Singleton
class GraphExecutionEngine @Inject constructor(
    private val nodeExecutorFactory: NodeExecutorFactory,
    private val toolNodeExecutor: ToolNodeExecutor,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val metricsRepository: MetricsRepository,
    private val promptTemplateEngine: PromptTemplateEngine,
    private val promptVariableProviders: Set<@JvmSuppressWildcards PromptVariableProvider>,
    private val nodeContextBuilder: NodeContextBuilder,
    private val retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase,
    private val crashReportingRepository: CrashReportingRepository,
    private val localModelRepository: LocalModelRepository,
    private val memoryRepository: MemoryRepository,
    private val pipelineRunRepository: PipelineRunRepository,
    private val runTraceRepository: RunTraceRepository,
) {

    /**
     * Resumes execution after user approval.
     */
    fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        toolNodeExecutor.resumeWithApproval(sessionId, isApproved)
    }

    /**
     * Returns the approval request the run of [sessionId] is currently
     * suspended on, or `null` when no approval gate is active. Mirrors
     * [resumeWithApproval] — both delegate to the [ToolNodeExecutor]
     * singleton that owns the per-session suspension primitives. Used by the
     * chat reattach protocol to restore the HITL confirmation card from the
     * authoritative pending snapshot.
     *
     * @param sessionId chat session id whose pending approval is queried.
     * @return the pending [AgentOrchestratorState.WaitingForApproval], or `null`.
     */
    fun pendingApprovalFor(sessionId: String): AgentOrchestratorState.WaitingForApproval? =
        toolNodeExecutor.pendingApprovalFor(sessionId)

    /**
     * Executes the graph by processing nodes sequentially.
     *
     * @param sessionId Id of the chat session the run belongs to.
     * @param userPrompt The user message that started the run.
     * @param graph The pipeline graph to execute.
     * @param runId Id of the persistent pipeline-run record this execution
     *   writes its progress into (the node currently executing and the
     *   WAITING_APPROVAL / WAITING_CLARIFICATION suspension statuses) and
     *   whose persistent trace receives every console event and per-node
     *   I/O snapshot (buffered, force-flushed at suspension and terminal
     *   points). `null` disables run persistence entirely — terminal statuses
     *   and the RUNNING transition are owned by the task queue, never by the
     *   engine.
     * @param resume Checkpoint payload that switches the walk into resume
     *   mode (see [ResumeContext]): while its seq-ordered record cursor is
     *   not exhausted, each visited non-INPUT/OUTPUT node consumes the next
     *   recorded snapshot instead of executing — the recorded output and
     *   routing verdicts drive the very same control-flow code live results
     *   would, so branches, queue iterations and inter-node inputs are
     *   re-derived deterministically without re-running executors. The first
     *   node without a record executes live; in particular a TOOL node the
     *   run died on raises a fresh HITL approval (see the TOOL asymmetry
     *   contract on [ResumeContext]). Requires a non-null [runId] — resuming
     *   without the persistent record/trace to continue makes no sense.
     *   `null` (the default) is a normal fresh run.
     * @param depth Pipeline-nesting depth of this run: `0` for a top-level run,
     *   and `parentDepth + 1` when `PipelineNodeExecutor` re-enters the engine to
     *   run a sub-pipeline. The value is forwarded to every node executor (inside
     *   [ExecutionScope]); only the PIPELINE executor consumes it (to enforce the
     *   runtime nesting ceiling and to thread the next depth into its own
     *   recursive call). It also stamps every console/trace record so the console
     *   can render nested sub-pipeline output as a hierarchy.
     * @param stepBudget The step budget shared across the whole run tree. `null`
     *   (the default, used by top-level callers) makes the engine seed a fresh
     *   [RunStepBudget] from [SettingsRepository.pipelineMaxSteps]; a sub-pipeline
     *   invocation passes the parent's budget so the nested run decrements the
     *   same ceiling instead of getting a private allowance. Exhaustion at any
     *   depth fails the run with the max-steps error, which propagates up the
     *   stack as the parent PIPELINE node's error.
     * @return A cold flow of orchestrator states describing the run.
     */
    // Reason: this is the agent's core orchestrator. It is a long single
    // state machine that walks the DAG, dispatches per node type, manages
    // queue/clarification/approval suspensions, emits typed orchestrator
    // states, and surfaces console events. Decomposition into helpers
    // historically obscured the linear flow; the method body is structured
    // and well-commented in place.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    operator fun invoke(
        sessionId: String,
        userPrompt: String,
        graph: PipelineGraph,
        runId: String? = null,
        resume: ResumeContext? = null,
        depth: Int = 0,
        stepBudget: RunStepBudget? = null,
    ): Flow<AgentOrchestratorState> = flow {
        // Buffer of console events accumulated for this run. The engine emits a
        // fresh `ConsoleLog` snapshot on every append so the UI reactively
        // updates the collapsed/expanded console panels.
        val consoleEvents = mutableListOf<ConsoleEvent>()

        // Monotonic position of the next trace record within this run, shared
        // by console events and per-node I/O snapshots. Uniqueness per run is
        // what lets the console deduplicate the replay/live seam by seq. A
        // resumed run continues the interrupted run's numbering instead of
        // colliding with its persisted records.
        var traceSeq = resume?.nextSeq ?: 0L

        // Position of the next checkpoint record to replay; meaningful only
        // in resume mode. Once it reaches the end of the recorded prefix the
        // walk is live for the rest of the run.
        var replayCursor = 0

        suspend fun pushConsole(type: ConsoleEventType, message: String) {
            // A nested sub-pipeline run prefixes its console lines with the
            // sub-pipeline name so the merged console reads as `[Translator] ▶ …`
            // even before indentation; [depth] additionally drives the indented
            // rendering. Top-level runs keep the bare message.
            val displayMessage = if (depth > 0) "[${graph.name}] $message" else message
            val event = ConsoleEvent(
                timestamp = System.currentTimeMillis(),
                type = type,
                message = displayMessage,
                seq = traceSeq++,
                depth = depth,
            )
            consoleEvents += event
            // Write-through into the persistent run trace. The repository
            // buffers and batch-flushes, so this never costs a SQLCipher
            // commit per streamed event.
            if (runId != null) {
                runTraceRepository.append(
                    RunTraceRecord.ConsoleEntry(
                        runId = runId,
                        sessionId = sessionId,
                        seq = event.seq,
                        timestamp = event.timestamp,
                        type = type,
                        message = displayMessage,
                        depth = depth,
                    ),
                )
            }
            emit(AgentOrchestratorState.ConsoleLog(consoleEvents.toList(), runId))
        }

        if (!graph.isValidDAG()) {
            // Push the console event BEFORE the terminal Error so the Error
            // remains the last value of the orchestrator state flow.
            // `TaskQueueManagerImpl.processTask` resets the flow to `Idle` in
            // its `finally` if the last value is anything other than
            // `Completed` / `Error`, so a trailing `ConsoleLog` would mask the
            // real failure for observers reading `stateFlow.value`.
            pushConsole(ConsoleEventType.Error, "Pipeline graph contains cycles")
            emit(AgentOrchestratorState.Error("Pipeline graph contains cycles and is invalid."))
            return@flow
        }

        val inputNode = graph.nodes.find { it.type == NodeType.INPUT }
        if (inputNode == null) {
            pushConsole(ConsoleEventType.Error, "Pipeline has no INPUT node")
            emit(AgentOrchestratorState.Error("Pipeline has no INPUT node"))
            return@flow
        }

        // Attach Crashlytics custom keys so any non-fatal recorded from
        // node executors downstream carries the pipeline/model context.
        // No-op when the user has not opted in to crash reporting.
        crashReportingRepository.setCustomKey(CRASH_KEY_PIPELINE_ID, graph.id)
        crashReportingRepository.setCustomKey(
            CRASH_KEY_ACTIVE_MODEL,
            localModelRepository.getActiveModel()?.name ?: ACTIVE_MODEL_NONE,
        )

        val maxSteps = settingsRepository.pipelineMaxSteps.first()
        // Step budget shared across the whole run tree. A top-level run seeds a
        // fresh budget from the setting; a sub-pipeline run reuses the parent's
        // instance (threaded in via [stepBudget]) so nested execution cannot
        // side-step the parent's MAX_STEPS ceiling. Every node visited at any
        // depth decrements it.
        val budget = stepBudget ?: RunStepBudget(maxSteps)
        // Per-`PIPELINE`-node visit counter. A PIPELINE node inside a loop
        // (QUEUE_PROCESSOR) executes once per item; the index disambiguates the
        // child run id of each visit and is re-derived deterministically on
        // resume (it increments on replayed visits too), so the in-flight visit
        // lands on the same index as on the interrupted run.
        val pipelineVisitCounts = mutableMapOf<String, Int>()
        // For deterministic graphs (no routing/queue nodes) the total is fixed from the start.
        // For branching graphs it stays null until the active branch is resolved.
        val hasBranching = graph.nodes.any {
            it.type == NodeType.INTENT_ROUTER ||
                it.type == NodeType.IF_CONDITION ||
                it.type == NodeType.QUEUE_PROCESSOR
        }
        var estimatedTotalSteps: Int? = if (hasBranching) null else graph.nodes.size
        var currentNode: NodeModel? = inputNode
        var stepCount = 0
        var currentInputText = userPrompt

        val activeQueue = mutableListOf<String>()
        var activeQueueProcessorId: String? = null
        val queueResults = mutableListOf<String>()
        val traceSteps = mutableListOf<AgentOrchestratorState.TraceStep>()

        // Long-term memory is retrieved lazily and at most once per run, keyed
        // off the immutable userPrompt. Only the first *executed* node that
        // actually opts into the `--- Long-Term Memory ---` block
        // (`contextConfig.longTermMemory`) triggers the query embedding. A
        // graph where no executed node requests memory never embeds the prompt
        // at all — sparing avoidable embedding-provider latency/cost and not
        // shipping the prompt to a cloud embedding backend the user did not ask
        // memory for. A resumed run is seeded from the interrupted run's
        // persisted snapshot, so it neither re-runs retrieval (the context
        // must be identical to the interrupted one) nor re-counts usage.
        var memoizedMemories: List<MemoryChunk>? = resume?.memorySnapshot
        suspend fun resolveMemoriesOnce(): List<MemoryChunk> {
            memoizedMemories?.let { return it }
            val scored: List<Pair<MemoryChunk, Float>> = try {
                retrieveRelevantMemoryUseCase.retrieveScored(userPrompt)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Memory retrieval suspends (embedding + DB lookup). Swallowing
                // cancellation here would let the parent flow keep running after
                // the caller cancelled, breaking structured concurrency.
                throw e
            } catch (e: Exception) {
                Timber.tag("PipelineDebug").w(e, "Failed to retrieve long-term memories; continuing without them")
                emptyList()
            }
            val verbose = settingsRepository.verboseMemoryLoggingEnabled.first()
            pushConsole(
                ConsoleEventType.MemoryAccess,
                MemoryAccessLogFormatter.format(query = userPrompt, hits = scored, verbose = verbose),
            )
            val hits = scored.map { it.first }
            // Record that these chunks were injected into this run so the
            // Memory detail sheet can show "Used in N replies". Best-effort:
            // a failure here must never break the pipeline run.
            try {
                memoryRepository.recordUsage(hits.map { it.id }, System.currentTimeMillis())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("PipelineDebug").w(e, "Failed to record memory usage; continuing")
            }
            // Persist the resolved chunks so a checkpoint resume of this run
            // can seed its memory from the snapshot instead of re-running
            // retrieval — the resumed context must be identical to this one.
            if (runId != null) {
                runTraceRepository.append(
                    RunTraceRecord.MemorySnapshot(
                        runId = runId,
                        sessionId = sessionId,
                        seq = traceSeq++,
                        timestamp = System.currentTimeMillis(),
                        entries = hits,
                    ),
                )
            }
            return hits.also { memoizedMemories = it }
        }
        // Tool invocations are accumulated as TOOL nodes complete and surfaced
        // via the `--- Tool Results ---` block on later nodes that opt in.
        val toolInvocationResults = mutableListOf<ToolInvocationResult>()

        // Tracks whether the persistent run record currently sits in a
        // WAITING_* suspension status, so the first state forwarded after
        // the suspension resolves flips the record back to RUNNING.
        var runSuspended = false

        while (currentNode != null && budget.remaining > 0) {
            // Charge this node against the shared run-tree budget before doing
            // any work. Replayed nodes are charged too (the budget is not
            // persisted across resume — see [RunStepBudget]).
            budget.remaining--
            stepCount++

            // Visit index of this node when it is a PIPELINE node — incremented
            // on every entry (replayed or live) so the counter stays aligned
            // through a resume replay and the live visit gets the right index.
            val pipelineVisitIndex = if (currentNode.type == NodeType.PIPELINE) {
                val idx = pipelineVisitCounts.getOrDefault(currentNode.id, 0)
                pipelineVisitCounts[currentNode.id] = idx + 1
                idx
            } else {
                0
            }

            // Record the node about to execute so an interrupted run can
            // report where it stopped. The repository is best-effort by
            // contract — a storage failure never aborts the run itself.
            if (runId != null) {
                pipelineRunRepository.updateCurrentNode(runId, currentNode.id)
            }

            // Emit current step with dynamically estimated total (null = still unknown).
            emit(
                AgentOrchestratorState.PipelineStage(
                    AgentOrchestratorState.PipelineStepInfo(
                        stepIndex = stepCount,
                        totalSteps = estimatedTotalSteps,
                        nodeName = currentNode.type.name,
                    ),
                ),
            )
            // Checkpoint replay decision: while the resume cursor still holds
            // records, the recorded snapshot of this node substitutes for
            // execution. INPUT and OUTPUT nodes are never recorded (the trace
            // skips them by design), so they always run their executors even
            // mid-replay — INPUT is a pure passthrough, and a recorded OUTPUT
            // cannot exist for an interrupted run.
            val replayRecord = if (resume != null &&
                replayCursor < resume.records.size &&
                currentNode.type != NodeType.INPUT &&
                currentNode.type != NodeType.OUTPUT
            ) {
                resume.records[replayCursor]
            } else {
                null
            }
            if (replayRecord != null && replayRecord.nodeId != currentNode.id) {
                // The persisted prefix diverged from the graph walk: the trace
                // cannot serve as a checkpoint (corruption, or an edit that
                // slipped past hash validation). Failing loudly beats silently
                // executing a half-replayed run on inconsistent inputs.
                pushConsole(
                    ConsoleEventType.Error,
                    "Checkpoint trace diverged at ${currentNode.type.name}; resume aborted",
                )
                emit(
                    AgentOrchestratorState.Error(
                        "Recorded checkpoint no longer matches the pipeline graph. Restart the task instead.",
                    ),
                )
                return@flow
            }

            var nodeResult: NodeExecutionResult? = null
            val executorInput: String
            val nodeDurationMs: Long

            if (replayRecord != null) {
                // Replay branch: the node completed before the interruption —
                // its recorded output (and routing verdicts) feed the same
                // control flow a live result would. No executor call, no
                // metrics, no new NodeIo trace record; the compact console
                // event below is the only addition to the persisted trace.
                replayCursor++
                nodeResult = NodeExecutionResult(
                    outputText = replayRecord.outputText,
                    conditionResult = replayRecord.conditionResult,
                    routingKey = replayRecord.routingKey,
                    tokenCount = replayRecord.tokenCount,
                    resolvedToolName = replayRecord.resolvedToolName,
                )
                executorInput = replayRecord.inputText
                nodeDurationMs = replayRecord.durationMs
                pushConsole(
                    ConsoleEventType.NodeExecution,
                    "↻ ${currentNode.type.name} replayed from checkpoint",
                )
            } else {
                pushConsole(ConsoleEventType.NodeExecution, "▶ ${currentNode.type.name}")

                // Give UI time to render the stage before CPU-heavy inference starts
                kotlinx.coroutines.delay(PipelineExecutionDefaults.LITE_RT_PREWARM_DELAY_MS)

                val executor = nodeExecutorFactory.getExecutor(currentNode.type)
                Timber.tag(
                    "PipelineDebug",
                ).d(
                    "[NODE_IN] type=${currentNode.type.name} id=${currentNode.id} " +
                        "input=${currentInputText.take(PipelineExecutionDefaults.NODE_IO_LOG_CHAR_LIMIT)}",
                )

                // Render `$VARIABLE` placeholders in the node's system prompt before the LLM
                // sees it. We only touch nodes whose system prompt is actually fed into an LLM
                // engine — the others (TOOL, IF_CONDITION, INPUT, QUEUE_PROCESSOR) either ignore
                // `systemPrompt` or use it for non-LLM logic where placeholders are not expected.
                val nodeForExecution = renderNodeSystemPrompt(currentNode)

                // Compose the executor input by selecting only the context blocks the node
                // opted into via its [NodeContextConfig]. Control-flow nodes (INPUT,
                // IF_CONDITION, QUEUE_PROCESSOR) keep their raw passthrough semantics —
                // wrapping them would corrupt routing/queue state. OUTPUT in echo mode
                // (no systemPrompt) is also passed through so it forwards the upstream
                // result verbatim instead of leaking context headers to the user.
                executorInput = if (shouldComposeContext(currentNode)) {
                    // Embed + search only when this node actually renders the
                    // memory block; otherwise pass an empty list so retrieval is
                    // never triggered on its behalf.
                    val memoryEntries = if (currentNode.contextConfig.longTermMemory) {
                        resolveMemoriesOnce()
                    } else {
                        emptyList()
                    }
                    val executionContext = PipelineExecutionContext(
                        originalUserMessage = userPrompt,
                        chatHistory = chatRepository.getMessagesForSession(sessionId).first(),
                        previousNodeOutput = currentInputText,
                        toolResults = toolInvocationResults.toList(),
                        memoryEntries = memoryEntries,
                    )
                    // No fallback to currentInputText: an empty result is the
                    // intended outcome of a sparse config (e.g. only toolResults=true
                    // before any tool has run). Step 3/6 forbids the all-flags-false
                    // case at the validation layer, so we will not silently leak
                    // previous-node output back into a node that opted out.
                    nodeContextBuilder.build(currentNode.contextConfig, executionContext)
                } else {
                    currentInputText
                }

                val nodeStartMs = System.currentTimeMillis()
                var runParked = false
                try {
                    executor.execute(
                        nodeForExecution,
                        executorInput,
                        sessionId,
                        userPrompt,
                        runId,
                        ExecutionScope(depth = depth, stepBudget = budget, pipelineVisitIndex = pipelineVisitIndex),
                    )
                        .collect { output ->
                            when (output) {
                                is NodeOutput.State -> {
                                    if (output.state is AgentOrchestratorState.SuspendedInBackground) {
                                        // Bypass persistSuspensionTransition: its
                                        // wasSuspended branch would flip the record
                                        // back to RUNNING, but a parked run must
                                        // keep its WAITING_* status.
                                        runParked = true
                                    } else if (runId != null) {
                                        runSuspended =
                                            persistSuspensionTransition(runId, output.state, runSuspended)
                                    }
                                    emit(output.state)
                                }
                                is NodeOutput.Result -> nodeResult = output.result
                            }
                        }

                    // A parked run ends the walk without a terminal state: the
                    // executor made the pending request durable and the run
                    // record keeps its WAITING_* status — the user's response
                    // resumes the run from its checkpoint later, possibly in
                    // another process. Flush the buffered trace first so the
                    // checkpoint is complete up to this exact node.
                    if (runParked) {
                        pushConsole(
                            ConsoleEventType.NodeExecution,
                            "⏸ ${currentNode.type.name} parked awaiting user response",
                        )
                        runTraceRepository.flush()
                        return@flow
                    }

                    // The executor flow completing means any HITL suspension of
                    // this node is definitively resolved — flip the record back
                    // to RUNNING here instead of waiting for the next forwarded
                    // state (a clarification node, for instance, emits no state
                    // after its answer arrives, which would otherwise leave the
                    // record stale-WAITING through the next node's model load).
                    if (runId != null && runSuspended) {
                        pipelineRunRepository.updateStatus(runId, PipelineRunStatus.RUNNING)
                        runSuspended = false
                    }

                    Timber.tag(
                        "PipelineDebug",
                    ).d(
                        "[NODE_OUT] type=${currentNode.type.name} id=${currentNode.id} " +
                            "output=${nodeResult?.outputText?.take(PipelineExecutionDefaults.NODE_IO_LOG_CHAR_LIMIT)}",
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Node executors suspend; collapsing a cancelled run into
                    // an `Error` emission would both surface a false error and
                    // keep the flow alive past its collector's cancellation.
                    throw e
                } catch (e: Exception) {
                    Timber.tag(
                        "PipelineDebug",
                    ).e(e, "[NODE_ERR] type=${currentNode.type.name} id=${currentNode.id} error=${e.message}")
                    pushConsole(
                        ConsoleEventType.Error,
                        "${currentNode.type.name}: ${e.message ?: "Unknown error"}",
                    )
                    emit(AgentOrchestratorState.Error(e.message ?: "Unknown error"))
                    return@flow
                }
                nodeDurationMs = System.currentTimeMillis() - nodeStartMs
                metricsRepository.recordNodeExecution(currentNode.type, nodeDurationMs, nodeResult?.tokenCount)
            }
            val nodeTokenCount = nodeResult?.tokenCount

            if (nodeResult?.error != null) {
                Timber.tag(
                    "PipelineDebug",
                ).e("[NODE_ERR] type=${currentNode.type.name} id=${currentNode.id} error=${nodeResult?.error}")
                pushConsole(
                    ConsoleEventType.Error,
                    "${currentNode.type.name}: ${nodeResult?.error}",
                )
                emit(AgentOrchestratorState.Error(nodeResult?.error!!))
                return@flow
            }

            // Skip the "✓" event for OUTPUT — its own emitted Completed state
            // already marks the end of the pipeline, and pushing a ConsoleLog
            // after Completed would shift the terminal state away from the
            // tail of the flow. Replayed nodes already pushed their compact
            // "↻ replayed" event instead.
            if (currentNode.type != NodeType.OUTPUT && replayRecord == null) {
                pushConsole(
                    ConsoleEventType.NodeExecution,
                    "✓ ${currentNode.type.name} in ${nodeDurationMs}ms",
                )
            }

            if (currentNode.type == NodeType.TOOL) {
                val toolOutput = nodeResult?.outputText ?: ""
                // Prefer the executor-resolved tool name so "auto"-configured TOOL
                // nodes attribute the observation to the tool that actually ran,
                // not the literal "auto" placeholder. Fall back to the node's
                // configured toolName, then the node label as a last resort.
                val toolName = nodeResult?.resolvedToolName
                    ?: currentNode.toolName?.takeUnless { it.equals("auto", ignoreCase = true) }
                    ?: currentNode.label
                toolInvocationResults += ToolInvocationResult(toolName = toolName, output = toolOutput)
                pushConsole(ConsoleEventType.ToolCall, toolName)
            }

            if (currentNode.type != NodeType.INPUT && currentNode.type != NodeType.OUTPUT) {
                val outputText = nodeResult?.outputText ?: currentInputText
                traceSteps.add(
                    AgentOrchestratorState.TraceStep(
                        nodeName = currentNode.type.name,
                        outputText = outputText,
                        durationMs = nodeDurationMs,
                        tokenCount = nodeTokenCount,
                        depth = depth,
                    ),
                )
                // Write-through into the persistent run trace: the NodeIo
                // record carries the full input/output pair so the Vars and
                // Traces console tabs can be rebuilt for a finished run, and
                // the checkpoint/resume path can substitute the recorded
                // output for re-execution (the routing verdicts and tool
                // attribution ride along for exactly that replay). A replayed
                // node appends nothing — its record is already in the trace.
                if (runId != null && replayRecord == null) {
                    runTraceRepository.append(
                        RunTraceRecord.NodeIo(
                            runId = runId,
                            sessionId = sessionId,
                            seq = traceSeq++,
                            timestamp = System.currentTimeMillis(),
                            nodeId = currentNode.id,
                            nodeType = currentNode.type.name,
                            inputText = executorInput,
                            outputText = outputText,
                            durationMs = nodeDurationMs,
                            tokenCount = nodeTokenCount,
                            conditionResult = nodeResult?.conditionResult,
                            routingKey = nodeResult?.routingKey,
                            resolvedToolName = nodeResult?.resolvedToolName,
                            depth = depth,
                        ),
                    )
                }
                emit(AgentOrchestratorState.PipelineTrace(traceSteps.toList()))
                // Surface the per-node I/O pair for the Vars tab of the
                // chat-home console pane. INPUT is skipped (its input is
                // the raw user prompt already surfaced as the latest chat
                // row) and OUTPUT is skipped
                // (already terminal; emitting after the `Completed` of
                // OUTPUT would shift the terminal state away from the
                // tail of the flow — same rule applied to the `✓`
                // console event upstream).
                emit(
                    AgentOrchestratorState.NodeIO(
                        nodeId = currentNode.id,
                        nodeType = currentNode.type.name,
                        input = executorInput,
                        output = outputText,
                        depth = depth,
                    ),
                )
            }

            if (currentNode.type == NodeType.OUTPUT) {
                return@flow
            }

            if (currentNode.type == NodeType.QUEUE_PROCESSOR) {
                val list = parseListFromText(nodeResult?.outputText ?: currentInputText)
                activeQueue.clear()
                activeQueue.addAll(list)
                queueResults.clear()
                activeQueueProcessorId = currentNode.id

                val edges = graph.connections.filter { it.sourceNodeId == currentNode.id }
                val itemNodeId = edges.find { it.label.equals("Item", ignoreCase = true) }?.targetNodeId
                    ?: edges.firstOrNull()?.targetNodeId
                val doneNodeId = edges.find { it.label.equals("Done", ignoreCase = true) }?.targetNodeId

                if (activeQueue.isNotEmpty() && itemNodeId != null) {
                    // Compute dynamic total: current steps already done + all queue iterations + tail after queue.
                    val itemNode = graph.nodes.find { it.id == itemNodeId }
                    val doneNode = graph.nodes.find { it.id == doneNodeId }
                    val nodesPerItem = countNodesOnPath(itemNode, graph, stopNodeIds = setOf(currentNode.id))
                    val nodesAfterQueue = countNodesOnPath(doneNode, graph)
                    val totalItems = activeQueue.size // before removeAt — full queue size
                    estimatedTotalSteps = stepCount + totalItems * nodesPerItem + nodesAfterQueue

                    val nextItem = activeQueue.removeAt(0)
                    val contextStr = queueResults.mapIndexed { i, res ->
                        "Result of Subtask ${i + 1}:\n$res"
                    }.joinToString("\n\n")
                    val subtaskInstruction = DefaultPrompts.QueueProcessor.SUBTASK_INSTRUCTION
                    currentInputText = if (contextStr.isNotEmpty()) {
                        "PREVIOUS RESULTS CONTEXT:\n$contextStr\n\n---\n\n$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    } else {
                        "$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    }
                    currentNode = graph.nodes.find { it.id == itemNodeId }
                    continue
                } else {
                    activeQueueProcessorId = null
                    currentNode = graph.nodes.find { it.id == doneNodeId }
                    continue
                }
            }

            // INTENT_ROUTER's outputText is the routing key — a control signal, not a content payload.
            // Preserve currentInputText so downstream nodes receive the original data, not the routing label.
            currentInputText = if (currentNode.type == NodeType.INTENT_ROUTER) {
                currentInputText
            } else {
                nodeResult?.outputText ?: currentInputText
            }

            val nextNodeId = findNextNodeId(currentNode, graph, nodeResult?.conditionResult, nodeResult?.routingKey)
            val nextNode = graph.nodes.find { it.id == nextNodeId }

            // After a branching node resolves its path, compute the estimated total for that branch.
            if (currentNode.type == NodeType.INTENT_ROUTER || currentNode.type == NodeType.IF_CONDITION) {
                estimatedTotalSteps = stepCount + countNodesOnPath(nextNode, graph)
            }

            if (activeQueueProcessorId != null && (nextNode == null || nextNode.type == NodeType.QUEUE_PROCESSOR)) {
                queueResults.add(currentInputText)

                val edges = graph.connections.filter { it.sourceNodeId == activeQueueProcessorId }
                val itemNodeId = edges.find { it.label.equals("Item", ignoreCase = true) }?.targetNodeId
                    ?: edges.firstOrNull()?.targetNodeId
                val doneNodeId = edges.find { it.label.equals("Done", ignoreCase = true) }?.targetNodeId

                if (activeQueue.isNotEmpty() && itemNodeId != null) {
                    val nextItem = activeQueue.removeAt(0)
                    val contextStr = queueResults.mapIndexed { i, res ->
                        "Result of Subtask ${i + 1}:\n$res"
                    }.joinToString("\n\n")
                    val subtaskInstruction = DefaultPrompts.QueueProcessor.SUBTASK_INSTRUCTION
                    if (contextStr.isNotEmpty()) {
                        currentInputText =
                            "PREVIOUS RESULTS CONTEXT:\n$contextStr\n\n---\n\n$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    } else {
                        currentInputText = "$subtaskInstruction\n\nCURRENT SUBTASK TO EXECUTE:\n$nextItem"
                    }
                    currentNode = graph.nodes.find { it.id == itemNodeId }
                    continue
                } else {
                    currentInputText =
                        "Queue execution completed.\nResults:\n" +
                        queueResults.mapIndexed { i, res -> "${i + 1}. $res" }.joinToString("\n")
                    activeQueueProcessorId = null
                    currentNode = graph.nodes.find { it.id == doneNodeId }
                    continue
                }
            }

            currentNode = nextNode
        }

        if (budget.remaining <= 0 && currentNode != null) {
            // Shared run-tree budget exhausted. When this run is a sub-pipeline
            // the Error becomes the parent PIPELINE node's error and terminates
            // the whole stack — the message names the tree-wide ceiling so the
            // failure is unambiguous regardless of which depth ran out.
            pushConsole(ConsoleEventType.Error, "Pipeline exceeded max steps ($maxSteps)")
            emit(
                AgentOrchestratorState.Error(
                    "Pipeline execution exceeded the maximum of $maxSteps steps shared across the pipeline tree.",
                ),
            )
        } else {
            // Loop exited because currentNode became null before reaching OUTPUT
            pushConsole(ConsoleEventType.Error, "Pipeline terminated without OUTPUT")
            emit(
                AgentOrchestratorState.Error(
                    "Pipeline execution terminated unexpectedly without reaching OUTPUT node.",
                ),
            )
        }
    }.onCompletion {
        // Terminal flush: completion, failure and cancellation all land here,
        // so the persisted trace is complete the moment the run ends — even
        // when the process is about to die right after. The cancellation path
        // arrives with the coroutine already cancelled, hence NonCancellable;
        // the flush itself is best-effort and never throws storage failures.
        if (runId != null) {
            withContext(NonCancellable) {
                runTraceRepository.flush()
            }
        }
    }

    /**
     * Counts the number of nodes reachable from [startNode] by following the first outgoing edge
     * of each node, including [startNode] itself. Stops at [NodeType.OUTPUT] (inclusive),
     * dead ends, already-visited nodes, or any node whose ID is in [stopNodeIds].
     *
     * Used to estimate the remaining steps on the active branch after a routing decision
     * or to measure the item-subgraph depth inside a [NodeType.QUEUE_PROCESSOR].
     *
     * @param startNode The node to start counting from, or null (returns 0).
     * @param graph The pipeline graph to traverse.
     * @param stopNodeIds IDs of nodes that act as exclusive stop boundaries (not counted).
     * @return The number of nodes on the path.
     */
    private fun countNodesOnPath(
        startNode: NodeModel?,
        graph: PipelineGraph,
        stopNodeIds: Set<String> = emptySet(),
    ): Int {
        var count = 0
        var node = startNode
        val visited = mutableSetOf<String>()
        while (node != null && node.id !in visited && node.id !in stopNodeIds) {
            visited.add(node.id)
            count++
            if (node.type == NodeType.OUTPUT) break
            val nextId = graph.connections.firstOrNull { it.sourceNodeId == node.id }?.targetNodeId
            node = graph.nodes.find { it.id == nextId }
        }
        return count
    }

    private fun findNextNodeId(
        currentNode: NodeModel,
        graph: PipelineGraph,
        conditionResult: Boolean?,
        routingKey: String? = null,
    ): String? {
        val edges = graph.connections.filter { it.sourceNodeId == currentNode.id }
        if (edges.isEmpty()) {
            Timber.tag("PipelineDebug").d("[ROUTE] from=${currentNode.id} label=null -> to=null")
            return null
        }

        val targetNodeId = if (currentNode.type == NodeType.IF_CONDITION) {
            val expectedLabel = if (conditionResult == true) "True" else "False"
            edges.find { it.label.equals(expectedLabel, ignoreCase = true) }?.targetNodeId
                ?: edges.firstOrNull()?.targetNodeId
        } else if (currentNode.type == NodeType.INTENT_ROUTER && routingKey != null) {
            val matchedEdge = edges.find { it.label?.equals(routingKey, ignoreCase = true) == true }
                ?: edges.find { !it.label.isNullOrBlank() && routingKey.contains(it.label, ignoreCase = true) }
            matchedEdge?.targetNodeId ?: edges.firstOrNull()?.targetNodeId
        } else if (currentNode.type == NodeType.EVALUATION && routingKey != null) {
            // EVALUATION emits a Pass / Retry / Fail verdict as the routing key;
            // route to the edge whose label matches the verdict, falling back to
            // the first outgoing edge when the verdict has no dedicated port.
            edges.find { it.label?.equals(routingKey, ignoreCase = true) == true }?.targetNodeId
                ?: edges.firstOrNull()?.targetNodeId
        } else {
            edges.firstOrNull()?.targetNodeId
        }

        val edgeLabel = edges.find { it.targetNodeId == targetNodeId }?.label ?: "null"
        Timber.tag("PipelineDebug").d("[ROUTE] from=${currentNode.id} label=$edgeLabel -> to=$targetNodeId")
        return targetNodeId
    }

    /**
     * Mirrors a human-in-the-loop suspension (and its resolution) into the
     * persistent run record. [AgentOrchestratorState.WaitingForApproval] and
     * [AgentOrchestratorState.AwaitingClarification] move the record to the
     * matching WAITING_* status; the first state forwarded *after* a
     * suspension flips it back to [PipelineRunStatus.RUNNING] (the node-end
     * flip in the main loop covers executors that emit no state after
     * resolution). All other states leave the record untouched — the RUNNING
     * transition itself and every terminal status are owned by the task
     * queue. The repository is best-effort by contract, so no guard is
     * needed here.
     *
     * @param runId Id of the persistent run record.
     * @param state The orchestrator state about to be forwarded downstream.
     * @param wasSuspended Whether the record currently sits in a WAITING_* status.
     * @return The new suspension flag to carry into the next forwarded state.
     */
    private suspend fun persistSuspensionTransition(
        runId: String,
        state: AgentOrchestratorState,
        wasSuspended: Boolean,
    ): Boolean = when {
        state is AgentOrchestratorState.WaitingForApproval -> {
            pipelineRunRepository.updateStatus(runId, PipelineRunStatus.WAITING_APPROVAL)
            // Suspension flush: the run may now wait indefinitely (and the
            // process may die waiting), so the trace must be durable up to
            // this exact point.
            runTraceRepository.flush()
            true
        }
        state is AgentOrchestratorState.AwaitingClarification -> {
            pipelineRunRepository.updateStatus(runId, PipelineRunStatus.WAITING_CLARIFICATION)
            runTraceRepository.flush()
            true
        }
        wasSuspended -> {
            pipelineRunRepository.updateStatus(runId, PipelineRunStatus.RUNNING)
            false
        }
        else -> false
    }

    /**
     * Decides whether [node]'s input string should be assembled by
     * [NodeContextBuilder] (true) or passed through as the raw
     * `currentInputText` (false). Delegates to [NodeModel.usesContextConfig],
     * the single source of truth shared with `PipelineGraph.validate()` so
     * the validator only flags empty configs on nodes that actually consume
     * them.
     */
    private fun shouldComposeContext(node: NodeModel): Boolean = node.usesContextConfig()

    /**
     * Returns a copy of [node] with its `systemPrompt` rendered through
     * [PromptTemplateEngine], substituting all `$VARIABLE` placeholders using the
     * injected [PromptVariableProvider] set. For nodes whose `systemPrompt` is
     * not consumed by an LLM (e.g. [NodeType.TOOL], [NodeType.IF_CONDITION]) the
     * original [node] instance is returned unchanged to avoid wasted work and
     * accidental substitution inside fields that happen to share the syntax.
     */
    private suspend fun renderNodeSystemPrompt(node: NodeModel): NodeModel {
        val rawPrompt = node.systemPrompt
        if (rawPrompt.isNullOrEmpty() || node.type !in LLM_NODE_TYPES) return node
        val rendered = promptTemplateEngine.render(rawPrompt, promptVariableProviders.toList())
        if (rendered === rawPrompt || rendered == rawPrompt) return node
        return node.copy(systemPrompt = rendered)
    }

    private fun parseListFromText(text: String): List<String> {
        try {
            val blockRegex = """```json\s*(\[.*?\])\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val blockMatch = blockRegex.find(text)
            val jsonString = blockMatch?.groups?.get(1)?.value ?: text.trim()

            if (jsonString.startsWith("[")) {
                val jsonArray = org.json.JSONArray(jsonString)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
                if (list.isNotEmpty()) return list
            }
        } catch (e: Exception) {
            // Ignore JSON parse errors and fallback
            Timber.tag("PipelineDebug").e(e, "Error parsing JSON list")
        }

        val lines = text.lines().map { it.trim() }.filter { it.matches(Regex("""^(\d+\.|-|\*)\s+.*""")) }
        if (lines.isNotEmpty()) {
            return lines.map { it.replaceFirst(Regex("""^(\d+\.|-|\*)\s+"""), "") }
        }

        return listOf(text)
    }

    private companion object {
        /**
         * Node types whose `systemPrompt` is forwarded to an LLM engine and
         * therefore needs `$VARIABLE` placeholders resolved before execution.
         * Includes [NodeType.LITE_RT], [NodeType.CLOUD], [NodeType.OUTPUT] from
         * the explicit task spec plus the other LLM-driven node types in this
         * codebase (`SUMMARY`, `INTENT_ROUTER`, `DECOMPOSITION`, `EVALUATION`).
         */
        private val LLM_NODE_TYPES: Set<NodeType> = setOf(
            NodeType.LITE_RT,
            NodeType.CLOUD,
            NodeType.OUTPUT,
            NodeType.SUMMARY,
            NodeType.INTENT_ROUTER,
            NodeType.DECOMPOSITION,
            NodeType.EVALUATION,
            NodeType.CLARIFICATION,
        )

        /** Crashlytics custom key for the id of the pipeline currently executing. */
        const val CRASH_KEY_PIPELINE_ID: String = "active_pipeline_id"

        /** Crashlytics custom key for the display name of the active local LLM. */
        const val CRASH_KEY_ACTIVE_MODEL: String = "active_model"

        /** Value reported when no local model is currently selected. */
        const val ACTIVE_MODEL_NONE: String = "none"
    }
}
