package app.knotwork.android.presentation.ui.chat.home

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.ClarificationRequest
import app.knotwork.android.domain.models.ConsoleEvent
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.PipelineRun
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.models.RunTraceRecord
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.ClarificationRepository
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.RunTraceRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.MemoryAutoExtractionCoordinator
import app.knotwork.android.domain.usecases.AgentOrchestratorUseCase
import app.knotwork.android.domain.usecases.GetContextWindowUseCase
import app.knotwork.android.domain.usecases.LoadModelUseCase
import app.knotwork.android.domain.usecases.PendingSubmissionOutcome
import app.knotwork.android.domain.usecases.ResumeOutcome
import app.knotwork.android.domain.usecases.ResumePipelineRunUseCase
import app.knotwork.android.domain.usecases.SaveMessageToMemoryUseCase
import app.knotwork.android.domain.usecases.SaveToMemoryOutcome
import app.knotwork.android.domain.usecases.SubmitApprovalDecisionUseCase
import app.knotwork.android.domain.usecases.SubmitClarificationAnswerUseCase
import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatMessageStatus
import app.knotwork.design.components.chat.ChatMetadata
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsoleFilter
import app.knotwork.design.components.console.ConsoleLine
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleSource
import app.knotwork.design.components.console.ConsoleTab
import app.knotwork.design.components.console.ConsoleTraceSpan
import app.knotwork.design.components.console.ConsoleVarRow
import app.knotwork.design.screens.chat.ChatHomeMessageRow
import app.knotwork.design.screens.chat.ChatHomeThreadRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Hilt [ViewModel] backing the redesigned Knotwork chat home.
 *
 * Wires together:
 *  - [AgentOrchestratorUseCase] for the agent execution stream;
 *  - [ChatRepository] for session + message persistence;
 *  - [PipelineRepository] for pipeline-binding observation and the
 *    deleted-pipeline fallback Snackbar;
 *  - [SettingsRepository] for the persisted active session id and the
 *    context-window cap;
 *  - [LlmInferenceEngine] for the "load a model first" gate;
 *  - [GetContextWindowUseCase] for the rough token-counter TopAppBar
 *    readout (v0.1 — `text.length / 4`).
 *
 * Covers the user-prompted generation cycle (`Idle → Generating →
 * Idle / Error`), HITL (`WaitingForApproval`) and Clarification
 * (`AwaitingClarification`) wiring, Console pane (`ConsoleLog`) streaming
 * plus persisted-trace replay on session open (baseline from
 * [RunTraceRepository], live snapshots merged by [ConsoleEvent.seq]),
 * pipeline binding + deleted-pipeline fallback, session initialisation +
 * thread switching, the token counter, and the drawer / composer / overflow
 * secondary actions (new-thread, rename, favorite, import, model picker,
 * settings).
 *
 * Everything the screen renders is aggregated into a single immutable
 * [ChatHomeScreenState] exposed through [state]; every mutation funnels
 * through `_state.update { it.copy(...) }`. One-shot side effects (export
 * payloads, snackbars, the pipeline-fallback signal) stay on dedicated
 * [SharedFlow] channels because replaying them on re-subscription would
 * duplicate the side effect.
 */
@HiltViewModel
@Suppress(
    // Reason: Chat home is the single entry-point for every user-visible
    // agent interaction. The render state is consolidated into one
    // ChatHomeScreenState flow, but the *intent* surface remains wide by
    // design: messaging, session switching, console pane, HITL +
    // clarification, model picker, import/export each contribute public
    // intent methods plus their private observers — well above the
    // 25-function gate. Splitting would scatter one screen's contract
    // across artificial helper classes.
    "TooManyFunctions",
)
class ChatHomeViewModel @Inject constructor(
    private val agentOrchestratorUseCase: AgentOrchestratorUseCase,
    private val chatRepository: ChatRepository,
    private val pipelineRepository: PipelineRepository,
    private val settingsRepository: SettingsRepository,
    private val getContextWindowUseCase: GetContextWindowUseCase,
    private val llmInferenceEngine: LlmInferenceEngine,
    private val clarificationRepository: ClarificationRepository,
    private val localModelRepository: LocalModelRepository,
    private val loadModelUseCase: LoadModelUseCase,
    private val memoryAutoExtractionCoordinator: MemoryAutoExtractionCoordinator,
    private val saveMessageToMemoryUseCase: SaveMessageToMemoryUseCase,
    private val pipelineRunRepository: PipelineRunRepository,
    private val runTraceRepository: RunTraceRepository,
    private val resumePipelineRunUseCase: ResumePipelineRunUseCase,
    private val pendingInteractionRepository: PendingInteractionRepository,
    private val submitApprovalDecisionUseCase: SubmitApprovalDecisionUseCase,
    private val submitClarificationAnswerUseCase: SubmitClarificationAnswerUseCase,
) : ViewModel() {

    private val _state: MutableStateFlow<ChatHomeScreenState> = MutableStateFlow(ChatHomeScreenState())

    /**
     * Single source of truth for the chat-home surface. The screen performs
     * one `collectAsStateWithLifecycle` on this flow and hands the immutable
     * sub-structures (composer, console, pending, thread, model, tokens)
     * down the tree as-is.
     */
    val state: StateFlow<ChatHomeScreenState> = _state.asStateFlow()

    private val _pipelineFallbackEvents: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
    private val _consoleSnackbarEvents: MutableSharedFlow<ConsoleSnackbarEvent> =
        MutableSharedFlow(extraBufferCapacity = 1)
    private val _exportEvents: MutableSharedFlow<ChatExportPayload> = MutableSharedFlow(extraBufferCapacity = 1)
    private val _importErrorEvents: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)
    private val _memorySaveEvents: MutableSharedFlow<MemorySaveEvent> = MutableSharedFlow(extraBufferCapacity = 1)
    private val _resumeFeedbackEvents: MutableSharedFlow<ResumeFeedbackEvent> =
        MutableSharedFlow(extraBufferCapacity = 1)

    /**
     * One-shot signal raised when the binding of the active chat points
     * at a pipeline that no longer exists in the library (deleted, or
     * stale at thread-switch time) and the chat is rebound to the default
     * pipeline. The screen surfaces a `KnotworkSnackbar` so the user is
     * told their selection moved — the rebind is never silent.
     */
    val pipelineFallbackEvents: SharedFlow<Unit> = _pipelineFallbackEvents.asSharedFlow()

    /**
     * One-shot stream of snackbar events raised by the console pane (line
     * copied, full log copied). The screen mirrors each emission into the
     * shared `SnackbarHostState`; the clipboard write itself is performed
     * by the Composable (which owns `LocalClipboardManager`).
     */
    val consoleSnackbarEvents: SharedFlow<ConsoleSnackbarEvent> = _consoleSnackbarEvents.asSharedFlow()

    /**
     * One-shot stream raised when the user picks `Export chat` from the
     * overflow menu. The screen consumes each payload via a `LaunchedEffect`
     * and dispatches a system share-sheet (`Intent.ACTION_SEND`).
     */
    val exportEvents: SharedFlow<ChatExportPayload> = _exportEvents.asSharedFlow()

    /**
     * One-shot stream of import-failure messages. Surfaced via the shared
     * `SnackbarHostState`; the carried string is a localised user-visible
     * description ("Could not read the selected file", JSON parse error, …).
     */
    val importErrorEvents: SharedFlow<String> = _importErrorEvents.asSharedFlow()

    /**
     * One-shot stream of "Save to memory" outcomes raised by the message
     * long-press action. The screen maps each event to a snackbar
     * ("Saved to memory" / failure copy).
     */
    val memorySaveEvents: SharedFlow<MemorySaveEvent> = _memorySaveEvents.asSharedFlow()

    /**
     * One-shot signal raised when a Resume tap on the interrupted-run card
     * could not start the checkpoint resume. Each variant maps to its own
     * snackbar copy on the screen; a successful resume emits nothing here —
     * the surface flips to `Generating` and the live state flow takes over.
     */
    val resumeFeedbackEvents: SharedFlow<ResumeFeedbackEvent> = _resumeFeedbackEvents.asSharedFlow()

    private var messagesJob: Job? = null
    private var generationJob: Job? = null
    private var tokenCounterJob: Job? = null
    private var sessions: List<ChatSession> = emptyList()
    private var availablePipelinesObserved: Boolean = false
    private var defaultPipelineId: String? = null

    /** Serializes reattach lookups so a rapid thread switch cannot interleave run branches. */
    private var reattachJob: Job? = null

    /**
     * Session ids that currently own a pipeline run in a non-terminal status.
     * Fed by [observeActiveRuns] and projected into the drawer thread rows as
     * the in-progress badge ([buildThreadRows]).
     */
    private var activeRunSessionIds: Set<String> = emptySet()

    /**
     * Number of [ConsoleEvent]s the user has already dismissed via
     * `Clear`. The engine emits cumulative [AgentOrchestratorState.ConsoleLog]
     * snapshots on every step, so the next snapshot post-Clear still
     * carries every previously-visible event; the baseline trims the
     * leading slice so cleared rows do not pop back in. Reset on every new
     * send / session switch (legacy `ChatViewModel.consoleClearBaseline`).
     */
    private var consoleClearBaseline: Int = 0

    /**
     * Per-node ordered map of the latest [AgentOrchestratorState.NodeIO]
     * snapshot for the active run. Kept on the VM (not in the flow) so
     * repeated emissions for the same node id (e.g. a queue-processor loop
     * revisiting the same body node) overwrite the previous I/O instead of
     * appending duplicate Vars rows.
     */
    private val nodeIoSnapshots: LinkedHashMap<String, AgentOrchestratorState.NodeIO> = LinkedHashMap()

    /** Counts of trace-step landings; used to assign a deterministic startedAt to each span. */
    private val traceStepStartMs: MutableList<Long> = mutableListOf()

    /**
     * Console replay baseline loaded from the persistent run trace when the
     * session opens, or `null` when none is loaded. The run id and its
     * replayed events travel as one value ([ReplayedBaseline]) so they can
     * never desynchronize: a live [AgentOrchestratorState.ConsoleLog]
     * snapshot merges with the events only when it carries the same run id —
     * events of a *different* run replace the baseline outright (a fresh
     * send already cleared it via [resetConsoleCachesForNewRun]).
     */
    private var replayedBaseline: ReplayedBaseline? = null

    /**
     * Dispatcher carrying the CPU-bound projection of a replayed trace
     * (filtering and mapping the full record list to console rows). Off-main
     * by default so a long run's one-shot projection cannot jank the session
     * open; swapped for the test dispatcher in unit tests.
     */
    @VisibleForTesting
    internal var traceProjectionDispatcher: CoroutineDispatcher = Dispatchers.Default

    init {
        observeAvailablePipelines()
        observeDefaultPipelineId()
        observeSessions()
        observeMaxContextSize()
        observeConsolePreferredTab()
        observeInstalledModels()
        observeActiveRuns()
        initializeSession()
    }

    /** Updates the composer input value. Hoisted to the VM so screen recompositions never own the text. */
    fun onComposerValueChange(value: String) {
        _state.update { it.copy(composer = it.composer.copy(value = value)) }
    }

    /** Updates the typed-confirm input shown next to the Destructive HITL confirmation row. */
    fun onTypedConfirmChange(value: String) {
        _state.update { it.copy(composer = it.composer.copy(typedConfirm = value)) }
    }

    /**
     * Sends the current composer draft through [AgentOrchestratorUseCase].
     * No-op when the composer is blank, when generation is already in
     * flight, or when no LLM model is loaded (in the last case the surface
     * flips to [ChatHomeUiState.Error] so the user sees actionable
     * guidance).
     *
     * The user prompt itself is persisted by the orchestrator's pipeline
     * (see `TaskQueueManagerImpl.processTask` step 1), not by this VM —
     * persisting it twice would surface duplicate rows in the display
     * flow. Same for the final agent reply: `OutputNodeExecutor` writes
     * the `isFinal = true` row when the pipeline reaches OUTPUT.
     */
    fun sendMessage() {
        val draft = _state.value.composer.value.trim()
        if (draft.isEmpty()) return
        if (_state.value.visual is ChatHomeUiState.Generating) return
        if (!llmInferenceEngine.isInitialized) {
            // Surface the error with copy that matches what Retry actually
            // does — Retry attempts to load the active model in-place
            // through `retryAfterError`, not "open Settings and load it
            // there". If no active model is registered the load fails and
            // the error message switches to the Settings-redirect copy.
            _state.update { it.copy(visual = ChatHomeUiState.Error(MODEL_NOT_LOADED_MESSAGE)) }
            return
        }
        _state.update { it.copy(composer = it.composer.copy(value = "")) }

        val sessionId = _state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        val pipelineId = sessions.firstOrNull { it.id == sessionId }?.pipelineId

        // Fresh run = fresh cumulative log upstream; the baseline carried
        // over from a previous run's mid-stream Clear no longer applies
        // (the engine restarts events from scratch), and an in-flight
        // reattach lookup is cancelled with it. Mirrors legacy
        // `ChatViewModel.sendMessage`.
        resetConsoleCachesForNewRun()
        // Single atomic transition: flip to Generating, reset the streaming
        // token counter (the pill displays "generating · 0 tok" →
        // "generating · N tok" rather than carrying over the previous
        // response's final count), drop pending HITL / clarification
        // snapshots, and clear the console projections of the previous run.
        _state.update { current ->
            current.withPendingCleared().withConsoleProjectionsCleared().copy(
                visual = ChatHomeUiState.Generating,
                tokens = current.tokens.copy(streaming = 0),
            )
        }

        autoRenameIfDefault(sessionId, draft)

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            agentOrchestratorUseCase(sessionId, draft, pipelineId)
                .catch { error ->
                    _state.update {
                        it.copy(visual = ChatHomeUiState.Error(error.message ?: UNKNOWN_ERROR_FALLBACK))
                    }
                }
                .collect { orchestratorState -> handleOrchestratorState(orchestratorState) }
        }
    }

    /**
     * Handles the Retry CTA on the chat error tile. The previous behaviour
     * simply flipped to Idle, which on the "model not loaded" branch
     * meant the user had to manually open Settings → Models → load —
     * Retry never actually did anything. Now Retry actively tries to
     * recover:
     *
     *  - If the inference engine isn't initialised, runs
     *    `LoadModelUseCase()` (null path = active model). Success settles
     *    to the resting state; failure flips back to Error with copy that
     *    explicitly redirects to Settings (typically "no active model
     *    registered" — only Settings can fix that).
     *  - Otherwise the engine is healthy and Retry collapses to "clear
     *    error → resting state", same as the prior behaviour.
     */
    fun retryAfterError() {
        if (llmInferenceEngine.isInitialized) {
            _state.update { it.copy(visual = it.restingVisual()) }
            return
        }
        // Surface a transient "loading…" hint via the generating state — the
        // catalog renders the same composer affordance and the user sees
        // forward motion. Reset back to Error / resting on the load result.
        _state.update { it.copy(visual = ChatHomeUiState.Generating) }
        viewModelScope.launch {
            val outcome = loadModelUseCase()
            // The resting visual is derived from the update receiver, not a
            // pre-computed snapshot — a message emission landing while
            // `loadModelUseCase` was suspended must be reflected here.
            _state.update { current ->
                current.copy(
                    visual = when (outcome) {
                        is Result.Success -> current.restingVisual()
                        is Result.Error -> ChatHomeUiState.Error(outcome.message ?: NO_ACTIVE_MODEL_MESSAGE)
                    },
                )
            }
        }
    }

    /**
     * Detaches the UI from the active generation stream. This does NOT
     * cancel the execution itself: there is no cancel-task API on
     * [app.knotwork.android.domain.engine.TaskQueueManager], and the run is
     * driven by the queue's own process-lifetime scope — cancelling the
     * VM-side collector only stops the screen from rendering its progress.
     * The run keeps executing in the background (the drawer's in-progress
     * indicator stays honest about that), its final answer still lands in
     * the conversation, and reopening the session re-attaches to it via the
     * reattach protocol. A real cancel path is deliberately deferred to the
     * background-execution work that owns run lifecycle end-to-end.
     */
    fun stopGeneration() {
        generationJob?.cancel()
        reattachJob?.cancel()
        _state.update { current ->
            val cleared = current.withPendingCleared()
            if (current.visual is ChatHomeUiState.Generating) {
                cleared.copy(visual = cleared.restingVisual())
            } else {
                cleared
            }
        }
    }

    /** Opens the drawer overlay. */
    fun openDrawer() {
        _state.update { it.copy(visual = ChatHomeUiState.DrawerOpen) }
    }

    /** Closes the drawer overlay, settling on the right state for the current message list. */
    fun closeDrawer() {
        _state.update { current ->
            if (current.visual is ChatHomeUiState.DrawerOpen) {
                current.copy(visual = current.restingVisual())
            } else {
                current
            }
        }
    }

    /**
     * Switches the active chat session. Persists the selection, cancels
     * any in-flight generation, and re-subscribes the message stream to
     * the newly-selected session.
     *
     * The previous thread's rows are cleared in the same atomic state
     * update that flips the surface to `Empty` and installs the new
     * session id — otherwise [restingVisual] would read stale data from
     * the message list and resolve to `Idle` while the UI is conceptually
     * empty, producing a brief flicker of the outgoing thread's content.
     */
    fun selectThread(threadId: String) {
        if (threadId.isBlank() || threadId == _state.value.thread.currentSessionId) return
        generationJob?.cancel()
        messagesJob?.cancel()
        resetConsoleCachesForNewRun()
        _state.update { current ->
            val cleared = current.withPendingCleared().withConsoleProjectionsCleared()
            cleared.copy(
                console = cleared.console.copy(snap = null),
                messages = emptyList(),
                tokens = cleared.tokens.copy(used = 0),
                visual = ChatHomeUiState.Empty,
                thread = cleared.thread.copy(currentSessionId = threadId),
            ).withSessionMetadataRefreshed()
        }
        observeMessages(threadId)
        reattachToRun(threadId)
        viewModelScope.launch {
            settingsRepository.setCurrentChatSessionId(threadId)
            // The pipelines / sessions flows do not re-emit on a thread
            // switch, so a binding that went stale since their last
            // emission would otherwise reach the task queue silently.
            // Re-running the deleted-binding check here rebinds the chat
            // to the default and surfaces the one-shot Snackbar instead.
            handleDeletedBoundPipeline(_state.value.availablePipelines)
        }
    }

    /**
     * Opens the console pane at the given [snap] (default: Partial). The
     * pane is an independent overlay — the underlying [ChatHomeScreenState.visual]
     * is left untouched, so the user can drill into pipeline activity during
     * Generating / HitlConfirm / Clarification without losing their place.
     */
    fun openConsole(snap: ConsoleSnap = ConsoleSnap.Partial) {
        _state.update { it.copy(console = it.console.copy(snap = snap)) }
    }

    /**
     * Updates the snap point of the currently-open console pane. No-op
     * when the pane is closed.
     */
    fun setConsoleSnap(snap: ConsoleSnap) {
        _state.update { current ->
            if (current.console.snap != null) {
                current.copy(console = current.console.copy(snap = snap))
            } else {
                current
            }
        }
    }

    /** Dismisses the console pane without touching the underlying chat state. */
    fun closeConsole() {
        _state.update { it.copy(console = it.console.copy(snap = null)) }
    }

    /** Toggles the console inline-search field. Cycles `null → "" → null`. */
    fun toggleConsoleSearch() {
        _state.update { current ->
            val next = if (current.console.searchQuery == null) "" else null
            current.copy(console = current.console.copy(searchQuery = next))
        }
    }

    /** Updates the console inline-search query while the field is visible. */
    fun onConsoleSearchQueryChange(query: String) {
        _state.update { it.copy(console = it.console.copy(searchQuery = query)) }
    }

    /** Replaces the active console source-set filter. */
    fun onConsoleFilterChange(filter: ConsoleFilter) {
        _state.update { it.copy(console = it.console.copy(filter = filter)) }
    }

    /** Reacts to the long-press "Only show this source" menu item. */
    fun filterConsoleByLineSource(source: ConsoleSource) {
        _state.update { it.copy(console = it.console.copy(filter = ConsoleFilter(sources = setOf(source)))) }
    }

    /**
     * Persists the user's currently-selected console tab. Mirrors the
     * change into the local console state synchronously so the catalog
     * tab strip re-renders without waiting for the DataStore round-trip.
     */
    fun onConsoleTabChange(tab: ConsoleTab) {
        if (_state.value.console.tab == tab) return
        _state.update { it.copy(console = it.console.copy(tab = tab)) }
        viewModelScope.launch {
            settingsRepository.setConsolePreferredConsoleTabName(tab.name)
        }
    }

    /**
     * Requests the destructive "Clear console for this session?" dialog —
     * the screen renders an [AlertDialog] driven by
     * [ChatHomeScreenState.consoleClearConfirmRequested]. The actual clear
     * runs on [confirmConsoleClear] so the user has a chance to back out.
     */
    fun requestConsoleClear() {
        if (_state.value.console.logs.isEmpty()) return
        _state.update { it.copy(consoleClearConfirmRequested = true) }
    }

    /** Dismisses the confirmation dialog without altering the log. */
    fun dismissConsoleClear() {
        _state.update { it.copy(consoleClearConfirmRequested = false) }
    }

    /**
     * Advances [consoleClearBaseline] by the count of currently-visible
     * lines and clears the console Logs tab. The next cumulative engine
     * snapshot trims its leading slice, so cleared rows do not pop back in.
     */
    fun confirmConsoleClear() {
        val visible = _state.value.console.logs
        _state.update { it.copy(consoleClearConfirmRequested = false) }
        if (visible.isEmpty()) return
        consoleClearBaseline += visible.size
        _state.update { it.copy(console = it.console.copy(logs = emptyList())) }
    }

    /**
     * Emits a one-shot snackbar event after the screen has placed the
     * single-line clipboard payload on the system clipboard. The
     * `ClipboardManager` interaction itself happens inside the Composable
     * layer (which has access to `LocalClipboardManager`).
     */
    fun signalConsoleLineCopied() {
        _consoleSnackbarEvents.tryEmit(ConsoleSnackbarEvent.LineCopied)
    }

    /** One-shot snackbar event raised after the full filtered log is copied. */
    fun signalConsoleAllCopied() {
        _consoleSnackbarEvents.tryEmit(ConsoleSnackbarEvent.AllCopied)
    }

    /**
     * Renders a single [ConsoleLine] as the plain-text clipboard payload
     * inserted by `onConsoleCopyLine`. Format: `[timestamp] [source] text`.
     * Public for testability — kept on the VM (not the screen) so unit
     * tests can pin the format without spinning up the Compose tooling.
     */
    fun buildConsoleLineCopyPayload(line: ConsoleLine): String =
        "[${line.timestamp}] [${line.source.name}] ${line.text}"

    /**
     * Renders the supplied list of [ConsoleLine]s as the multi-line
     * clipboard payload inserted by `onConsoleCopyAll`. The caller is
     * expected to apply the current [ConsoleFilter] / search query before
     * passing the list in — the chat-home `Copy all` action only copies
     * what the user is actively looking at.
     */
    fun buildConsoleAllCopyPayload(lines: List<ConsoleLine>): String =
        lines.joinToString(separator = "\n") { buildConsoleLineCopyPayload(it) }

    /**
     * Debug-only escape hatch used by the triple-tap state picker to force
     * the surface into an arbitrary visual state. The picker UI is gated on
     * `BuildConfig.DEBUG`, but the VM accepts the call in any build so
     * unit tests can drive the state machine deterministically.
     */
    fun forceState(state: ChatHomeUiState) {
        _state.update { it.copy(visual = state) }
    }

    /**
     * Loads or restores the active chat session, mirroring legacy
     * `ChatViewModel.initializeSession`. Either reuses the id persisted
     * in [SettingsRepository.currentChatSessionId] or generates a fresh
     * one and creates an empty [ChatSession] for it.
     */
    private fun initializeSession() {
        viewModelScope.launch {
            val savedSessionId = settingsRepository.currentChatSessionId.first()
            val sessionId = if (savedSessionId.isNullOrBlank()) {
                val newId = UUID.randomUUID().toString()
                settingsRepository.setCurrentChatSessionId(newId)
                chatRepository.saveSession(
                    ChatSession(
                        id = newId,
                        name = DEFAULT_NEW_CHAT_NAME,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                newId
            } else {
                savedSessionId
            }
            _state.update {
                it.copy(thread = it.thread.copy(currentSessionId = sessionId)).withSessionMetadataRefreshed()
            }
            observeMessages(sessionId)
            reattachToRun(sessionId)
        }
    }

    /**
     * Continuously observes the pipeline library. Used for three things:
     *  1. Caching the available pipelines so [sendMessage] can resolve
     *     the bound pipeline name.
     *  2. Recomputing the TopAppBar subtitle ([ChatHomeScreenState.pipelineName]).
     *  3. Detecting deletion of the pipeline bound to the active chat
     *     and rebinding it to the default pipeline, surfacing a one-shot
     *     Snackbar via [pipelineFallbackEvents].
     */
    private fun observeAvailablePipelines() {
        viewModelScope.launch {
            pipelineRepository.getAllPipelines().collect { graphs ->
                val summaries = graphs.map { PipelineSummary(id = it.id, name = it.name) }
                _state.update { it.copy(availablePipelines = summaries).withPipelineNameRefreshed() }
                availablePipelinesObserved = true
                handleDeletedBoundPipeline(summaries)
            }
        }
    }

    /**
     * Observes the installed-model list and mirrors it into the
     * [ChatHomeModelState] slice. Also keeps the model display name in sync
     * with the currently active model so the TopAppBar subtitle always
     * reflects the real inference engine bound to the chat.
     */
    private fun observeInstalledModels() {
        viewModelScope.launch {
            localModelRepository.getAllModels().collect { models ->
                val active = models.firstOrNull { it.isActive }
                _state.update {
                    it.copy(
                        model = ChatHomeModelState(
                            name = active?.name ?: ChatHomeModelState.DEFAULT_NAME,
                            installed = models,
                            activeId = active?.id,
                        ),
                    )
                }
            }
        }
    }

    /** Observes the user-set default pipeline id and refreshes the subtitle when it changes. */
    private fun observeDefaultPipelineId() {
        viewModelScope.launch {
            settingsRepository.defaultPipelineId.collect { id ->
                defaultPipelineId = id
                _state.update { it.withPipelineNameRefreshed() }
            }
        }
    }

    /** Observes the session list. Keeps a cache for pipeline-id lookups and refreshes the title + subtitle. */
    private fun observeSessions() {
        viewModelScope.launch {
            chatRepository.getSessionsFlow().collect { current ->
                sessions = current
                _state.update { it.withSessionMetadataRefreshed() }
                handleDeletedBoundPipeline(_state.value.availablePipelines)
            }
        }
    }

    /**
     * Projects the live [sessions] cache into drawer thread rows. Favorited
     * sessions sort to the top of the drawer; the rest follow the
     * repository's `updatedAt DESC` ordering. The catalog drawer renders
     * the `selected`/`active` chrome from the matching flags here. Pure
     * projection — composed into a state update by
     * [withSessionMetadataRefreshed].
     *
     * @param activeId id of the active session used for the
     *   `selected`/`active` flags.
     */
    private fun buildThreadRows(activeId: String): List<ChatHomeThreadRow> {
        val sorted = sessions.sortedWith(
            compareByDescending<ChatSession> { it.isStarred }
                .thenByDescending { it.updatedAt },
        )
        return sorted.map { session ->
            ChatHomeThreadRow(
                id = session.id,
                title = session.name.ifBlank { ChatHomeThreadState.DEFAULT_TITLE },
                subtitle = formatThreadSubtitle(session.updatedAt),
                selected = session.id == activeId,
                active = session.id == activeId,
                starred = session.isStarred,
                running = session.id in activeRunSessionIds,
            )
        }
    }

    /** Observes the configured context-window cap and mirrors it into [ChatHomeTokenState.max]. */
    private fun observeMaxContextSize() {
        viewModelScope.launch {
            settingsRepository.maxContextLength.collect { value ->
                _state.update { it.copy(tokens = it.tokens.copy(max = value)) }
            }
        }
    }

    /**
     * Hydrates the console tab from the persisted DataStore preference. An
     * unrecognised value (e.g. an enum entry removed in a future version)
     * falls back to [ConsoleTab.Logs] so the surface never renders an
     * undefined tab.
     */
    private fun observeConsolePreferredTab() {
        viewModelScope.launch {
            settingsRepository.consolePreferredConsoleTabName.collect { name ->
                val tab = ConsoleTab.entries.firstOrNull { it.name == name } ?: ConsoleTab.Logs
                _state.update { it.copy(console = it.console.copy(tab = tab)) }
            }
        }
    }

    /**
     * Subscribes the message stream to [sessionId], cancelling any prior
     * subscription. Each emission is projected through
     * [chatMessageToRow] and folded into [ChatHomeScreenState.messages];
     * the rough token counter is recomputed *concurrently* via
     * [GetContextWindowUseCase] so that the suspending tokenisation never
     * stalls the collector (otherwise [rebalanceRestingState] would only
     * fire after the use case resumes, gating the UI behind a background
     * computation that could take dozens of milliseconds on a cold session).
     */
    private fun observeMessages(sessionId: String) {
        messagesJob?.cancel()
        tokenCounterJob?.cancel()
        if (sessionId.isBlank()) {
            _state.update { it.copy(messages = emptyList(), tokens = it.tokens.copy(used = 0)) }
            // No session to load — settle Loading to Empty so the surface
            // renders the empty-state hero instead of spinning forever.
            rebalanceRestingState()
            return
        }
        messagesJob = viewModelScope.launch {
            chatRepository.getDisplayMessagesForSession(sessionId).collect { incoming ->
                _state.update { current ->
                    current.copy(messages = incoming.map { chatMessageToRow(it, current.model.name) })
                }
                rebalanceRestingState()
                tokenCounterJob?.cancel()
                tokenCounterJob = launch {
                    val approx = try {
                        getContextWindowUseCase(sessionId).length / TOKEN_CHARS_PER_TOKEN
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        0
                    }
                    _state.update { it.copy(tokens = it.tokens.copy(used = approx)) }
                }
            }
        }
    }

    /**
     * Reacts to the message stream emitting an empty / non-empty list by
     * flipping between [ChatHomeUiState.Empty] and [ChatHomeUiState.Idle].
     * Active overlays (Generating, Error, Drawer, Console, HITL,
     * Clarification) are preserved — the resting state only matters when
     * no overlay is up.
     */
    private fun rebalanceRestingState() {
        _state.update { current ->
            when {
                // Cold-start: first message emission settles Loading into the
                // matching resting state (Empty if no messages, Idle if any).
                current.visual is ChatHomeUiState.Loading ->
                    current.copy(visual = current.restingVisual())
                current.visual is ChatHomeUiState.Empty && current.messages.isNotEmpty() ->
                    current.copy(visual = ChatHomeUiState.Idle)
                current.visual is ChatHomeUiState.Idle && current.messages.isEmpty() ->
                    current.copy(visual = ChatHomeUiState.Empty)
                else -> current
            }
        }
    }

    /** Branches on the orchestrator emission. Terminal states settle UI; intermediate states keep `Generating`. */
    private fun handleOrchestratorState(state: AgentOrchestratorState) {
        when (state) {
            is AgentOrchestratorState.WaitingForApproval -> handleWaitingForApproval(state)
            is AgentOrchestratorState.AwaitingClarification -> handleAwaitingClarification(state.request)
            is AgentOrchestratorState.ConsoleLog -> handleConsoleLog(state.events, state.runId)
            is AgentOrchestratorState.PipelineTrace -> handlePipelineTrace(state.steps)
            is AgentOrchestratorState.NodeIO -> handleNodeIo(state)
            is AgentOrchestratorState.Thinking ->
                // Approximate-token estimate from the cumulative partial text
                // length divided by `TOKEN_CHARS_PER_TOKEN`. Same heuristic
                // we use for the chat-level token meter.
                updateStreamingTokens(state.partialText.length / TOKEN_CHARS_PER_TOKEN)
            is AgentOrchestratorState.Answering ->
                updateStreamingTokens(state.partialText.length / TOKEN_CHARS_PER_TOKEN)
            is AgentOrchestratorState.Completed -> {
                _state.update { current ->
                    current.withPendingCleared().copy(
                        tokens = current.tokens.copy(streaming = 0),
                        visual = current.restingVisual(),
                    )
                }
                // Fire-and-forget: distil durable facts from the just-finished
                // conversation into long-term memory. The coordinator debounces
                // and owns its own scope, so this survives ViewModel teardown.
                memoryAutoExtractionCoordinator.onPipelineCompleted(_state.value.thread.currentSessionId)
            }
            is AgentOrchestratorState.Error -> {
                _state.update {
                    it.withPendingCleared().copy(
                        tokens = it.tokens.copy(streaming = 0),
                        visual = ChatHomeUiState.Error(state.message),
                    )
                }
            }
            // Intermediate states keep `Generating` while the request is in flight.
            else -> Unit
        }
    }

    /** Mirrors the running streamed-token estimate into [ChatHomeTokenState.streaming]. */
    private fun updateStreamingTokens(tokens: Int) {
        _state.update { it.copy(tokens = it.tokens.copy(streaming = tokens)) }
    }

    /**
     * Mirrors a cumulative [AgentOrchestratorState.ConsoleLog.events]
     * snapshot into the console Logs tab. The snapshot is first merged with
     * the replayed baseline of the same run (deduplicated by
     * [ConsoleEvent.seq]), then the clear baseline is applied so events the
     * user just dismissed via [confirmConsoleClear] stay hidden until the
     * next session switch / send.
     */
    private fun handleConsoleLog(events: List<ConsoleEvent>, runId: String?) {
        val merged = mergeWithReplayedBaseline(events, runId)
        val trimmed = applyConsoleClearBaseline(merged)
        _state.update { it.copy(console = it.console.copy(logs = trimmed.map(ConsoleEvent::toConsoleLine))) }
    }

    /**
     * Merges a live cumulative console snapshot with the replayed baseline
     * via [mergeConsoleEventsBySeq]. When the live snapshot belongs to a
     * different run — or to no persisted run at all — the baseline is
     * irrelevant and the live snapshot passes through untouched.
     *
     * @param live The cumulative live snapshot from the engine.
     * @param liveRunId The persistent run the live snapshot belongs to.
     * @return The merged event list ordered by seq.
     */
    private fun mergeWithReplayedBaseline(live: List<ConsoleEvent>, liveRunId: String?): List<ConsoleEvent> {
        val baseline = replayedBaseline ?: return live
        if (baseline.events.isEmpty() || liveRunId == null || liveRunId != baseline.runId) return live
        return mergeConsoleEventsBySeq(baseline.events, live)
    }

    /**
     * Loads the persisted trace of [run] — resolved once by [reattachToRun]
     * — and installs it as the console baseline: Logs from the replayed
     * console events, Vars and Traces re-projected from the replayed
     * per-node I/O records through the exact same mappers as the live path.
     * The CPU-bound projection of the full trace runs on
     * [traceProjectionDispatcher]; only the VM-confined cache and state
     * installation happen back on the main dispatcher. A run without a
     * persisted trace leaves the console empty, which matches the
     * pre-replay behaviour. Must complete before the live collector
     * subscribes (see the ordering note on [reattachToRun]).
     */
    private suspend fun replayConsoleTrace(run: PipelineRun) {
        val trace = runTraceRepository.getTraceForRun(run.id)
        if (trace.isEmpty()) return
        // Snapshot the main-confined clear baseline before hopping off
        // the main dispatcher; the projection applies it as a plain drop
        // (the equivalent of applyConsoleClearBaseline for a fresh list).
        val clearBaseline = consoleClearBaseline
        val projection = withContext(traceProjectionDispatcher) {
            val consoleEvents = trace
                .filterIsInstance<RunTraceRecord.ConsoleEntry>()
                .map(::consoleEntryToConsoleEvent)
            val nodeIoRecords = trace.filterIsInstance<RunTraceRecord.NodeIo>()
            val snapshots = nodeIoRecords.map { it.nodeId to nodeIoRecordToNodeIo(it) }
            ReplayProjection(
                baseline = ReplayedBaseline(runId = run.id, events = consoleEvents),
                nodeIoSnapshots = snapshots,
                logs = consoleEvents.drop(clearBaseline).map(ConsoleEvent::toConsoleLine),
                vars = snapshots.flatMap { (_, io) -> nodeIoToVarRows(io) },
                traces = nodeIoRecords.map(::nodeIoRecordToConsoleSpan),
            )
        }
        replayedBaseline = projection.baseline
        nodeIoSnapshots.clear()
        projection.nodeIoSnapshots.forEach { (nodeId, io) -> nodeIoSnapshots[nodeId] = io }
        _state.update {
            it.copy(
                console = it.console.copy(
                    logs = projection.logs,
                    vars = projection.vars,
                    traces = projection.traces,
                ),
            )
        }
    }

    /**
     * Chat reattach protocol — resolves the session's run record once and
     * re-binds the UI to whatever it says when the session is opened (cold
     * start or thread switch). The single lookup feeds both the console
     * trace replay and the reattach branching, so a session open costs at
     * most two run queries instead of four. Branches:
     *
     *  - **Active run** (QUEUED / RUNNING / WAITING_*) → subscribe to the live
     *    per-session state flow *without* enqueueing a new task; the
     *    suspension cards are restored from the authoritative pending
     *    snapshots (see [restoreSuspensionCard]) because the flow's replay
     *    cache may have been overwritten by console events.
     *  - **Latest run INTERRUPTED** → surface the interrupted-run status card
     *    (Resume / Discard) with the display label of the node the run
     *    stopped at.
     *  - **Anything else** (terminal run or no runs) → no live attach; the
     *    regular message flow + the replay above already render the outcome.
     *
     * Ordering matters at the console seam: the trace replay must install
     * its baseline **before** the live collector subscribes — the live
     * flow's replayed cumulative snapshot merges with the baseline by seq,
     * whereas the reverse order would let the (slower) replay projection
     * overwrite fresher live rows with the lagging persisted trace.
     *
     * The branch decision is made against the persistent status, never the
     * in-memory flow: WAITING_* runs of a dead process are settled to
     * INTERRUPTED by the startup orphan sweep (which runs under the splash
     * screen, before this ViewModel exists), so an active status here always
     * denotes a run that is genuinely alive in this process.
     */
    private fun reattachToRun(sessionId: String) {
        reattachJob?.cancel()
        reattachJob = viewModelScope.launch {
            val activeRun = pipelineRunRepository.getActiveRunForSession(sessionId)
            val baselineRun = activeRun ?: pipelineRunRepository.getLatestRunForSession(sessionId)
            // A rapid thread switch may have changed the active session while
            // the lookup suspended — applying the stale branch would bleed
            // the previous thread's run state into the new one.
            if (_state.value.thread.currentSessionId != sessionId) return@launch
            if (baselineRun != null) replayConsoleTrace(baselineRun)
            when {
                activeRun != null -> attachToLiveRun(sessionId, activeRun.status)
                baselineRun?.status == PipelineRunStatus.INTERRUPTED -> presentInterruptedRun(baselineRun)
                else -> Unit
            }
            watchForBackgroundRuns(sessionId, alreadyAttachedRunId = activeRun?.id)
        }
    }

    /**
     * Keeps watching the session's persistent run records after the one-shot
     * reattach so a run that *starts* while the session is already open still
     * attaches the UI to the live stream. The one-shot path only covers runs
     * that existed at session-open time; a scheduler-origin run firing into
     * the open session afterwards would otherwise stream its messages (the
     * Room flow is live) with no Generating state and no live console.
     *
     * Attach conditions: a non-terminal run the collector has not attached
     * yet **and** a resting/cold visual surface. The visual guard keeps the
     * watcher away from interactive sends — `sendMessage` flips the surface
     * to `Generating` synchronously before its run record can appear here,
     * and its own collector already owns the stream.
     *
     * Rides [reattachJob], so a thread switch (which re-runs the one-shot
     * path) or [onCleared] cancels the watcher with it.
     */
    private suspend fun watchForBackgroundRuns(sessionId: String, alreadyAttachedRunId: String?) {
        var attachedRunId = alreadyAttachedRunId
        pipelineRunRepository.observeRunsForSession(sessionId)
            .mapNotNull { runs -> runs.firstOrNull { !it.status.isTerminal } }
            .distinctUntilChangedBy { it.id }
            .collect { run ->
                if (run.id == attachedRunId) return@collect
                if (_state.value.thread.currentSessionId != sessionId) return@collect
                if (!_state.value.visual.isRestingOrCold()) return@collect
                attachedRunId = run.id
                attachToLiveRun(sessionId, run.status)
            }
    }

    /**
     * Live branch of the reattach protocol: subscribes the orchestrator-state
     * collector to [sessionId] without enqueueing a task, flips the surface
     * to `Generating` (the run is in flight — the composer must offer Stop,
     * not Send), and restores the HITL / clarification card when the
     * persistent [status] says the run is suspended on one.
     */
    private suspend fun attachToLiveRun(sessionId: String, status: PipelineRunStatus) {
        _state.update { current ->
            if (current.visual.isRestingOrCold()) {
                current.copy(visual = ChatHomeUiState.Generating)
            } else {
                current
            }
        }
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            agentOrchestratorUseCase.observe(sessionId)
                .catch { error ->
                    _state.update {
                        it.copy(visual = ChatHomeUiState.Error(error.message ?: UNKNOWN_ERROR_FALLBACK))
                    }
                }
                .collect { orchestratorState -> handleOrchestratorState(orchestratorState) }
        }
        restoreSuspensionCard(sessionId, status)
    }

    /**
     * Restores the trailing suspension card from the authoritative pending
     * snapshot when the persistent run [status] is a WAITING_* one. The live
     * flow cannot be relied on for this: its replay cache (depth 1) holds
     * whatever the engine emitted last, and console events emitted while the
     * run waits overwrite the `WaitingForApproval` / `AwaitingClarification`
     * emission. A pending snapshot that is already gone (the request was
     * resolved between the status read and this lookup) is a benign no-op —
     * the live subscription delivers the post-resolution states.
     */
    private suspend fun restoreSuspensionCard(sessionId: String, status: PipelineRunStatus) {
        when (status) {
            PipelineRunStatus.WAITING_APPROVAL -> {
                val live = agentOrchestratorUseCase.pendingApprovalFor(sessionId)
                if (live != null) {
                    handleWaitingForApproval(live)
                } else {
                    // Persistent phase (or a different process parked the
                    // run): rebuild the card from the durable record. The
                    // decision then routes through the parked-run submission
                    // path — the live deferred is gone.
                    pendingInteractionRepository.getForSession(sessionId)
                        ?.takeIf { it.kind == PendingInteractionKind.APPROVAL }
                        ?.let { parked ->
                            handleWaitingForApproval(
                                AgentOrchestratorState.WaitingForApproval(
                                    toolName = parked.toolName.orEmpty(),
                                    arguments = parked.toolArgs.orEmpty(),
                                    risk = parked.risk ?: ToolRisk.SENSITIVE,
                                ),
                            )
                        }
                }
            }
            PipelineRunStatus.WAITING_CLARIFICATION -> {
                val live = clarificationRepository.pendingRequests.first()
                    .lastOrNull { it.sessionId == sessionId }
                if (live != null) {
                    // No watchdog on restore: the repository's authoritative
                    // timeout has been running since the request was raised.
                    handleAwaitingClarification(live)
                } else {
                    // Persistent phase: re-render the persisted question. The
                    // synthetic request id (run id) can never match a live
                    // deferred, so the answer falls through to the parked-run
                    // submission path. No watchdog — the approval window is
                    // the authoritative clock now.
                    pendingInteractionRepository.getForSession(sessionId)
                        ?.takeIf { it.kind == PendingInteractionKind.CLARIFICATION }
                        ?.let { parked ->
                            handleAwaitingClarification(
                                ClarificationRequest(
                                    id = parked.runId,
                                    sessionId = parked.sessionId,
                                    question = parked.question.orEmpty(),
                                    options = parked.options,
                                    timeoutMs = 0L,
                                ),
                            )
                        }
                }
            }
            else -> Unit
        }
    }

    /**
     * Interrupted branch of the reattach protocol: installs the
     * interrupted-run snapshot (run id, resolved node label, interruption
     * timestamp) into the pending slice and flips the surface to
     * [ChatHomeUiState.Interrupted] so the mapping appends the status card
     * with Resume / Discard actions. The pending snapshot is installed
     * unconditionally — [restingVisual] resolves to `Interrupted` from it,
     * so the card surfaces as soon as any overlay (drawer, error) settles.
     * Only the immediate visual flip is guarded to resting/cold states: a
     * user mid-overlay must not have it yanked away.
     */
    private suspend fun presentInterruptedRun(run: PipelineRun) {
        val nodeLabel = resolveNodeLabel(run)
        // The card only offers Resume while the checkpoint is inside the
        // resume window and the record carries everything resume needs (the
        // original prompt — absent on legacy rows). The use case re-validates
        // on tap; this pre-check just keeps the CTA honest.
        val maxAgeMillis = settingsRepository.resumeMaxAgeHours.first() * MILLIS_PER_HOUR
        val interruptedAt = run.finishedAt ?: run.startedAt
        val resumable = run.userPrompt != null &&
            System.currentTimeMillis() - interruptedAt <= maxAgeMillis
        val pending = InterruptedRunPending(
            runId = run.id,
            nodeLabel = nodeLabel,
            // The card shows when the run actually died, not when the chat
            // was reopened — finishedAt is stamped by the orphan sweep's
            // terminal write; startedAt is the defensive fallback for a
            // record that somehow lost it.
            timestamp = formatMessageTimestamp(run.finishedAt ?: run.startedAt),
            resumable = resumable,
        )
        _state.update { current ->
            val withPending = current.copy(pending = current.pending.copy(interrupted = pending))
            if (current.visual.isRestingOrCold()) {
                withPending.copy(visual = ChatHomeUiState.Interrupted)
            } else {
                withPending
            }
        }
    }

    /**
     * Resolves the display label of the node [PipelineRun.currentNodeId]
     * points at by loading the run's pipeline graph. Falls back to
     * [INTERRUPTED_UNKNOWN_NODE_LABEL] when the run never reached a node, the
     * pipeline was deleted since, or the node id no longer exists in the
     * graph (the graph may have been edited after the interruption).
     */
    private suspend fun resolveNodeLabel(run: PipelineRun): String {
        val nodeId = run.currentNodeId ?: return INTERRUPTED_UNKNOWN_NODE_LABEL
        val graph = run.pipelineId?.let { pipelineRepository.getPipelineById(it) }
            ?: return INTERRUPTED_UNKNOWN_NODE_LABEL
        val node = graph.nodes.firstOrNull { it.id == nodeId } ?: return INTERRUPTED_UNKNOWN_NODE_LABEL
        return node.label.ifBlank { node.type.name }
    }

    /**
     * Discards the interrupted run surfaced by the status card: settles the
     * persistent record as FAILED with a "discarded by user" marker (a
     * guarded INTERRUPTED → FAILED transition — see
     * [PipelineRunRepository.discardInterruptedRun]) and drops the card. The
     * trace stays in the database until retention cleanup; only the resume
     * offer disappears. No-op when no interrupted run is pending.
     */
    fun discardInterruptedRun() {
        val pending = _state.value.pending.interrupted ?: return
        _state.update { current ->
            val cleared = current.copy(pending = current.pending.copy(interrupted = null))
            cleared.copy(visual = cleared.restingVisual())
        }
        viewModelScope.launch {
            pipelineRunRepository.discardInterruptedRun(pending.runId)
        }
    }

    /**
     * Resume CTA of the interrupted-run card. Delegates to
     * [ResumePipelineRunUseCase]: on success the card is dropped, the surface
     * flips to `Generating` and the live orchestrator-state collector
     * attaches — the resumed run then streams into the chat exactly like a
     * reattached background run. On failure the card stays up (the user can
     * still Discard) and the typed reason is surfaced through
     * [resumeFeedbackEvents]; an expired checkpoint additionally demotes the
     * card to its discard-only variant.
     */
    fun resumeInterruptedRun() {
        val pending = _state.value.pending.interrupted ?: return
        val sessionId = _state.value.thread.currentSessionId
        viewModelScope.launch {
            when (resumePipelineRunUseCase(pending.runId)) {
                ResumeOutcome.Resumed -> {
                    _state.update { current ->
                        val cleared = current.copy(pending = current.pending.copy(interrupted = null))
                        cleared.copy(visual = ChatHomeUiState.Generating)
                    }
                    attachToLiveRun(sessionId, PipelineRunStatus.QUEUED)
                }
                ResumeOutcome.GraphChanged -> _resumeFeedbackEvents.tryEmit(ResumeFeedbackEvent.GraphChanged)
                ResumeOutcome.Expired -> {
                    _state.update { current ->
                        val demoted = current.pending.interrupted?.copy(resumable = false)
                        current.copy(pending = current.pending.copy(interrupted = demoted))
                    }
                    _resumeFeedbackEvents.tryEmit(ResumeFeedbackEvent.Expired)
                }
                ResumeOutcome.NotResumable -> _resumeFeedbackEvents.tryEmit(ResumeFeedbackEvent.NotResumable)
            }
        }
    }

    /**
     * Mirrors the set of sessions owning a non-terminal run into
     * [activeRunSessionIds] and re-projects the drawer rows, so threads with
     * a run still working in the background render the in-progress badge.
     * The repository flow is already deduplicated, and only the rows are
     * rebuilt — title / pipeline-name resolution is untouched because a
     * badge flip cannot change either.
     */
    private fun observeActiveRuns() {
        viewModelScope.launch {
            pipelineRunRepository.observeActiveRunSessionIds().collect { sessionIds ->
                activeRunSessionIds = sessionIds
                _state.update { current ->
                    current.copy(
                        thread = current.thread.copy(rows = buildThreadRows(current.thread.currentSessionId)),
                    )
                }
            }
        }
    }

    /**
     * Whether this visual is a resting or cold-start state that a reattach
     * branch may safely overwrite. Active overlays (Generating, HITL,
     * Clarification, Error, Drawer) are user-facing context that the
     * asynchronous reattach lookup must never yank away.
     */
    private fun ChatHomeUiState.isRestingOrCold(): Boolean =
        this is ChatHomeUiState.Loading || this is ChatHomeUiState.Empty || this is ChatHomeUiState.Idle

    /**
     * Mirrors the latest [AgentOrchestratorState.PipelineTrace.steps]
     * snapshot into the console Traces tab. The catalog span requires a
     * pre-formatted `startedAt` — we observe wall-clock time the first
     * time we see each step index and reuse it for subsequent emissions of
     * the same step so the displayed start does not jitter on every
     * snapshot.
     */
    private fun handlePipelineTrace(steps: List<AgentOrchestratorState.TraceStep>) {
        val nowMs = System.currentTimeMillis()
        // Grow the start-timestamp cache to match the step count; existing entries are preserved.
        while (traceStepStartMs.size < steps.size) {
            traceStepStartMs.add(nowMs)
        }
        val spans = steps.mapIndexed { index, step ->
            traceStepToConsoleSpan(step, traceStepStartMs[index])
        }
        _state.update { it.copy(console = it.console.copy(traces = spans)) }
    }

    /**
     * Captures a per-node I/O snapshot and re-projects the console Vars tab
     * from the accumulated map so repeated emissions for the same node id
     * overwrite (not duplicate) the previous Vars rows.
     */
    private fun handleNodeIo(io: AgentOrchestratorState.NodeIO) {
        nodeIoSnapshots[io.nodeId] = io
        val vars = nodeIoSnapshots.values.flatMap(::nodeIoToVarRows)
        _state.update { it.copy(console = it.console.copy(vars = vars)) }
    }

    /**
     * Trims the leading [consoleClearBaseline] entries off a cumulative
     * [AgentOrchestratorState.ConsoleLog.events] snapshot. When the
     * baseline already covers the snapshot (no new events since the last
     * Clear) the result is an empty list.
     */
    private fun applyConsoleClearBaseline(events: List<ConsoleEvent>): List<ConsoleEvent> {
        if (consoleClearBaseline <= 0) return events
        if (consoleClearBaseline >= events.size) return emptyList()
        return events.subList(consoleClearBaseline, events.size)
    }

    /**
     * Drops the VM-side console caches (clear baseline, per-node I/O map,
     * trace start timestamps). Called at the start of each new run, always
     * paired with [withConsoleProjectionsCleared] inside the caller's
     * single `_state.update` block so the flow emission stays atomic.
     */
    private fun resetConsoleCachesForNewRun() {
        consoleClearBaseline = 0
        nodeIoSnapshots.clear()
        traceStepStartMs.clear()
        // A new run (or a thread switch) invalidates the replayed baseline:
        // the next live snapshot belongs to a different run, and a thread
        // switch reloads its own baseline via reattachToRun. The reattach
        // job is cancelled with it — a stale in-flight lookup must neither
        // re-install the old baseline nor re-subscribe the collector.
        reattachJob?.cancel()
        replayedBaseline = null
    }

    /**
     * Pure transformer: clears the console Logs / Vars / Traces projections
     * of the previous run. The pane's snap, tab, filter, and search query
     * survive — only run-scoped data is dropped. Composed into the caller's
     * `_state.update` block (never its own emission) so multi-field
     * transitions remain a single atomic flow emission.
     */
    private fun ChatHomeScreenState.withConsoleProjectionsCleared(): ChatHomeScreenState = copy(
        console = console.copy(
            logs = emptyList(),
            vars = emptyList(),
            traces = emptyList(),
        ),
    )

    /**
     * Approves the tool the orchestrator is paused on. For a destructive
     * tool the approval is gated on the typed-confirm matching the
     * canonical magic word (`"yes"`, trimmed, case-insensitive) — the
     * catalog `HitlConfirmationCard` already disables the Allow CTA in
     * that case, but the VM mirrors the gate defensively so a programmatic
     * caller (tests, automation) cannot bypass it.
     *
     * No-op when no tool is pending.
     */
    fun approveTool() {
        val pending = _state.value.pending.tool ?: return
        if (pending.risk == ToolRisk.DESTRUCTIVE && !isTypedConfirmValid()) return
        val sessionId = _state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        _state.update {
            it.copy(
                pending = it.pending.copy(tool = null),
                composer = it.composer.copy(typedConfirm = ""),
                visual = ChatHomeUiState.Generating,
            )
        }
        viewModelScope.launch { submitApprovalDecision(sessionId, isApproved = true) }
    }

    /**
     * Routes the user's approve / deny decision through
     * [SubmitApprovalDecisionUseCase] (live gate first, then the parked
     * record) and maps the persistent-phase outcomes onto the existing
     * resume plumbing: a resumed parked run attaches exactly like a resumed
     * interrupted run; failures surface through [resumeFeedbackEvents] and
     * settle the visual back to its resting state so the chat is not stuck
     * on `Generating` for a run that will never emit.
     *
     * @param sessionId Id of the session whose gate is being answered.
     * @param isApproved `true` to approve, `false` to deny.
     */
    private suspend fun submitApprovalDecision(sessionId: String, isApproved: Boolean) {
        when (submitApprovalDecisionUseCase(sessionId, isApproved)) {
            PendingSubmissionOutcome.LiveResumed -> Unit
            PendingSubmissionOutcome.Resumed -> attachToLiveRun(sessionId, PipelineRunStatus.QUEUED)
            PendingSubmissionOutcome.GraphChanged -> {
                _resumeFeedbackEvents.tryEmit(ResumeFeedbackEvent.GraphChanged)
                _state.update { it.copy(visual = it.restingVisual()) }
            }
            PendingSubmissionOutcome.Expired -> {
                _resumeFeedbackEvents.tryEmit(ResumeFeedbackEvent.Expired)
                _state.update { it.copy(visual = it.restingVisual()) }
            }
            PendingSubmissionOutcome.NothingPending ->
                _state.update { it.copy(visual = it.restingVisual()) }
        }
    }

    /**
     * Rejects the tool the orchestrator is paused on. Persists a SYSTEM
     * chat row recording the denial so the user can see in-thread what
     * happened — the legacy chat surface only cleared state, which left
     * the conversation looking like the agent had silently moved on.
     *
     * No-op when no tool is pending.
     */
    fun rejectTool() {
        val pending = _state.value.pending.tool ?: return
        val sessionId = _state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        // Resuming the pipeline restarts orchestrator emission — keep the
        // surface in `Generating` until the next state (or a terminal
        // Completed / Error) settles it, otherwise the chat appears idle
        // while the agent is actively producing the denial follow-up.
        _state.update {
            it.copy(
                pending = it.pending.copy(tool = null),
                composer = it.composer.copy(typedConfirm = ""),
                visual = ChatHomeUiState.Generating,
            )
        }
        viewModelScope.launch {
            submitApprovalDecision(sessionId, isApproved = false)
            chatRepository.saveMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = Role.SYSTEM,
                    content = SYSTEM_MESSAGE_TOOL_DENIED.format(pending.toolName),
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Submits the user's reply to the active clarification request and
     * cancels the watchdog timer. The pipeline coroutine resumes
     * immediately via [ClarificationRepository.submitClarification]; the
     * agent then publishes its next state, which the orchestrator stream
     * collector translates into the next UI tick.
     *
     * When the repository reports the reply was NOT consumed (`false` — the
     * request already resolved by timeout or an earlier answer, possible
     * after a reattach where the card outlives the repository's clock), a
     * SYSTEM chat row records that the agent proceeded without the reply —
     * silently dropping the user's choice would misrepresent what the
     * pipeline actually consumed.
     *
     * @param answer the user's reply text (option label or free-form).
     */
    fun submitClarificationReply(answer: String) {
        val pending = _state.value.pending.clarification ?: return
        val sessionId = _state.value.thread.currentSessionId
        // Allow an empty reply through — the orchestrator already accepts
        // `""` as the timeout fallback for free-form requests, so an
        // intentional blank submit is a legitimate "skip" affordance.
        val trimmed = answer.trim()
        _state.update {
            it.copy(
                pending = it.pending.copy(clarification = null),
                visual = ChatHomeUiState.Generating,
            )
        }
        viewModelScope.launch {
            when (submitClarificationAnswerUseCase(sessionId, pending.id, trimmed)) {
                PendingSubmissionOutcome.LiveResumed -> Unit
                PendingSubmissionOutcome.Resumed -> attachToLiveRun(sessionId, PipelineRunStatus.QUEUED)
                PendingSubmissionOutcome.GraphChanged -> {
                    _resumeFeedbackEvents.tryEmit(ResumeFeedbackEvent.GraphChanged)
                    _state.update { it.copy(visual = it.restingVisual()) }
                }
                PendingSubmissionOutcome.Expired -> {
                    _resumeFeedbackEvents.tryEmit(ResumeFeedbackEvent.Expired)
                    _state.update { it.copy(visual = it.restingVisual()) }
                }
                PendingSubmissionOutcome.NothingPending -> {
                    // Neither a live deferred nor a parked record consumed
                    // the reply — record in-thread that the agent proceeded
                    // without it, exactly like the legacy undelivered path.
                    _state.update { it.copy(visual = it.restingVisual()) }
                    if (sessionId.isNotBlank()) {
                        chatRepository.saveMessage(
                            ChatMessage(
                                sessionId = sessionId,
                                role = Role.SYSTEM,
                                content = SYSTEM_MESSAGE_CLARIFICATION_REPLY_NOT_DELIVERED,
                                timestamp = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Captures the orchestrator's pending approval and flips the UI to the HITL state. */
    private fun handleWaitingForApproval(state: AgentOrchestratorState.WaitingForApproval) {
        _state.update {
            it.copy(
                pending = it.pending.copy(
                    tool = HitlPending(
                        toolName = state.toolName,
                        arguments = state.arguments,
                        risk = state.risk,
                    ),
                ),
                composer = it.composer.copy(typedConfirm = ""),
                visual = ChatHomeUiState.HitlConfirm(state.risk.toCatalogRisk()),
            )
        }
    }

    /**
     * Captures the orchestrator's pending clarification and flips the UI to
     * the Clarification state. No UI-side timeout runs against the card:
     * the repository owns the live waiting window via `withTimeout`, and
     * when it elapses the run parks persistently (`WAITING_CLARIFICATION`)
     * instead of consuming a fabricated default answer — the card stays
     * answerable and the reply then routes through the parked-run
     * submission path.
     *
     * @param request the pending clarification to surface.
     */
    private fun handleAwaitingClarification(request: ClarificationRequest) {
        _state.update {
            it.copy(
                pending = it.pending.copy(clarification = request),
                visual = ChatHomeUiState.Clarification,
            )
        }
    }

    /** Whether the current typed-confirm input satisfies the destructive HITL gate. */
    private fun isTypedConfirmValid(): Boolean =
        _state.value.composer.typedConfirm.trim().equals(DESTRUCTIVE_TYPED_CONFIRM_WORD, ignoreCase = true)

    /**
     * Pure transformer: drops every pending HITL / clarification snapshot
     * and resets the typed-confirm input. Composed into the caller's
     * `_state.update` block (never its own emission) so multi-field
     * transitions remain a single atomic flow emission.
     */
    private fun ChatHomeScreenState.withPendingCleared(): ChatHomeScreenState = copy(
        pending = ChatHomePendingState(),
        composer = composer.copy(typedConfirm = ""),
    )

    /**
     * Auto-renames a newly-created chat to the first user message
     * (truncated to [AUTO_RENAME_CHAR_LIMIT] characters). Mirrors legacy
     * `ChatViewModel` behaviour so the drawer entry reflects the user's
     * framing.
     */
    private fun autoRenameIfDefault(sessionId: String, prompt: String) {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return
        if (session.name != DEFAULT_NEW_CHAT_NAME) return
        val truncated = if (prompt.length > AUTO_RENAME_CHAR_LIMIT) {
            prompt.take(AUTO_RENAME_CHAR_LIMIT) + AUTO_RENAME_SUFFIX
        } else {
            prompt
        }
        viewModelScope.launch {
            chatRepository.saveSession(session.copy(name = truncated))
        }
    }

    /**
     * Rebinds the active chat to the default pipeline when its binding
     * points at a pipeline that no longer exists in the library, and
     * raises the one-shot [pipelineFallbackEvents] Snackbar. Invoked from
     * the pipelines / sessions observers (deletion while the chat is
     * active) and from [selectThread] (binding already stale when the
     * thread is opened). No-op while the pipeline flow has not produced
     * its initial snapshot — without this guard a startup race would
     * misread the empty initial pipeline list as "the bound pipeline no
     * longer exists" and silently rebind every chat to the default.
     */
    private suspend fun handleDeletedBoundPipeline(summaries: List<PipelineSummary>) {
        if (!availablePipelinesObserved) return
        val session = sessions.firstOrNull { it.id == _state.value.thread.currentSessionId } ?: return
        val boundId = session.pipelineId ?: return
        if (summaries.any { it.id == boundId }) return
        chatRepository.saveSession(session.copy(pipelineId = null))
        _pipelineFallbackEvents.tryEmit(Unit)
    }

    /**
     * Pure transformer: recomputes the pipeline subtitle for this snapshot
     * from the [sessions] / [defaultPipelineId] caches. Composed into the
     * caller's `_state.update` block (never its own emission) so refreshes
     * ride the same atomic emission as the change that triggered them.
     */
    private fun ChatHomeScreenState.withPipelineNameRefreshed(): ChatHomeScreenState = copy(
        pipelineName = resolvePipelineName(
            sessions = sessions,
            currentSessionId = thread.currentSessionId,
            summaries = availablePipelines,
            defaultPipelineId = defaultPipelineId,
        ),
    )

    /**
     * Resolves the pipeline display name for the active chat — explicit
     * binding when set, otherwise the user-marked default. Returns `null`
     * when neither resolves (empty library, or no default marked): the
     * subtitle must not advertise a pipeline that execution would never
     * pick, so there is no order-dependent "first in the library" fallback.
     */
    private fun resolvePipelineName(
        sessions: List<ChatSession>,
        currentSessionId: String,
        summaries: List<PipelineSummary>,
        defaultPipelineId: String?,
    ): String? {
        if (summaries.isEmpty()) return null
        val session = sessions.firstOrNull { it.id == currentSessionId }
        val boundId = session?.pipelineId
        val boundMatch = boundId?.let { id -> summaries.firstOrNull { it.id == id } }
        if (boundMatch != null) return boundMatch.name
        return defaultPipelineId?.let { id -> summaries.firstOrNull { it.id == id } }?.name
    }

    /**
     * Pure transformer: recomputes thread title + favorite, the drawer
     * rows, and the pipeline subtitle for this snapshot from the latest
     * session/pipeline caches. Composed into the caller's single
     * `_state.update` block so one upstream emission (sessions flow,
     * session init, thread switch) produces exactly one state emission —
     * collectors never observe a frame with a fresh title but stale rows.
     *
     * Title/favorite are left untouched when the active session id is
     * non-blank but missing from the cache (mid-deletion window) —
     * mirrors the pre-consolidation behaviour.
     */
    private fun ChatHomeScreenState.withSessionMetadataRefreshed(): ChatHomeScreenState {
        val currentId = thread.currentSessionId
        val session = sessions.firstOrNull { it.id == currentId }
        val refreshedThread = when {
            session != null -> thread.copy(
                title = session.name.ifBlank { ChatHomeThreadState.DEFAULT_TITLE },
                favorite = session.isStarred,
            )
            currentId.isBlank() -> thread.copy(
                title = ChatHomeThreadState.DEFAULT_TITLE,
                favorite = false,
            )
            else -> thread
        }
        return copy(thread = refreshedThread.copy(rows = buildThreadRows(currentId)))
            .withPipelineNameRefreshed()
    }

    /**
     * Returns the active session's pipeline binding (or `null` when the
     * session inherits the default). Surfaced to the screen so the
     * new-thread picker can pre-select the same pipeline as the current
     * chat, matching legacy `ChatViewModel.requestNewSession` ergonomics.
     */
    fun currentPipelineId(): String? =
        sessions.firstOrNull { it.id == _state.value.thread.currentSessionId }?.pipelineId

    /**
     * Extracts the plain-text body of a chat-home row by its catalog id
     * (`ChatHomeMessageRow.id`). Returns `null` for rows whose content
     * is not a plain-text bubble (Clarification cards, HITL
     * confirmations, tool-call tiles, inline errors) — those have their
     * own affordances and don't expose a copyable payload.
     *
     * Used by the long-press context menu (`onMessageContextAction`) to
     * resolve Copy / Rerun targets.
     */
    fun textForRow(rowId: String): String? {
        val row = _state.value.messages.firstOrNull { it.id == rowId } ?: return null
        return when (val content = row.content) {
            is ChatContent.Text -> content.text
            is ChatContent.Markdown -> content.source
            else -> null
        }
    }

    /**
     * Persists the text of the message identified by [rowId] into long-term
     * memory as a manual entry, then raises a [MemorySaveEvent] so the screen
     * can confirm via snackbar. Rows without a copyable text payload, and
     * blank texts, are silently ignored (no event).
     *
     * Backs the long-press "Save to memory" context action.
     */
    fun saveMessageToMemory(rowId: String) {
        val text = textForRow(rowId) ?: return
        viewModelScope.launch {
            when (saveMessageToMemoryUseCase(text)) {
                is SaveToMemoryOutcome.Saved -> _memorySaveEvents.tryEmit(MemorySaveEvent.Saved)
                is SaveToMemoryOutcome.Failed -> _memorySaveEvents.tryEmit(MemorySaveEvent.Failed)
                SaveToMemoryOutcome.Skipped -> Unit
            }
        }
    }

    /**
     * Resting (non-overlay) visual for this snapshot — `Interrupted` while
     * an interrupted-run snapshot is pending (the status card must survive
     * transient overlays like the drawer; dropping to Idle would strand the
     * Resume / Discard actions until the next thread switch), otherwise
     * `Empty` / `Idle` by message-list presence. Derived from the receiver
     * (never `_state.value`) so calls inside an `_state.update` lambda stay
     * consistent with the snapshot being transformed.
     */
    private fun ChatHomeScreenState.restingVisual(): ChatHomeUiState = when {
        pending.interrupted != null -> ChatHomeUiState.Interrupted
        messages.isEmpty() -> ChatHomeUiState.Empty
        else -> ChatHomeUiState.Idle
    }

    /**
     * Creates a new chat session bound to [pipelineId] and switches to it.
     * Mirrors legacy `ChatViewModel.createNewSession` so the new-thread
     * picker behaves identically across both surfaces.
     *
     * @param pipelineId Pipeline identifier to bind, or `null` to inherit
     *   the application-wide default pipeline.
     */
    fun createNewSessionWithPipeline(pipelineId: String?) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            chatRepository.saveSession(
                ChatSession(
                    id = newId,
                    name = DEFAULT_NEW_CHAT_NAME,
                    updatedAt = System.currentTimeMillis(),
                    pipelineId = pipelineId,
                ),
            )
            selectThread(newId)
        }
    }

    /**
     * Renames the chat session identified by [threadId]. Trims the input
     * and short-circuits when the trimmed value is blank — the rename
     * sheet's Save button is already gated on a non-blank value, but the
     * VM mirrors the gate defensively.
     */
    fun renameSession(threadId: String, newName: String) {
        val trimmed = newName.trim()
        if (threadId.isBlank() || trimmed.isEmpty()) return
        viewModelScope.launch {
            chatRepository.renameSession(threadId, trimmed)
        }
    }

    /**
     * Flips the session-level favorite flag on the currently active chat.
     * No-op when no session is loaded.
     */
    fun toggleFavoriteCurrent() {
        val sessionId = _state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        val current = sessions.firstOrNull { it.id == sessionId }?.isStarred ?: false
        viewModelScope.launch {
            chatRepository.setSessionFavorite(sessionId, !current)
        }
    }

    /**
     * Imports a chat from a JSON document. Surfaces a snackbar event on
     * failure via [importErrorEvents]; on success the newly created
     * session becomes the active thread.
     *
     * @param json Raw JSON payload (export shape or bare message array).
     */
    fun importChatFromJson(json: String) {
        viewModelScope.launch {
            try {
                val newId = chatRepository.importChat(json)
                selectThread(newId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _importErrorEvents.tryEmit(
                    e.localizedMessage ?: IMPORT_GENERIC_FAILURE_MESSAGE,
                )
            }
        }
    }

    /**
     * Activates the LiteRT model identified by [modelId] and reloads the
     * inference engine. Subsequent `sendMessage` calls run against the
     * freshly-loaded model.
     */
    fun pickModel(modelId: Long) {
        viewModelScope.launch {
            localModelRepository.setActiveModel(modelId)
            loadModelUseCase()
        }
    }

    /**
     * Serialises the currently active session and emits the resulting
     * [ChatExportPayload] via [exportEvents]. The screen consumes the
     * payload and dispatches a system share-sheet (`Intent.ACTION_SEND`)
     * — kept in the screen because Hilt-ViewModels stay free of `Context`.
     */
    fun exportCurrentSession() {
        val sessionId = _state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            try {
                val rawMessages = chatRepository.getMessagesForSession(sessionId).first()
                val session = chatRepository.getSessionById(sessionId)
                val sessionName = session?.name ?: EXPORT_FALLBACK_SESSION_NAME
                val messagesArray = JSONArray()
                rawMessages.forEach { message ->
                    messagesArray.put(
                        JSONObject()
                            .put("role", message.role.name)
                            .put("text", message.content)
                            .put("timestamp", message.timestamp),
                    )
                }
                val root = JSONObject()
                    .put("sessionId", sessionId)
                    .put("sessionName", sessionName)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("messages", messagesArray)
                val payload =
                    ChatExportPayload(sessionName = sessionName, json = root.toString(EXPORT_JSON_INDENT))
                _exportEvents.tryEmit(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Mirrors the previous silent-failure contract: an export
                // that cannot be serialised simply emits nothing.
                Timber.w(e, "Chat export failed")
            }
        }
    }

    /**
     * Deletes the currently active session and auto-selects the next
     * available thread; when no other session exists, creates a fresh
     * unbound chat so the user is never stranded on a non-existent
     * session id.
     */
    fun deleteCurrentSession() {
        val sessionId = _state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            val remaining = sessions.filter { it.id != sessionId }
            if (remaining.isNotEmpty()) {
                selectThread(remaining.first().id)
            } else {
                createNewSessionWithPipeline(pipelineId = null)
            }
        }
    }

    /**
     * Console replay baseline of one persisted run: the run id and its
     * replayed console events as a single value, so the merge guard can
     * never see one without the other.
     *
     * @property runId Id of the run whose trace was replayed.
     * @property events The run's replayed console events, ordered by seq.
     */
    private data class ReplayedBaseline(val runId: String, val events: List<ConsoleEvent>)

    /**
     * Result of projecting a persisted run trace into console rows, built
     * off the main dispatcher by [replayConsoleTrace] and installed on the
     * main dispatcher in one step.
     *
     * @property baseline The replay/live merge baseline.
     * @property nodeIoSnapshots Per-node I/O snapshots in trace order,
     *   ready to seed [ChatHomeViewModel.nodeIoSnapshots].
     * @property logs Pre-rendered Logs-tab rows (clear baseline applied).
     * @property vars Pre-rendered Vars-tab rows.
     * @property traces Pre-rendered Traces-tab spans.
     */
    private data class ReplayProjection(
        val baseline: ReplayedBaseline,
        val nodeIoSnapshots: List<Pair<String, AgentOrchestratorState.NodeIO>>,
        val logs: List<ConsoleLine>,
        val vars: List<ConsoleVarRow>,
        val traces: List<ConsoleTraceSpan>,
    )

    companion object {
        /** Default name of a freshly-created chat session — must match legacy `ChatViewModel` for compatibility. */
        const val DEFAULT_NEW_CHAT_NAME: String = "New Chat"

        /** Maximum characters of the first user message used as the auto-generated session name. */
        const val AUTO_RENAME_CHAR_LIMIT: Int = 20

        /** Suffix appended to a truncated auto-rename name. */
        const val AUTO_RENAME_SUFFIX: String = "..."

        /** Fallback message for `Throwable` instances surfaced via [ChatHomeUiState.Error] without a cause. */
        const val UNKNOWN_ERROR_FALLBACK: String = "Unknown error"

        /**
         * User-facing message surfaced when [sendMessage] is invoked
         * while no LLM model is loaded. The Retry CTA on the resulting
         * error tile triggers [retryAfterError], which attempts to load
         * the active model in-place rather than redirecting to Settings.
         * Copy reflects that: "Tap Retry to load the active model".
         */
        const val MODEL_NOT_LOADED_MESSAGE: String =
            "No model is loaded. Tap Retry to load the active model."

        /**
         * User-facing message surfaced when [retryAfterError] tries to
         * load the model but `LoadModelUseCase` can't find an active one
         * (no models installed, or the registered active model file is
         * missing). At that point only Settings → Models can fix the
         * problem, so the copy redirects there.
         */
        const val NO_ACTIVE_MODEL_MESSAGE: String =
            "No active model. Open Settings → Models to install or activate one."

        /** Rough chars-per-token divisor used for the v0.1 token counter (`text.length / 4`). */
        const val TOKEN_CHARS_PER_TOKEN: Int = 4

        /**
         * Magic word the user must type to confirm a destructive tool call.
         * Mirrors the catalog `HitlConfirmationState.DESTRUCTIVE_CONFIRM_WORD`
         * but duplicated here so the VM gate stays free of `:catalog`
         * imports beyond the [Risk] adapter.
         */
        const val DESTRUCTIVE_TYPED_CONFIRM_WORD: String = "yes"

        /**
         * Template of the SYSTEM chat row persisted when the user rejects a
         * pending tool call via [ChatHomeViewModel.rejectTool]. `%s` is
         * replaced with the tool name.
         */
        const val SYSTEM_MESSAGE_TOOL_DENIED: String = "Tool '%s' denied by user."

        /**
         * SYSTEM chat row persisted when the user's clarification reply was
         * not consumed by the pipeline (the request had already resolved —
         * typically the repository's timeout fired while a reattach-restored
         * card was still on screen).
         */
        const val SYSTEM_MESSAGE_CLARIFICATION_REPLY_NOT_DELIVERED: String =
            "Reply was not delivered — the clarification had already been resolved with a default answer."

        /**
         * Fallback node label rendered on the interrupted-run card when the
         * run stopped before reaching any node, the pipeline was deleted, or
         * the recorded node id no longer exists in the (since-edited) graph.
         */
        const val INTERRUPTED_UNKNOWN_NODE_LABEL: String = "unknown step"

        /** Milliseconds in one hour, for the resume-window pre-check on the interrupted card. */
        private const val MILLIS_PER_HOUR: Long = 3_600_000L

        /** Pre-formatted timestamp format used in [ChatMetadata.timestamp] (24h, locale-aware). */
        private const val TIMESTAMP_PATTERN: String = "HH:mm"

        /** Pattern used for the drawer thread subtitle (e.g. `Mon 14:32`). */
        private const val THREAD_SUBTITLE_PATTERN: String = "EEE HH:mm"

        /** Fallback session name forwarded as the share-sheet subject when the session has no name. */
        const val EXPORT_FALLBACK_SESSION_NAME: String = "Chat"

        /** JSON pretty-print indent used for export payloads. */
        const val EXPORT_JSON_INDENT: Int = 2

        /** Fallback localised-error string used when the import path throws without a message. */
        const val IMPORT_GENERIC_FAILURE_MESSAGE: String = "Could not import the chat."

        /** Formats a session `updatedAt` timestamp as the drawer thread subtitle. */
        fun formatThreadSubtitle(updatedAt: Long): String =
            SimpleDateFormat(THREAD_SUBTITLE_PATTERN, Locale.getDefault()).format(Date(updatedAt))

        /** Formats an epoch-millis instant with the in-chat message timestamp pattern (`HH:mm`). */
        fun formatMessageTimestamp(epochMs: Long): String =
            SimpleDateFormat(TIMESTAMP_PATTERN, Locale.getDefault()).format(Date(epochMs))

        /**
         * Projects a domain [ChatMessage] onto the design-system row
         * model. Kept on the companion so the mapping can be unit-tested
         * without spinning up the VM.
         */
        fun chatMessageToRow(message: ChatMessage, activeModelName: String): ChatHomeMessageRow {
            val role = when (message.role) {
                Role.USER -> ChatRole.User
                Role.AGENT -> ChatRole.Assistant
                Role.SYSTEM -> ChatRole.System
            }
            val formattedTimestamp = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.getDefault())
                .format(Date(message.timestamp))
            val metadata = ChatMetadata(
                timestamp = formattedTimestamp,
                model = if (role == ChatRole.Assistant) activeModelName else null,
                status = ChatMessageStatus.Sent,
            )
            val idPrefix = when (role) {
                ChatRole.User -> "u"
                ChatRole.Assistant -> "a"
                ChatRole.System -> "s"
                ChatRole.Tool -> "t"
            }
            val rowId = message.id?.let { "$idPrefix-$it" } ?: "$idPrefix-${UUID.randomUUID()}"
            // Agent + tool bubbles can carry markdown (headings / lists / code
            // fences from the LLM); surface as `ChatContent.Markdown` so the
            // host-supplied renderer formats them. User input never carries
            // intentional markdown — stick with plain text to avoid e.g. a
            // stray `#` turning into a heading.
            val content = when (role) {
                ChatRole.Assistant, ChatRole.Tool -> ChatContent.Markdown(message.content)
                else -> ChatContent.Text(message.content)
            }
            return ChatHomeMessageRow(
                id = rowId,
                role = role,
                content = content,
                metadata = metadata,
            )
        }
    }
}

/**
 * Minimal pipeline summary used by [ChatHomeViewModel] to resolve the
 * TopAppBar subtitle and the deleted-pipeline fallback.
 *
 * @property id stable identifier of the pipeline.
 * @property name display name of the pipeline.
 */
data class PipelineSummary(val id: String, val name: String)

/**
 * Snapshot of the tool the orchestrator is currently paused on, exposed
 * through [ChatHomePendingState.tool] so the mapper can render the
 * trailing HITL confirmation card from real data instead of fixtures.
 *
 * @property toolName fully-qualified tool id (e.g. `fs.write_file`).
 * @property arguments raw JSON-encoded argument blob emitted by the agent.
 * @property risk per-tool risk tier resolved by `ToolRepository.getRisk`.
 */
data class HitlPending(val toolName: String, val arguments: String, val risk: ToolRisk)

/**
 * Snapshot of the session's interrupted run, exposed through
 * [ChatHomePendingState.interrupted] so the mapping can render the trailing
 * interrupted-run status card (Resume / Discard) from real data.
 *
 * @property runId id of the interrupted persistent run record — the Discard
 *   intent settles exactly this record, never "whatever is interrupted now".
 * @property nodeLabel resolved display label of the node the run stopped at
 *   (falls back to [ChatHomeViewModel.INTERRUPTED_UNKNOWN_NODE_LABEL]).
 * @property timestamp pre-formatted time the run was actually interrupted
 *   (`finishedAt` of the record). Captured once here so the card shows a
 *   stable, truthful time instead of re-deriving "now" on every
 *   recomposition.
 * @property resumable whether the card offers the Resume CTA: `false` when
 *   the interruption is older than the resume window or the record predates
 *   prompt persistence — only Discard remains. The use case re-validates on
 *   tap regardless; this flag just keeps the offered action honest.
 */
data class InterruptedRunPending(
    val runId: String,
    val nodeLabel: String,
    val timestamp: String,
    val resumable: Boolean = true,
)

/**
 * One-shot failure outcomes of a Resume tap on the interrupted-run card,
 * mapped to snackbar copy by the screen. Modelled as an enum so resource ids
 * stay out of the ViewModel. A successful resume emits no event — the
 * surface flips to `Generating` instead.
 */
enum class ResumeFeedbackEvent {
    /** The pipeline graph was edited or deleted; only a full restart can help. */
    GraphChanged,

    /** The interruption is older than the configured resume window. */
    Expired,

    /** The run is no longer resumable (raced discard/resume, legacy record). */
    NotResumable,
}

/**
 * Maps the domain [ToolRisk] enum onto the catalog [Risk] enum. The two
 * exist in different layers (domain stays free of catalog imports) so a
 * thin adapter at the presentation boundary is the cleanest cut.
 */
internal fun ToolRisk.toCatalogRisk(): Risk = when (this) {
    ToolRisk.READ_ONLY -> Risk.Readonly
    ToolRisk.SENSITIVE -> Risk.Sensitive
    ToolRisk.DESTRUCTIVE -> Risk.Destructive
}

/**
 * Discrete one-shot events raised by the console pane and consumed by the
 * screen-level snackbar host. Modelled as an enum so the screen can map
 * each value to the right localised string in one place (no resource id
 * leaks into the VM).
 */
enum class ConsoleSnackbarEvent {
    /** A single console line was copied to the system clipboard. */
    LineCopied,

    /** The full filtered log was copied to the system clipboard. */
    AllCopied,
}

/**
 * One-shot outcome of the long-press "Save to memory" action, mapped to a
 * snackbar by the screen. Modelled as an enum so resource ids stay out of
 * the ViewModel.
 */
enum class MemorySaveEvent {
    /** The message text was embedded and stored as a manual memory entry. */
    Saved,

    /** Embedding or persistence failed; surface a retry-able failure copy. */
    Failed,
}
