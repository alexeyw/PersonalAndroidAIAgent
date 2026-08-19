package app.knotwork.android.domain.engine

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.constants.PipelineExecutionDefaults
import app.knotwork.android.domain.engine.executors.NodeExecutorFactory
import app.knotwork.android.domain.engine.executors.ToolNodeExecutor
import app.knotwork.android.domain.engine.structured.JsonPayloadExtractor
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ChatHistorySummary
import app.knotwork.android.domain.models.ConsoleEvent
import app.knotwork.android.domain.models.ConsoleEventType
import app.knotwork.android.domain.models.EngineImageInput
import app.knotwork.android.domain.models.ExecutionScope
import app.knotwork.android.domain.models.MemoryChunk
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.ResumeContext
import app.knotwork.android.domain.models.RunBudgetLedger
import app.knotwork.android.domain.models.RunGeneratingModel
import app.knotwork.android.domain.models.RunImageDelivery
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.models.RunSpend
import app.knotwork.android.domain.models.RunTerminationReason
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
import app.knotwork.android.domain.usecases.ResolveRunCeilingsUseCase
import app.knotwork.android.domain.usecases.RetrieveRelevantMemoryUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine responsible for executing a given [PipelineGraph].
 * It traverses nodes starting from [NodeType.INPUT], evaluates conditions,
 * executes LLM inference, triggers tools, and reaches [NodeType.OUTPUT].
 */
// LargeClass is suppressed deliberately: this is the central pipeline
// orchestrator, and the run-walk logic (node traversal, lazy memory and
// chat-history resolution, HITL suspension, checkpoint/resume, sub-pipeline
// fan-out) is most readable as one cohesive state machine. Decomposing it into
// collaborators is tracked as future work rather than forced here.
@Suppress("LargeClass")
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
    private val chatHistoryWindowPlanner: ChatHistoryWindowPlanner,
    private val retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase,
    private val crashReportingRepository: CrashReportingRepository,
    private val localModelRepository: LocalModelRepository,
    private val memoryRepository: MemoryRepository,
    private val pipelineRunRepository: PipelineRunRepository,
    private val runTraceRepository: RunTraceRepository,
    private val resolveRunCeilingsUseCase: ResolveRunCeilingsUseCase,
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
     * @param budget The spend ledger shared across the whole run tree. `null`
     *   (the default, used by top-level callers) makes the engine build one:
     *   ceilings resolved from [origin], counters seeded from whatever the run
     *   record already holds — which is zero for a fresh run and the previous
     *   attempt's spend for a resumed one, so the ceiling binds across a park
     *   and resume instead of restarting. A sub-pipeline invocation passes the
     *   parent's ledger so the nested run charges the same ceiling instead of
     *   getting a private allowance. A breach at any depth fails the run with a
     *   typed [RunTerminationReason], which propagates up the stack as the
     *   parent PIPELINE node's error.
     * @param imageInput The **top-level** run's single image attachment, already
     *   resolved to an absolute path + dimensions + byte size, or `null` for a
     *   text-only run (and always `null` for a sub-pipeline invocation, which
     *   instead receives [imageDelivery]). The engine emits an `Image input: W×H,
     *   N KB` console line at run start and seeds the tree-shared delivery state
     *   from it. `null` on resume — a replayed run never re-delivers.
     * @param imageDelivery The run tree's shared single-image delivery state,
     *   passed by a `PIPELINE` node into its sub-pipeline invocation so a vision
     *   sink nested inside the sub-pipeline can consume the image. `null` for a
     *   top-level run (which seeds it from [imageInput]) and for a text-only run.
     *   The image reaches the **first** `LITE_RT` node whose context includes the
     *   original task in execution order *anywhere in the tree* (via
     *   [ExecutionScope.imagePath]) and exactly that node; every other node — and
     *   every `CLOUD` node — sees only text, realising the "attachment belongs to
     *   `userPrompt`, the graph carries text" contract. The send-time pre-flight
     *   verifies such a sink is *reachable* (recursing into sub-pipelines) before
     *   enqueuing; branch-dependent routing can still skip it, in which case the
     *   top-level run emits an "Image not used" console note.
     * @param runHadImage Presence-only signal for a resumed run: `true` when the
     *   interrupted run's originating message carried an image (from the persisted
     *   `PipelineRun.hadImage`). A fresh run leaves this `false` and derives presence
     *   from [imageDelivery] instead. Threaded into [ExecutionScope.imagePresent] so a
     *   live-executed IF/router node past the resume point can still branch on "the user
     *   sent a picture" even though the image itself is never re-delivered on resume.
     * @param origin What started this run. Interactive origins ([RunOrigin.CHAT],
     *   [RunOrigin.SHARE]) key long-term-memory retrieval off [userPrompt] as before;
     *   background ones (trigger / scheduler / tile) prefer the pipeline's declared
     *   `memoryRetrievalQuery`, then the first memory-aware node's input — see
     *   [MemoryRetrievalQueryResolver] and `DESCRIPTION.md` §6.10.1. Defaults to
     *   [RunOrigin.CHAT] (the interactive, unchanged behaviour) so editor test runs and
     *   any caller that does not care keep the old semantics. A sub-pipeline invocation
     *   receives the parent's origin via [ExecutionScope.runOrigin].
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
        budget: RunBudgetLedger? = null,
        imageInput: EngineImageInput? = null,
        imageDelivery: RunImageDelivery? = null,
        runHadImage: Boolean = false,
        generatingModel: RunGeneratingModel? = null,
        origin: RunOrigin = RunOrigin.CHAT,
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

        // The run tree's shared single-image delivery state. A top-level run seeds
        // it from [imageInput]; a sub-pipeline run reuses the parent's instance
        // (threaded in via [imageDelivery]) so a vision sink nested inside a
        // sub-pipeline can consume the image, tracked once across the whole tree.
        val delivery = imageDelivery ?: imageInput?.let { RunImageDelivery(it) }

        // Presence-only signal threaded to every node: true when this run carried an
        // image. A fresh run knows from [delivery]; a resumed run (delivery == null,
        // never re-delivers) carries it forward via [runHadImage] from the persisted
        // PipelineRun. Lets an IF/router node branch on image presence on either path.
        val imagePresent = delivery != null || runHadImage

        // The run tree's shared "which model produced the answer" holder. A
        // top-level run seeds a fresh one; a sub-pipeline reuses the parent's
        // instance (threaded in via [generatingModel]) so an answer produced
        // inside a sub-pipeline still attributes at the root OUTPUT.
        val genModel = generatingModel ?: RunGeneratingModel()

        // Honesty note for a run that carried an image but routed down a path with
        // no vision sink: the send-time pre-flight guarantees such a node *exists*,
        // but branch-dependent routing can still skip it. Emitted only at the
        // top-level run (which owns the "Image input" announcement) and only when
        // the tree-wide delivery was never consumed at any depth.
        suspend fun noteUndeliveredImage() {
            if (imageInput != null && delivery?.consumed == false) {
                pushConsole(
                    ConsoleEventType.SystemMessage,
                    "Image not used: this run took a path with no on-device step that reads images.",
                )
            }
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

        // Announce the image attachment once, at the top-level run start (a
        // sub-run has [imageDelivery] but no [imageInput]), so the console shows
        // the multimodal input before any node executes. The image itself is
        // delivered to a single LITE_RT node below; this line is informational.
        if (imageInput != null) {
            // Round to the nearest KB with a 1 KB floor: a valid sub-1 KB image
            // must never read "0 KB" (which looks like a broken attachment).
            val sizeKb = maxOf(1L, (imageInput.sizeBytes + BYTES_PER_KB / 2) / BYTES_PER_KB)
            pushConsole(
                ConsoleEventType.SystemMessage,
                "Image input: ${imageInput.width}×${imageInput.height}, $sizeKb KB",
            )
        }

        // Spend ledger shared across the whole run tree. A sub-pipeline run
        // reuses the parent's instance (threaded in via [budget]) so nested
        // execution cannot side-step the parent's ceilings; a top-level run
        // builds one, resolving the ceilings from its own origin and seeding
        // the counters from what the run record already holds.
        //
        // The seed is what makes the ceiling bind across a resume. Every
        // answered background approval comes back through the resume path, and
        // a run may park an unbounded number of times — a ledger that started
        // at zero on each attempt would hand a nightly loop a fresh ceiling
        // after every answer, which is the one scenario these ceilings exist
        // for.
        val runBudget = budget ?: run {
            val ceilings = resolveRunCeilingsUseCase(origin)
            val spent = runId?.let { pipelineRunRepository.getSpend(it) } ?: RunSpend()
            RunBudgetLedger(
                ceilings = ceilings,
                rootRunId = runId,
                stepsAlreadySpent = spent.steps,
                tokensAlreadySpent = spent.tokens,
            )
        }
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

        // Long-term memory is retrieved lazily and at most once per run. Only
        // the first *executed* node that actually opts into the
        // `--- Long-Term Memory ---` block (`contextConfig.longTermMemory`)
        // triggers the query embedding — and that same node decides the
        // retrieval key (see [MemoryRetrievalQueryResolver]): an interactive run
        // keys off the immutable userPrompt as it always has, a background run
        // prefers the pipeline's declared query, then the node's own input,
        // because a trigger's prompt is authored once and describes no
        // particular firing. A graph where no executed node requests memory
        // never embeds anything at all — sparing avoidable embedding-provider
        // latency/cost and not shipping the prompt to a cloud embedding backend
        // the user did not ask memory for. A resumed run is seeded from the
        // interrupted run's persisted snapshot, so it neither re-runs retrieval
        // (the context must be identical to the interrupted one) nor re-counts
        // usage.
        var memoizedMemories: List<MemoryChunk>? = resume?.memorySnapshot
        suspend fun resolveMemoriesOnce(nodeInput: String): List<MemoryChunk> {
            memoizedMemories?.let { return it }
            // The declared query is a prompt template like any other, so `$DATE`
            // and friends resolve per run instead of being frozen at authoring
            // time. Rendering happens only when a declared query exists and only
            // on the one node that triggers retrieval.
            val declaredQuery = graph.memoryRetrievalQuery
                ?.takeIf { it.isNotBlank() }
                ?.let { promptTemplateEngine.render(it, promptVariableProviders.toList()) }
            val query = MemoryRetrievalQueryResolver.resolve(
                origin = origin,
                declaredQuery = declaredQuery,
                nodeInput = nodeInput,
                userPrompt = userPrompt,
            )
            val scored: List<Pair<MemoryChunk, Float>> = try {
                retrieveRelevantMemoryUseCase.retrieveScored(query.text)
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
                MemoryAccessLogFormatter.format(
                    query = query.text,
                    source = query.source,
                    hits = scored,
                    verbose = verbose,
                ),
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

        // Chat-history compression splits into a run-stable part and a per-node
        // part:
        //  - The cached summary and the compression settings are resolved at most
        //    once per run. They are safe to memoize because the background
        //    compressor is gated off while a pipeline is active (see
        //    ChatHistoryCompressionCoordinator), so the `chat_history_summaries`
        //    row and the settings cannot change mid-run.
        //  - The live message list is re-read on EVERY call and is NOT memoized.
        //    Message-writing nodes mutate it mid-run — in particular a TOOL node's
        //    observation is persisted as an `isFinal = false` SYSTEM chat message
        //    (ToolInvocationGate) — and a later history-enabled node must see it,
        //    so freezing the list here would hide in-run messages until the next
        //    user turn.
        // Not snapshotted for resume — chat history was never resume-stable.
        var chatCompressionResolved = false
        var chatCompressionEnabled = false
        var chatHistorySummary: ChatHistorySummary? = null
        var chatHistoryThresholdTokens = 0
        var chatHistoryLiveWindow = 0
        // The console note fires once per run, the first time compression actually
        // changes what a node sees.
        var historyCompressionLogged = false
        suspend fun resolveChatHistoryView(): ChatHistoryView {
            if (!chatCompressionResolved) {
                chatCompressionEnabled = settingsRepository.chatHistoryCompressionEnabled.first()
                chatHistorySummary = if (chatCompressionEnabled) {
                    try {
                        chatRepository.getHistorySummary(sessionId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("PipelineDebug").w(e, "Failed to load chat-history summary; continuing without it")
                        null
                    }
                } else {
                    null
                }
                chatHistoryThresholdTokens = settingsRepository.chatHistoryCompressionThresholdTokens.first()
                chatHistoryLiveWindow = settingsRepository.chatHistoryLiveWindowSize.first()
                chatCompressionResolved = true
            }
            // Re-read fresh: the list grows as message-writing nodes append rows.
            val messages = chatRepository.getMessagesForSession(sessionId).first()
            val view = chatHistoryWindowPlanner.plan(
                messages = messages,
                summary = chatHistorySummary,
                compressionEnabled = chatCompressionEnabled,
                thresholdTokens = chatHistoryThresholdTokens,
                liveWindowSize = chatHistoryLiveWindow,
            )
            // Surface the compression only when it actually changed what the node
            // sees — a within-budget run stays silent — and only once per run.
            if (!historyCompressionLogged && (view.truncatedWithoutSummary || view.earlierSummary != null)) {
                historyCompressionLogged = true
                if (view.truncatedWithoutSummary) {
                    pushConsole(
                        ConsoleEventType.HistoryCompression,
                        "Chat history over budget; summary not ready, kept the last " +
                            "${view.liveWindow.size} messages",
                    )
                } else {
                    val gap = if (view.droppedUncoveredCount > 0) {
                        " (${view.droppedUncoveredCount} recent messages not yet summarized)"
                    } else {
                        ""
                    }
                    pushConsole(
                        ConsoleEventType.HistoryCompression,
                        "Chat history compressed: summarized older turns, kept the last " +
                            "${view.liveWindow.size} messages$gap",
                    )
                }
            }
            return view
        }
        // Tool invocations are accumulated as TOOL nodes complete and surfaced
        // via the `--- Tool Results ---` block on later nodes that opt in.
        val toolInvocationResults = mutableListOf<ToolInvocationResult>()

        // Tracks whether the persistent run record currently sits in a
        // WAITING_* suspension status, so the first state forwarded after
        // the suspension resolves flips the record back to RUNNING.
        var runSuspended = false

        // Set the moment a ceiling refuses to let the walk continue, so the
        // post-loop branch can say which one bound instead of re-deriving it.
        var terminationReason: RunTerminationReason? = null

        // A soft crossing announces itself on the console immediately (below,
        // where it happens) but reaches the model only on the next node — the
        // walk rewrites `currentInputText` after the charge site, so a note
        // prepended there would be overwritten before anything read it.
        var pendingSoftCeilingNote = false

        while (currentNode != null) {
            // Ask the ledger before charging, so a node that is refused is never
            // counted: a run stopped at the ceiling has spent exactly the
            // ceiling, not one more than it.
            terminationReason = runBudget.hardBreach()
            if (terminationReason != null) break

            // Deliver a soft warning raised by the previous node into the input
            // this one executes on, so the model can wind the task up rather
            // than discovering the hard stop by walking into it.
            //
            // Only into a node that actually composes a prompt for a model. For
            // every other node `currentInputText` is *data*, not a prompt: an
            // OUTPUT node in its shipped pass-through mode persists its input
            // verbatim as the agent's chat message, so an unguarded injection
            // printed the engine's internal notice to the user as the answer.
            // `QUEUE_PROCESSOR` parses its input as a list and `IF_CONDITION`
            // branches on it — both would be corrupted the same way. The note
            // stays pending until a model-facing node comes along, or is simply
            // never delivered if none does; a hint nobody can act on is worth
            // less than an answer nobody garbled.
            if (pendingSoftCeilingNote && shouldComposeContext(currentNode)) {
                currentInputText = "$SOFT_CEILING_CONTEXT_NOTE\n\n$currentInputText"
                pendingSoftCeilingNote = false
            }

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
                        reason = RunTerminationReason.GraphChanged,
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
                        // `currentInputText` is what this node actually executes
                        // on (the upstream node's output, or the run prompt for a
                        // node right behind INPUT) — the background run's
                        // second-choice retrieval key.
                        resolveMemoriesOnce(currentInputText)
                    } else {
                        emptyList()
                    }
                    // Only nodes that render chat history pay for loading +
                    // planning it; others get the empty view (no DB read). The
                    // view is recomputed per node (fresh message read) so in-run
                    // message writes — e.g. TOOL observations — are visible to
                    // later history-enabled nodes.
                    val chatHistoryView = if (currentNode.contextConfig.chatHistory) {
                        resolveChatHistoryView()
                    } else {
                        ChatHistoryView.EMPTY
                    }
                    val executionContext = PipelineExecutionContext(
                        originalUserMessage = userPrompt,
                        chatHistory = chatHistoryView.liveWindow,
                        previousNodeOutput = currentInputText,
                        toolResults = toolInvocationResults.toList(),
                        memoryEntries = memoryEntries,
                        earlierSummary = chatHistoryView.earlierSummary,
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
                // A routing node validates its key against the labels of its own
                // outgoing edges, which only the graph knows — surface them through
                // the scope so the executor can constrain (and repair towards) a key
                // that actually matches a branch. Empty for every other node type.
                val routingChoices = if (currentNode.type == NodeType.INTENT_ROUTER) {
                    graph.connections
                        .filter { it.sourceNodeId == currentNode.id && !it.label.isNullOrBlank() }
                        .map { it.label!! }
                        .distinct()
                } else {
                    emptyList()
                }
                // Deliver the run's image to the FIRST vision-eligible node only:
                // a LITE_RT node whose context includes the original task (so the
                // image accompanies the user's prompt). Consumption is tracked on
                // the tree-shared [delivery], so once any node at any depth takes
                // the image, every later node — and every CLOUD node — sees text only.
                val imagePathForNode = delivery
                    ?.takeIf {
                        !it.consumed &&
                            currentNode.type == NodeType.LITE_RT &&
                            currentNode.contextConfig.originalTask
                    }
                    ?.image
                    ?.absolutePath
                if (imagePathForNode != null) {
                    delivery?.consumed = true
                }
                // Note an undelivered image *before* the terminal OUTPUT node runs:
                // OUTPUT's executor emits the terminal `Completed`, after which the
                // engine must not push any further console line (it would shift the
                // last orchestrator state away from `Completed`). By the OUTPUT
                // iteration every upstream node — including any vision sink — has
                // already run, so the delivery state is final here.
                if (currentNode.type == NodeType.OUTPUT) {
                    noteUndeliveredImage()
                }
                try {
                    executor.execute(
                        nodeForExecution,
                        executorInput,
                        sessionId,
                        userPrompt,
                        runId,
                        ExecutionScope(
                            depth = depth,
                            budget = runBudget,
                            pipelineVisitIndex = pipelineVisitIndex,
                            routingChoices = routingChoices,
                            imagePath = imagePathForNode,
                            imageDelivery = delivery,
                            imagePresent = imagePresent,
                            generatingModel = genModel,
                            runOrigin = origin,
                        ),
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
                                is NodeOutput.Console -> {
                                    // The node has no console sink of its own; the
                                    // engine owns `seq`/`depth` stamping. A repair
                                    // attempt additionally bumps the per-node repair
                                    // counter so the statistics surface reflects how
                                    // often this node's structured output stumbled.
                                    pushConsole(output.type, output.message)
                                    if (output.type == ConsoleEventType.StructuredOutputRepair) {
                                        metricsRepository.recordStructuredOutputRepair(nodeForExecution.label)
                                    }
                                }
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
                // Charge the tree only for work actually done. A replayed node
                // was charged when it really ran; charging it again would make a
                // run that parks often die earlier than one that never parks —
                // the inverse of what the persisted ledger is for. That is why
                // this sits in the live branch, beside the metrics write and the
                // trace append, which are guarded the same way.
                //
                // Two further exclusions keep the *step* counter stable across
                // attempts, which is the property a persisted counter has to have
                // and the one a per-attempt budget never needed:
                //
                //  - INPUT and OUTPUT are never written to the trace, so they are
                //    never replayed and run their executors again on every
                //    resumed attempt. On a fresh attempt they are ordinary steps
                //    and are charged; on a resume they were already charged the
                //    first time, and charging them again would let a run that
                //    parks fifteen times exhaust a fifteen-step ceiling on
                //    pass-through nodes alone — which is the very scenario the
                //    persisted counter exists to bound.
                //  - A node whose result carries a termination reason is a
                //    `PIPELINE` node whose child already charged this same tree
                //    and stopped it; charging the parent node on top would
                //    persist one more than the ceiling it just reported.
                //
                // Tokens are charged unconditionally: INPUT produces none, and
                // OUTPUT is terminal, so neither can be double-counted later.
                val replayedPassThrough = resume != null &&
                    (currentNode.type == NodeType.INPUT || currentNode.type == NodeType.OUTPUT)
                val chargeableStep = !replayedPassThrough && nodeResult?.terminationReason == null
                if (chargeableStep) {
                    runBudget.chargeStep()
                }
                runBudget.chargeTokens(nodeResult?.tokenCount, approximate = nodeResult?.tokensEstimated != false)
                if (runId != null) {
                    persistSpend(runBudget)
                }
                // Announce a soft crossing where it happens, not at the top of
                // the next iteration: a run whose last node crosses the
                // threshold would otherwise never say so, because there is no
                // next iteration to say it in.
                //
                // OUTPUT is excluded for the reason the `✓` event and the
                // `NodeIO` emission below are: its executor has already emitted
                // `Completed`, and a console line pushed after that would shift
                // the terminal state away from the tail of the flow. Nothing is
                // lost — a warning that the run is approaching its limit has no
                // reader once the answer has been delivered.
                if (currentNode.type != NodeType.OUTPUT) {
                    runBudget.claimSoftBreach()?.let { soft ->
                        pushConsole(
                            ConsoleEventType.RunCeiling,
                            "Approaching the ${soft.axis.name.lowercase()} limit " +
                                "(${soft.spent} of ${soft.hardLimit})",
                        )
                        pendingSoftCeilingNote = true
                    }
                }
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
                // A `PIPELINE` node forwards its sub-pipeline's typed cause here.
                // Re-emitting it is what keeps a ceiling breach one nesting
                // level down from settling the root run as an ordinary failure.
                emit(AgentOrchestratorState.Error(nodeResult?.error!!, reason = nodeResult?.terminationReason))
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
                    // A tool call is the one node whose re-execution is not free:
                    // it acted on the world. The trace is write-buffered (flushed
                    // on size, on a 500 ms timer, or at suspension points), so a
                    // process death inside that window used to lose the record of
                    // a tool that had already run — and the resume then called it
                    // a second time. Measured on the reference device: killed
                    // 112 ms after the tool returned, the resumed run re-invoked
                    // it (second `tools/call` on the wire); killed 1.2 s after, it
                    // replayed as designed. Flushing here closes the window at the
                    // cost of one batch insert per tool call.
                    // A CLOUD node earns the same treatment for a different reason:
                    // re-running it is not free either, but the cost is money and the
                    // provider's rate limit rather than a side effect on the world. The
                    // original TOOL-only rule reasoned that for every other node type a
                    // repeat is "lost time, not a side effect" — that holds for on-device
                    // nodes and not for a billed API call.
                    if (currentNode.type == NodeType.TOOL || currentNode.type == NodeType.CLOUD) {
                        runTraceRepository.flush()
                    }
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

        // Covers loop exits that don't go through an OUTPUT node (a dangling graph
        // whose walk ends with no terminal node); the OUTPUT path notes it inline.
        noteUndeliveredImage()

        val breach = terminationReason
        if (breach != null) {
            // A ceiling refused to let the walk continue. When this run is a
            // sub-pipeline the Error becomes the parent PIPELINE node's error and
            // terminates the whole stack — the message names the tree-wide
            // ceiling so the failure is unambiguous regardless of which depth ran
            // out, and the typed reason travels with it so consumers no longer
            // have to recover the cause from the prose.
            pushConsole(ConsoleEventType.RunCeiling, ceilingConsoleLine(breach))
            emit(AgentOrchestratorState.Error(ceilingErrorMessage(breach), reason = breach))
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
            val oppositeLabel = if (conditionResult == true) "False" else "True"
            val exactTarget = edges.find { it.label.equals(expectedLabel, ignoreCase = true) }?.targetNodeId
            when {
                exactTarget != null -> exactTarget
                // The author wired the opposite branch but left this one
                // unconnected: terminate the branch (-> "terminated without
                // OUTPUT") instead of silently falling through to an arbitrary
                // first edge and running the wrong branch on this verdict.
                edges.any { it.label.equals(oppositeLabel, ignoreCase = true) } -> null
                // No True/False labels at all — a single default edge. Keep the
                // legacy fall-through so an unlabelled pass-through still routes.
                else -> edges.firstOrNull()?.targetNodeId
            }
        } else if (currentNode.type == NodeType.INTENT_ROUTER && routingKey != null) {
            val matchedEdge = edges.find { it.label?.equals(routingKey, ignoreCase = true) == true }
                ?: edges.find { !it.label.isNullOrBlank() && routingKeyContainsLabelAsWord(routingKey, it.label) }
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
     * INTENT_ROUTER fallback match: `true` when [routingKey] contains [label] as
     * a **standalone token** (case-insensitive). Used only after an exact label
     * match fails, to tolerate a model that wraps the chosen label in a sentence
     * ("I choose Cancel") while still rejecting incidental substring hits — an
     * unanchored `contains` would route the key "Cancel" to a port labelled
     * "can".
     *
     * The boundary is expressed as alphanumeric-adjacency lookarounds rather than
     * `\b`: a `\b`-based regex fails to match labels that begin or end with a
     * non-word character (e.g. a port labelled `C#` or `node.js`), because `\b`
     * requires a word↔non-word transition at the label edge. The lookarounds
     * `(?<![A-Za-z0-9])` / `(?![A-Za-z0-9])` instead reject a match only when an
     * alphanumeric character abuts the label, so `C#` matches in "Use C# here"
     * while "can" still does not match inside "Cancel". [label] is regex-escaped
     * so its own characters are literal.
     *
     * @param routingKey The router's chosen routing key (model output).
     * @param label The candidate edge label to test against [routingKey].
     * @return `true` if [label] appears as a standalone token inside [routingKey].
     */
    private fun routingKeyContainsLabelAsWord(routingKey: String, label: String): Boolean = Regex(
        "(?<![A-Za-z0-9])${Regex.escape(label)}(?![A-Za-z0-9])",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(routingKey)

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
        // Console lines and node-I/O snapshots are observations *about* the run,
        // not progress of it: they keep arriving while a HITL gate is waiting
        // (a sub-pipeline forwards its child's console traffic upwards). Letting
        // them fall through to the branch below made the first such line read as
        // "the wait ended" and flip the record back to RUNNING while the gate was
        // still open. For a nested pipeline that left the root RUNNING and the
        // child WAITING_APPROVAL, so `ResumePipelineRunUseCase` — which requires
        // a resumable *root* — rejected every attempt to answer the parked
        // notification (phase-40 finding F7).
        state is AgentOrchestratorState.ConsoleLog || state is AgentOrchestratorState.NodeIO -> wasSuspended
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

    /**
     * Parses a list of items from a node's text output, used to seed a
     * `QUEUE_PROCESSOR` from an upstream `DECOMPOSITION` (or any list-producing
     * node).
     *
     * JSON isolation is delegated to the shared [JsonPayloadExtractor] and the
     * array is deserialized with `kotlinx.serialization`, so this no longer
     * carries its own ```json regex or `org.json` walk — a `DECOMPOSITION` node
     * already validated and re-encoded its list through the structured-output
     * gate, so the common case is a clean array. The Markdown-list fallback (and
     * the single-item fallback) remain for nodes that emit a plain bulleted or
     * numbered list rather than JSON.
     *
     * @param text The upstream node output to parse.
     * @return The parsed items, or a single-element list of [text] when nothing
     *   list-shaped is found.
     */
    private fun parseListFromText(text: String): List<String> {
        val payload = JsonPayloadExtractor.extract(text)
        if (payload.startsWith("[")) {
            try {
                val list = listJson.decodeFromString(ListSerializer(String.serializer()), payload)
                if (list.isNotEmpty()) return list
            } catch (e: IllegalArgumentException) {
                // Not a valid string array — fall through to the Markdown-list parsing.
                // `decodeFromString` is non-suspend, so this cannot mask a CancellationException.
                Timber.tag("PipelineDebug").e(e, "Error parsing JSON list")
            }
        }

        val lines = text.lines().map { it.trim() }.filter { it.matches(Regex("""^(\d+\.|-|\*)\s+.*""")) }
        if (lines.isNotEmpty()) {
            return lines.map { it.replaceFirst(Regex("""^(\d+\.|-|\*)\s+"""), "") }
        }

        return listOf(text)
    }

    /**
     * Writes the tree's accumulated spend onto the root run record.
     *
     * Called once per executed node, from whatever depth is running. Two things
     * make that cadence the right one rather than an extravagance: the walk
     * already writes to `pipeline_runs` on every node entry (`updateCurrentNode`),
     * so this adds no new class of traffic; and the counter has to be exact at
     * every park, because a parked run resumes by reading it back. Nodes are
     * seconds apart — they are LLM calls — so the write is never hot.
     *
     * Best-effort by the repository's contract: losing the write loses accuracy,
     * never the run, and an under-count makes the ceiling bind late rather than
     * early.
     *
     * @param ledger The run tree's spend ledger.
     */
    private suspend fun persistSpend(ledger: RunBudgetLedger) {
        val rootId = ledger.rootRunId ?: return
        pipelineRunRepository.recordSpend(
            rootRunId = rootId,
            stepsSpent = ledger.stepsSpent,
            tokensSpent = ledger.tokensSpent,
        )
    }

    /**
     * The console line announcing a forced termination.
     *
     * Pushed **before** the terminal `Error` emission, like every other console
     * write in this walk: a line appended afterwards would shift the last
     * orchestrator state away from the terminal one the queue reads.
     *
     * @param reason The typed cause.
     * @return The line to append to the run's console.
     */
    private fun ceilingConsoleLine(reason: RunTerminationReason): String = when (reason) {
        is RunTerminationReason.StepCeiling -> "Pipeline exceeded max steps (${reason.limit})"
        is RunTerminationReason.TokenCeiling -> "Pipeline exceeded its token limit (${reason.limit})"
        RunTerminationReason.HitlWindowExpired,
        RunTerminationReason.NoProgress,
        RunTerminationReason.GraphChanged,
        RunTerminationReason.ProcessDied,
        RunTerminationReason.DiscardedByUser,
        RunTerminationReason.NotResumable,
        -> "Pipeline stopped: ${reason.kind.name}"
    }

    /**
     * The human-readable message carried by the terminal `Error` state.
     *
     * Still assembled here, in English, exactly as the step-ceiling message
     * always was. Moving this text into presentation resources is the job of the
     * task that also writes the ceilings settings screen, so that the wording a
     * user reads in the chat, in the settings and in the documentation is
     * decided once instead of three times.
     *
     * @param reason The typed cause.
     * @return The message for the terminal state.
     */
    private fun ceilingErrorMessage(reason: RunTerminationReason): String = when (reason) {
        is RunTerminationReason.StepCeiling ->
            "Pipeline execution exceeded the maximum of ${reason.limit} steps shared across the pipeline tree."
        is RunTerminationReason.TokenCeiling ->
            "Pipeline execution exceeded the maximum of ${reason.limit} tokens shared across the pipeline tree."
        RunTerminationReason.HitlWindowExpired,
        RunTerminationReason.NoProgress,
        RunTerminationReason.GraphChanged,
        RunTerminationReason.ProcessDied,
        RunTerminationReason.DiscardedByUser,
        RunTerminationReason.NotResumable,
        -> "Pipeline execution was stopped (${reason.kind.name})."
    }

    private companion object {
        /**
         * Injected into the run's own context the first time an axis crosses its
         * soft threshold, so the model driving the next node can bring the task
         * to a close on its own terms instead of discovering the hard stop by
         * walking into it.
         *
         * Deliberately says what to do rather than quoting a number: the numbers
         * are in the console line a person reads, and a budget figure inside the
         * prompt invites the model to reason about arithmetic instead of about
         * the task.
         */
        const val SOFT_CEILING_CONTEXT_NOTE: String =
            "SYSTEM NOTICE: this run is close to its resource limit and may be stopped before it " +
                "finishes. Wrap up now: produce the best answer you can from what you already have, " +
                "and do not start new sub-tasks or additional tool calls."

        /** Lenient JSON used to parse a `QUEUE_PROCESSOR` seed list (see [parseListFromText]). */
        val listJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

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

        /** Bytes-per-kilobyte divisor for the `Image input` console line. */
        const val BYTES_PER_KB: Long = 1024L
    }
}
