package app.knotwork.android.integration

import androidx.room.Room
import app.knotwork.android.data.engine.KoogClientFactory
import app.knotwork.android.data.engine.KoogCloudLlmModelResolver
import app.knotwork.android.data.engine.TaskQueueManagerImpl
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.models.ChatSessionEntity
import app.knotwork.android.data.repositories.PendingInteractionRepositoryImpl
import app.knotwork.android.data.repositories.PipelineRunRepositoryImpl
import app.knotwork.android.data.repositories.RunTraceRepositoryImpl
import app.knotwork.android.domain.engine.GraphExecutionEngine
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.engine.NodeContextBuilder
import app.knotwork.android.domain.engine.executors.ClarificationNodeExecutor
import app.knotwork.android.domain.engine.executors.CloudLlmNodeExecutor
import app.knotwork.android.domain.engine.executors.IfConditionNodeExecutor
import app.knotwork.android.domain.engine.executors.InputNodeExecutor
import app.knotwork.android.domain.engine.executors.LiteRtNodeExecutor
import app.knotwork.android.domain.engine.executors.NodeExecutorFactory
import app.knotwork.android.domain.engine.executors.OutputNodeExecutor
import app.knotwork.android.domain.engine.executors.QueueProcessorNodeExecutor
import app.knotwork.android.domain.engine.executors.SummaryNodeExecutor
import app.knotwork.android.domain.engine.executors.SystemNodeExecutor
import app.knotwork.android.domain.engine.executors.ToolNodeExecutor
import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.models.RunTraceRecord
import app.knotwork.android.domain.models.ToolApprovalPolicy
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.prompt.PromptTemplateEngine
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.services.ApprovalNotifier
import app.knotwork.android.domain.services.ClarificationNotifier
import app.knotwork.android.domain.usecases.AgentOrchestratorUseCase
import app.knotwork.android.domain.usecases.LoadModelUseCase
import app.knotwork.android.domain.usecases.ParkedRunResumer
import app.knotwork.android.domain.usecases.PendingSubmissionOutcome
import app.knotwork.android.domain.usecases.ResumeOutcome
import app.knotwork.android.domain.usecases.ResumePipelineRunUseCase
import app.knotwork.android.domain.usecases.RetrieveRelevantMemoryUseCase
import app.knotwork.android.domain.usecases.SubmitApprovalDecisionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * End-to-end JVM integration test of the background-autonomy cycle:
 *
 * 1. a **scheduled** task is enqueued (the same `enqueueScheduled` path
 *    `AgentWorker` drives) and its run starts executing;
 * 2. the process is **killed** while the LLM node is mid-inference — modelled
 *    as a second set of process-scoped components ("process B") over the same
 *    database, while process A's coroutines simply never complete;
 * 3. process B's startup **orphan sweep** detects the ownerless RUNNING
 *    record and settles it INTERRUPTED;
 * 4. the user **resumes** the run; the engine replays the persisted trace
 *    prefix and continues live until the TOOL node, whose SENSITIVE risk
 *    raises an approval gate; with no UI response the live wait times out
 *    and the run **parks** (persistent WAITING_APPROVAL + notification);
 * 5. the user **approves from the notification** (the same
 *    [SubmitApprovalDecisionUseCase] entry point `AgentApprovalReceiver`
 *    dispatches to); the run resumes from its checkpoint, the TOOL node
 *    consumes the parked decision, executes the tool, and the pipeline runs
 *    to completion — the final answer lands in the session and the run
 *    record settles COMPLETED.
 *
 * Every seam here has its own focused suite (worker, queue, engine replay,
 * resumer, receiver); this test chains them over **real** Room-backed
 * repositories and a **real** engine + queue to prove the arc holds
 * end-to-end. Only the device edges are test doubles: LLM inference,
 * tool execution, chat persistence, notifications, and settings.
 *
 * Real `Dispatchers.IO` hops inside the repositories do not run on the
 * virtual-time scheduler, so the test synchronises on **observed database
 * state** via [awaitUntil] instead of bare `advanceUntilIdle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackgroundAutonomyCycleIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var database: AppDatabase

    // Shared device-edge doubles (identical for both "processes").
    private lateinit var chatRepository: ChatRepository
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var toolRepository: ToolRepository

    private val graph = PipelineGraph(
        id = GRAPH_ID,
        name = "Background cycle",
        nodes = listOf(
            NodeModel("input_1", NodeType.INPUT, 0f, 0f),
            NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f, systemPrompt = STEP_MARKER),
            NodeModel("tool_1", NodeType.TOOL, 0f, 0f, toolName = TOOL_NAME),
            NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = FINAL_MARKER),
        ),
        connections = listOf(
            ConnectionModel("c1", "input_1", "llm_1"),
            ConnectionModel("c2", "llm_1", "tool_1"),
            ConnectionModel("c3", "tool_1", "output_1"),
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // The persisted trace carries a foreign key into `chat_sessions`;
        // chat persistence itself is mocked, so seed the owning session row
        // the way the real chat repository would have.
        runBlocking {
            database.chatDao().insertSession(ChatSessionEntity(id = SESSION_ID, name = "cycle", updatedAt = 0L))
        }

        chatRepository = mockk(relaxed = true)
        every { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())

        pipelineRepository = mockk()
        every { pipelineRepository.getAllPipelines() } returns flowOf(listOf(graph))
        coEvery { pipelineRepository.getPipelineById(GRAPH_ID) } returns graph

        settingsRepository = mockk()
        every { settingsRepository.verboseMemoryLoggingEnabled } returns flowOf(false)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        every { settingsRepository.toolUsageInstruction } returns flowOf("")
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(LIVE_WAIT_TIMEOUT_MS)
        every { settingsRepository.resumeMaxAgeHours } returns flowOf(48)
        every { settingsRepository.backgroundApprovalWindowHours } returns flowOf(24)
        every { settingsRepository.defaultPipelineId } returns flowOf(GRAPH_ID)

        toolRepository = mockk(relaxed = true)
        coEvery { toolRepository.getAvailableTools() } returns
            listOf(AgentTool(TOOL_NAME, "Searches the web", "{}"))
        coEvery { toolRepository.getRisk(TOOL_NAME) } returns ToolRisk.SENSITIVE
        coEvery { toolRepository.executeTool(TOOL_NAME, any(), any()) } returns TOOL_RESULT
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `scheduled run survives process death, resumes, parks on HITL, and completes after approval`() =
        testScope.runTest {
            // ── Phase 1: schedule — the run starts and hangs on the LLM node ──
            val processA = buildProcess(
                llmEngine = mockk<LlmInferenceEngine> {
                    // The local model never returns: the process will "die"
                    // mid-inference on the LITE_RT node.
                    every { generateResponseStream(any()) } returns flow { awaitCancellation() }
                },
            )
            val runId = AgentOrchestratorUseCase(processA.taskQueueManager)
                .enqueueScheduled(SESSION_ID, USER_PROMPT)

            awaitUntil("run is RUNNING") {
                processA.runRepository.getRun(runId)?.status == PipelineRunStatus.RUNNING
            }
            // The buffered recorder's flush timer (virtual time) makes the
            // pre-kill trace durable; the run dies exactly where the TODO
            // scenario demands — mid-inference on the LLM node.
            awaitUntil(
                "run is mid-inference on the LITE_RT node",
                dump = {
                    "run=${processA.runRepository.getRun(runId)}\n" +
                        "trace=${processA.traceRepository.getTraceForRun(runId)}"
                },
            ) {
                processA.runRepository.getRun(runId)?.currentNodeId == "llm_1" &&
                    processA.traceRepository.getTraceForRun(runId).isNotEmpty()
            }

            // ── Phase 2: process death + restart — orphan sweep marks INTERRUPTED ──
            val processB = buildProcess(llmEngine = scriptedLlmEngine())
            // Mirror of InitializeAppUseCase.sweepOrphanedRuns(): non-terminal
            // runs not owned by the (new) process, minus parked runs, settle
            // INTERRUPTED. Process B's repository has a fresh ownership
            // registry, exactly like a process that just cold-started.
            val parked = processB.pendingRepository.getAllRunIds()
            processB.runRepository.getOrphanedRuns()
                .filterNot { it.id in parked }
                .forEach { processB.runRepository.finishRun(it.id, PipelineRunStatus.INTERRUPTED, "killed") }

            assertEquals(PipelineRunStatus.INTERRUPTED, processB.runRepository.getRun(runId)?.status)
            // Only now tear process A down — a real dead process runs no
            // `finally` blocks, and the guarded terminal write keeps its
            // CANCELLED stamp from touching the already-INTERRUPTED record.
            processA.taskQueueManager.scope.cancel()

            // ── Phase 3: resume — replay to the TOOL node, park on approval ──
            assertEquals(ResumeOutcome.Resumed, processB.resumeRun(runId))
            awaitUntil("run parked WAITING_APPROVAL") {
                processB.runRepository.getRun(runId)?.status == PipelineRunStatus.WAITING_APPROVAL &&
                    processB.pendingRepository.getForRun(runId) != null
            }
            verify(atLeast = 1) {
                processB.approvalNotifier.sendApprovalRequest(any(), any(), any(), any())
            }
            // The resumed engine executed the LLM step live (the interrupted
            // run never completed it) and its record is now part of the
            // checkpoint the post-approval resume will replay.
            assertEquals(
                1,
                processB.traceRepository.getTraceForRun(runId)
                    .filterIsInstance<RunTraceRecord.NodeIo>()
                    .count { it.nodeId == "llm_1" },
            )

            // ── Phase 4: approve from the notification — run completes ──
            val outcome = SubmitApprovalDecisionUseCase(
                processB.taskQueueManager,
                processB.pendingRepository,
                processB.parkedRunResumer,
            )(SESSION_ID, isApproved = true, runId = runId)
            assertEquals(PendingSubmissionOutcome.Resumed, outcome)

            awaitUntil("run COMPLETED") {
                processB.runRepository.getRun(runId)?.status == PipelineRunStatus.COMPLETED
            }
            // The post-approval resume replayed the completed LLM step from
            // the checkpoint instead of running inference again: the run
            // crossed that node twice, yet exactly one record exists.
            assertEquals(
                1,
                processB.traceRepository.getTraceForRun(runId)
                    .filterIsInstance<RunTraceRecord.NodeIo>()
                    .count { it.nodeId == "llm_1" },
            )
            // The pending record was consumed and the notification withdrawn.
            assertNull(processB.pendingRepository.getForRun(runId))
            // The staged tool call really executed after the approval.
            coVerify(exactly = 1) { toolRepository.executeTool(TOOL_NAME, any(), any()) }
            // The final answer landed in the session as a regular assistant
            // message — the same landing path an interactive run uses.
            val saved = mutableListOf<ChatMessage>()
            coVerify(atLeast = 1) { chatRepository.saveMessage(capture(saved)) }
            assertTrue(
                "Expected a final assistant message carrying the OUTPUT answer",
                saved.any { it.role == Role.AGENT && it.isFinal && it.content.contains(FINAL_ANSWER) },
            )

            processB.taskQueueManager.scope.cancel()
        }

    /**
     * One "process" worth of singletons: fresh Room-backed repositories
     * (fresh run-ownership registry), a real engine wired exactly like
     * production DI, and a real task queue on the shared test dispatcher.
     */
    private fun buildProcess(llmEngine: LlmInferenceEngine): ProcessHarness {
        val runRepository = PipelineRunRepositoryImpl(database.pipelineRunDao())
        val traceRepository = RunTraceRepositoryImpl(database.traceStepDao())
            .apply { dispatcher = testDispatcher }
        val pendingRepository = PendingInteractionRepositoryImpl(database.pendingInteractionDao())
        val approvalNotifier = mockk<ApprovalNotifier>(relaxed = true)
        val clarificationNotifier = mockk<ClarificationNotifier>(relaxed = true)
        val loadModelUseCase = mockk<LoadModelUseCase>()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        val retrieveRelevantMemoryUseCase = mockk<RetrieveRelevantMemoryUseCase>()
        coEvery { retrieveRelevantMemoryUseCase(any()) } returns emptyList()
        coEvery { retrieveRelevantMemoryUseCase.retrieveScored(any()) } returns emptyList()

        val toolNodeExecutor = ToolNodeExecutor(
            llmEngine,
            loadModelUseCase,
            toolRepository,
            settingsRepository,
            approvalNotifier,
            chatRepository,
            pendingRepository,
        )
        val nodeExecutorFactory = NodeExecutorFactory(
            InputNodeExecutor(),
            OutputNodeExecutor(llmEngine, loadModelUseCase, chatRepository),
            IfConditionNodeExecutor(mockk()),
            toolNodeExecutor,
            LiteRtNodeExecutor(
                llmEngine,
                toolRepository,
                chatRepository,
                settingsRepository,
                mockk(relaxed = true),
                loadModelUseCase,
            ),
            CloudLlmNodeExecutor(
                toolRepository,
                chatRepository,
                settingsRepository,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<KoogClientFactory>(),
                mockk<KoogCloudLlmModelResolver>(),
                mockk(relaxed = true),
            ),
            SystemNodeExecutor(llmEngine, loadModelUseCase, chatRepository),
            QueueProcessorNodeExecutor(),
            SummaryNodeExecutor(llmEngine, loadModelUseCase),
            ClarificationNodeExecutor(
                llmEngine,
                loadModelUseCase,
                mockk(),
                pendingRepository,
                clarificationNotifier,
            ),
        )
        val engine = GraphExecutionEngine(
            nodeExecutorFactory,
            toolNodeExecutor,
            chatRepository,
            settingsRepository,
            mockk(relaxed = true),
            PromptTemplateEngine(),
            emptySet(),
            NodeContextBuilder(),
            retrieveRelevantMemoryUseCase,
            mockk(relaxed = true),
            mockk(relaxed = true) {
                coEvery { getActiveModel() } returns null
            },
            mockk(relaxed = true),
            runRepository,
            traceRepository,
        )
        val taskQueueManager = TaskQueueManagerImpl(
            chatRepository = chatRepository,
            pipelineRepository = pipelineRepository,
            settingsRepository = settingsRepository,
            graphExecutionEngine = engine,
            pipelineRunRepository = runRepository,
            runTraceRepository = traceRepository,
        ).apply { dispatcher = testDispatcher }

        val resumeRun = ResumePipelineRunUseCase(
            runRepository,
            pipelineRepository,
            settingsRepository,
            pendingRepository,
            taskQueueManager,
        )
        val parkedRunResumer = ParkedRunResumer(
            pendingRepository,
            runRepository,
            settingsRepository,
            approvalNotifier,
            clarificationNotifier,
            resumeRun,
        )
        return ProcessHarness(
            taskQueueManager = taskQueueManager,
            runRepository = runRepository,
            traceRepository = traceRepository,
            pendingRepository = pendingRepository,
            approvalNotifier = approvalNotifier,
            resumeRun = resumeRun,
            parkedRunResumer = parkedRunResumer,
        )
    }

    /**
     * LLM double for the post-restart process. Dispatches on the node's
     * system-prompt marker embedded in the rendered prompt, so the script
     * stays correct no matter how many times a node re-runs (checkpoint
     * replay vs. live re-execution).
     */
    private fun scriptedLlmEngine(): LlmInferenceEngine = mockk {
        every { generateResponseStream(any()) } answers {
            val prompt = firstArg<String>()
            flowOf(
                when {
                    // Markers first: the LITE_RT prompt may itself embed the
                    // available-tools list (and with it the tool name).
                    prompt.contains(STEP_MARKER) -> STEP_ANSWER
                    prompt.contains(FINAL_MARKER) -> FINAL_ANSWER
                    // Anything else is the TOOL node resolving its call; both
                    // park and post-approval re-resolution must yield the
                    // identical call so the TOCTOU argument guard accepts the
                    // parked decision.
                    else -> TOOL_CALL_JSON
                },
            )
        }
    }

    /**
     * Drives the virtual-time scheduler until [predicate] observes the
     * expected database state, yielding to real worker threads between
     * passes — the repositories hop through real `Dispatchers.IO`, whose
     * completions the test scheduler cannot see.
     */
    private suspend fun TestScope.awaitUntil(
        what: String,
        dump: suspend () -> String = { "" },
        predicate: suspend () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (!predicate()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for: $what\n${dump()}" }
            advanceUntilIdle()
            withContext(Dispatchers.Default) { delay(REAL_POLL_DELAY_MS) }
        }
    }

    /**
     * The per-process singleton set the production DI graph would provide.
     *
     * @property taskQueueManager The process's queue worker.
     * @property runRepository Run store with the process-local ownership registry.
     * @property traceRepository Buffered trace store on the test dispatcher.
     * @property pendingRepository Persistent HITL park store.
     * @property approvalNotifier Captures the park notification of this process.
     * @property resumeRun Checkpoint-resume entry point.
     * @property parkedRunResumer Background-decision submission tail.
     */
    private data class ProcessHarness(
        val taskQueueManager: TaskQueueManagerImpl,
        val runRepository: PipelineRunRepositoryImpl,
        val traceRepository: RunTraceRepositoryImpl,
        val pendingRepository: PendingInteractionRepositoryImpl,
        val approvalNotifier: ApprovalNotifier,
        val resumeRun: ResumePipelineRunUseCase,
        val parkedRunResumer: ParkedRunResumer,
    )

    private companion object {
        const val GRAPH_ID = "cycle-graph"
        const val SESSION_ID = "cycle-session"
        const val USER_PROMPT = "Find tonight's aurora forecast"
        const val TOOL_NAME = "web.search"
        const val TOOL_RESULT = "Aurora visible after 23:00"
        const val TOOL_CALL_JSON = """{"tool":"web.search","arguments":"q=aurora forecast"}"""
        const val STEP_MARKER = "STEP-MARKER"
        const val STEP_ANSWER = "Need the live forecast — calling the search tool."
        const val FINAL_MARKER = "FINAL-MARKER"
        const val FINAL_ANSWER = "Aurora is expected to be visible after 23:00 tonight."
        const val LIVE_WAIT_TIMEOUT_MS = 1_000L
        const val AWAIT_TIMEOUT_MS = 30_000L
        const val REAL_POLL_DELAY_MS = 5L
    }
}
