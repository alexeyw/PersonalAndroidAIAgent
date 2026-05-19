package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.LocalModel
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Hilt [ViewModel] backing the redesigned Knotwork chat home
 * (`compose/screens/README.md §C1`). Phase 22 / Task 1/17 replaces the
 * Phase 21 stub VM with real wiring to:
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
 * Scope of Task 1/17 (the rest is split across follow-up tasks of the
 * same phase): user-prompted generation cycle (`Idle → Generating →
 * Idle / Error`), pipeline binding + deleted-pipeline fallback, session
 * initialisation + thread switching, token counter.
 *
 * Out of scope here (handled later):
 *  - Task 2/17 — HITL (`WaitingForApproval`) and Clarification
 *    (`AwaitingClarification`) wiring.
 *  - Task 3/17 — Console pane (`ConsoleLog`) streaming.
 *  - Task 4/17 — drawer / composer / overflow secondary actions
 *    (new-thread, rename, favorite, import, model picker, settings).
 *
 * Until those tasks land, intermediate orchestrator states stay folded
 * into [ChatHomeUiState.Generating] so the user still gets visual
 * feedback while a request is in flight.
 */
@HiltViewModel
@Suppress(
    // Reason: Chat home is the single entry-point for every user-visible
    // agent interaction (messaging, session switching, pipeline binding,
    // token counter, deleted-pipeline fallback, debug picker). Splitting
    // would require an external store; tracked for a future refactor.
    "TooManyFunctions",
    "LargeClass",
    "LongParameterList",
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
) : ViewModel() {

    private val _currentSessionId: MutableStateFlow<String> = MutableStateFlow("")
    private val _threadTitle: MutableStateFlow<String> = MutableStateFlow(DEFAULT_THREAD_TITLE)
    private val _modelName: MutableStateFlow<String> = MutableStateFlow(DEFAULT_MODEL_NAME)
    private val _composerValue: MutableStateFlow<String> = MutableStateFlow("")
    private val _pendingTypedConfirm: MutableStateFlow<String> = MutableStateFlow("")
    private val _messages: MutableStateFlow<List<ChatHomeMessageRow>> = MutableStateFlow(emptyList())
    private val _state: MutableStateFlow<ChatHomeUiState> = MutableStateFlow(ChatHomeUiState.Empty)
    private val _consoleSearchQuery: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _consoleFilter: MutableStateFlow<ConsoleFilter> = MutableStateFlow(ConsoleFilter.allOn)
    private val _pipelineName: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _tokensUsed: MutableStateFlow<Int> = MutableStateFlow(0)
    private val _tokensMax: MutableStateFlow<Int> = MutableStateFlow(0)
    private val _pipelineFallbackEvents: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
    private val _pendingTool: MutableStateFlow<HitlPending?> = MutableStateFlow(null)
    private val _pendingClarification: MutableStateFlow<ClarificationRequest?> = MutableStateFlow(null)
    private val _consoleLines: MutableStateFlow<List<ConsoleLine>> = MutableStateFlow(emptyList())
    private val _consoleVars: MutableStateFlow<List<ConsoleVarRow>> = MutableStateFlow(emptyList())
    private val _consoleTraces: MutableStateFlow<List<ConsoleTraceSpan>> = MutableStateFlow(emptyList())
    private val _consoleTab: MutableStateFlow<ConsoleTab> = MutableStateFlow(ConsoleTab.Logs)
    private val _consoleSnap: MutableStateFlow<ConsoleSnap?> = MutableStateFlow(null)
    private val _consoleClearConfirmRequested: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _consoleSnackbarEvents: MutableSharedFlow<ConsoleSnackbarEvent> =
        MutableSharedFlow(extraBufferCapacity = 1)
    private val _favorite: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _threadRows: MutableStateFlow<List<ChatHomeThreadRow>> = MutableStateFlow(emptyList())
    private val _installedModels: MutableStateFlow<List<LocalModel>> = MutableStateFlow(emptyList())
    private val _activeModelId: MutableStateFlow<Long?> = MutableStateFlow(null)
    private val _availablePipelines: MutableStateFlow<List<PipelineSummary>> = MutableStateFlow(emptyList())
    private val _exportEvents: MutableSharedFlow<ChatExportPayload> = MutableSharedFlow(extraBufferCapacity = 1)
    private val _importErrorEvents: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)

    /** Externally-observable current session id. */
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    /** Externally-observable thread title used by the screen-level composable. */
    val threadTitle: StateFlow<String> = _threadTitle.asStateFlow()

    /** Externally-observable model display name. */
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    /** Externally-observable composer input value. */
    val composerValue: StateFlow<String> = _composerValue.asStateFlow()

    /** Externally-observable typed-confirm input. */
    val pendingTypedConfirm: StateFlow<String> = _pendingTypedConfirm.asStateFlow()

    /** Externally-observable chat message rows projected from [ChatRepository]. */
    val messages: StateFlow<List<ChatHomeMessageRow>> = _messages.asStateFlow()

    /** Externally-observable sealed UI state — single source of truth for the chat-home surface. */
    val state: StateFlow<ChatHomeUiState> = _state.asStateFlow()

    /** Externally-observable console search query (null = hidden, "" = visible but unfiltered). */
    val consoleSearchQuery: StateFlow<String?> = _consoleSearchQuery.asStateFlow()

    /** Externally-observable console source-set filter. */
    val consoleFilter: StateFlow<ConsoleFilter> = _consoleFilter.asStateFlow()

    /** Display name of the pipeline bound to the active chat — rendered as TopAppBar subtitle. */
    val pipelineName: StateFlow<String?> = _pipelineName.asStateFlow()

    /** Rough token usage of the active session (text-length / 4). */
    val tokensUsed: StateFlow<Int> = _tokensUsed.asStateFlow()

    /** Configured context-window cap propagated from [SettingsRepository.maxContextLength]. */
    val tokensMax: StateFlow<Int> = _tokensMax.asStateFlow()

    /**
     * One-shot signal raised when the pipeline bound to the active chat
     * disappears from the library and the binding silently falls back to
     * the default pipeline. The screen surfaces a `KnotworkSnackbar` so
     * the user is told their selection moved.
     */
    val pipelineFallbackEvents: SharedFlow<Unit> = _pipelineFallbackEvents.asSharedFlow()

    /**
     * Snapshot of the tool invocation that the orchestrator has paused on,
     * surfaced so the trailing HITL confirmation card can render its real
     * tool name / arguments / risk. `null` whenever no approval gate is
     * active. Mirrors [pendingClarification] which plays the same role for
     * `AwaitingClarification`.
     */
    val pendingTool: StateFlow<HitlPending?> = _pendingTool.asStateFlow()

    /**
     * Snapshot of the clarification request the orchestrator has paused on,
     * surfaced so the trailing clarification card can render the real
     * question + canned answer options. `null` whenever the agent is not
     * waiting on a clarification reply.
     */
    val pendingClarification: StateFlow<ClarificationRequest?> = _pendingClarification.asStateFlow()

    /**
     * Live snapshot of the console pane's Logs tab. Aggregated from every
     * [AgentOrchestratorState.ConsoleLog] emission of the active run, with
     * cleared rows trimmed off via [consoleClearBaseline] so a mid-run
     * `Clear` survives the next cumulative engine snapshot.
     */
    val consoleLines: StateFlow<List<ConsoleLine>> = _consoleLines.asStateFlow()

    /**
     * Live snapshot of the console pane's Vars tab. Derived from every
     * [AgentOrchestratorState.NodeIO] emission; two rows per node (`input`
     * and `output`), grouped by node label inside the catalog body.
     */
    val consoleVars: StateFlow<List<ConsoleVarRow>> = _consoleVars.asStateFlow()

    /**
     * Live snapshot of the console pane's Traces tab. Built from every
     * [AgentOrchestratorState.PipelineTrace] emission; the catalog spans
     * carry pre-formatted start timestamps observed when the trace step
     * landed (not the node's own wall-clock start, which the engine does
     * not surface today).
     */
    val consoleTraces: StateFlow<List<ConsoleTraceSpan>> = _consoleTraces.asStateFlow()

    /**
     * User-selected console tab persisted via
     * [SettingsRepository.consolePreferredConsoleTabName]. Hydrated on init
     * and updated by [onConsoleTabChange].
     */
    val consoleTab: StateFlow<ConsoleTab> = _consoleTab.asStateFlow()

    /**
     * Active console-pane snap (`null` = closed). Independent of [state] so
     * the pane can stay open across `Generating → HitlConfirm →
     * Clarification → Idle / Completed / Error` transitions. The catalog
     * renders the overlay whenever this is non-null, keeping the underlying
     * chat surface visible behind the pane.
     */
    val consoleSnap: StateFlow<ConsoleSnap?> = _consoleSnap.asStateFlow()

    /**
     * Whether the destructive "Clear console for this session?" confirmation
     * dialog is requested. Toggled by [requestConsoleClear] /
     * [dismissConsoleClear] / [confirmConsoleClear].
     */
    val consoleClearConfirmRequested: StateFlow<Boolean> = _consoleClearConfirmRequested.asStateFlow()

    /**
     * One-shot stream of snackbar events raised by the console pane (line
     * copied, full log copied). The screen mirrors each emission into the
     * shared `SnackbarHostState`; the clipboard write itself is performed
     * by the Composable (which owns `LocalClipboardManager`).
     */
    val consoleSnackbarEvents: SharedFlow<ConsoleSnackbarEvent> = _consoleSnackbarEvents.asSharedFlow()

    /** Whether the currently active session is favorited. Drives the TopAppBar star icon. */
    val favorite: StateFlow<Boolean> = _favorite.asStateFlow()

    /**
     * Drawer thread list projected from the live [ChatSession] cache. Favorited
     * sessions sort to the top; the rest follow the repository's `updatedAt
     * DESC` order. The catalog `ChatHomeViewState.threads` slot is wired to
     * this flow so the screen no longer needs to call into fixtures for the
     * drawer body.
     */
    val threadRows: StateFlow<List<ChatHomeThreadRow>> = _threadRows.asStateFlow()

    /** Locally installed LiteRT models — feeds the chat-home model-picker sheet. */
    val installedModels: StateFlow<List<LocalModel>> = _installedModels.asStateFlow()

    /** Row id of the currently active local model (`null` when none is active). */
    val activeModelId: StateFlow<Long?> = _activeModelId.asStateFlow()

    /** Available pipelines — surfaced by the new-chat pipeline picker. */
    internal val availablePipelinesFlow: StateFlow<List<PipelineSummary>> = _availablePipelines.asStateFlow()

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

    private var messagesJob: Job? = null
    private var generationJob: Job? = null
    private var tokenCounterJob: Job? = null
    private var clarificationTimeoutJob: Job? = null
    private var sessions: List<ChatSession> = emptyList()
    private var availablePipelinesObserved: Boolean = false
    private var availablePipelines: List<PipelineSummary> = emptyList()
    private var defaultPipelineId: String? = null

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

    init {
        observeAvailablePipelines()
        observeDefaultPipelineId()
        observeSessions()
        observeMaxContextSize()
        observeConsolePreferredTab()
        observeInstalledModels()
        initializeSession()
    }

    /** Updates the composer input value. Hoisted to the VM so screen recompositions never own the text. */
    fun onComposerValueChange(value: String) {
        _composerValue.value = value
    }

    /** Updates the typed-confirm input shown next to the Destructive HITL confirmation row. */
    fun onTypedConfirmChange(value: String) {
        _pendingTypedConfirm.value = value
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
        val draft = _composerValue.value.trim()
        if (draft.isEmpty()) return
        if (_state.value is ChatHomeUiState.Generating) return
        if (!llmInferenceEngine.isInitialized) {
            _state.value = ChatHomeUiState.Error(LOAD_MODEL_FIRST_MESSAGE)
            return
        }
        _composerValue.value = ""

        val sessionId = _currentSessionId.value
        if (sessionId.isBlank()) return
        val pipelineId = sessions.firstOrNull { it.id == sessionId }?.pipelineId

        _state.value = ChatHomeUiState.Generating
        clearPendingApprovalAndClarification()
        // Fresh run = fresh cumulative log upstream; the baseline carried
        // over from a previous run's mid-stream Clear no longer applies
        // (the engine restarts events from scratch). Mirrors legacy
        // `ChatViewModel.sendMessage`.
        resetConsoleStateForNewRun()

        autoRenameIfDefault(sessionId, draft)

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            agentOrchestratorUseCase(sessionId, draft, pipelineId)
                .catch { error ->
                    _state.value = ChatHomeUiState.Error(error.message ?: UNKNOWN_ERROR_FALLBACK)
                }
                .collect { orchestratorState -> handleOrchestratorState(orchestratorState) }
        }
    }

    /**
     * Cancels the active generation job. Mirrors legacy behaviour — does
     * not call into the orchestrator (no cancel-task API on
     * [ai.agent.android.domain.engine.TaskQueueManager] today); the
     * coroutine cancellation propagates through the engine via the
     * collector teardown.
     */
    fun stopGeneration() {
        generationJob?.cancel()
        clearPendingApprovalAndClarification()
        _state.update { current ->
            if (current is ChatHomeUiState.Generating) {
                if (_messages.value.isEmpty()) ChatHomeUiState.Empty else ChatHomeUiState.Idle
            } else {
                current
            }
        }
    }

    /** Opens the drawer overlay. */
    fun openDrawer() {
        _state.value = ChatHomeUiState.DrawerOpen
    }

    /** Closes the drawer overlay, settling on the right state for the current message list. */
    fun closeDrawer() {
        if (_state.value is ChatHomeUiState.DrawerOpen) {
            _state.value = restingState()
        }
    }

    /**
     * Switches the active chat session. Persists the selection, cancels
     * any in-flight generation, and re-subscribes the message stream to
     * the newly-selected session.
     *
     * The previous thread's rows are cleared *synchronously* before the
     * persistence coroutine launches — otherwise [restingState] would
     * read stale data from [_messages] and resolve to `Idle` while the
     * UI is conceptually empty, producing a brief flicker of the
     * outgoing thread's content.
     */
    fun selectThread(threadId: String) {
        if (threadId.isBlank() || threadId == _currentSessionId.value) return
        generationJob?.cancel()
        messagesJob?.cancel()
        clearPendingApprovalAndClarification()
        resetConsoleStateForNewRun()
        _consoleSnap.value = null
        _messages.value = emptyList()
        _tokensUsed.value = 0
        _state.value = ChatHomeUiState.Empty
        _currentSessionId.value = threadId
        applyCurrentSessionMetadata()
        observeMessages(threadId)
        viewModelScope.launch {
            settingsRepository.setCurrentChatSessionId(threadId)
        }
    }

    /**
     * Opens the console pane at the given [snap] (default: Partial). The
     * pane is an independent overlay — the underlying [state] is left
     * untouched, so the user can drill into pipeline activity during
     * Generating / HitlConfirm / Clarification without losing their place.
     */
    fun openConsole(snap: ConsoleSnap = ConsoleSnap.Partial) {
        _consoleSnap.value = snap
    }

    /**
     * Updates the snap point of the currently-open console pane. No-op
     * when the pane is closed.
     */
    fun setConsoleSnap(snap: ConsoleSnap) {
        if (_consoleSnap.value != null) {
            _consoleSnap.value = snap
        }
    }

    /** Dismisses the console pane without touching the underlying chat state. */
    fun closeConsole() {
        _consoleSnap.value = null
    }

    /** Toggles the console inline-search field. Cycles `null → "" → null`. */
    fun toggleConsoleSearch() {
        _consoleSearchQuery.update { current -> if (current == null) "" else null }
    }

    /** Updates the console inline-search query while the field is visible. */
    fun onConsoleSearchQueryChange(query: String) {
        _consoleSearchQuery.value = query
    }

    /** Replaces the active console source-set filter. */
    fun onConsoleFilterChange(filter: ConsoleFilter) {
        _consoleFilter.value = filter
    }

    /** Reacts to the long-press "Only show this source" menu item. */
    fun filterConsoleByLineSource(source: ConsoleSource) {
        _consoleFilter.value = ConsoleFilter(sources = setOf(source))
    }

    /**
     * Persists the user's currently-selected console tab. Mirrors the
     * change into the local [consoleTab] flow synchronously so the catalog
     * tab strip re-renders without waiting for the DataStore round-trip.
     */
    fun onConsoleTabChange(tab: ConsoleTab) {
        if (_consoleTab.value == tab) return
        _consoleTab.value = tab
        viewModelScope.launch {
            settingsRepository.setConsolePreferredConsoleTabName(tab.name)
        }
    }

    /**
     * Requests the destructive "Clear console for this session?" dialog —
     * the screen renders an [AlertDialog] driven by
     * [consoleClearConfirmRequested]. The actual clear runs on
     * [confirmConsoleClear] so the user has a chance to back out.
     */
    fun requestConsoleClear() {
        if (_consoleLines.value.isEmpty()) return
        _consoleClearConfirmRequested.value = true
    }

    /** Dismisses the confirmation dialog without altering the log. */
    fun dismissConsoleClear() {
        _consoleClearConfirmRequested.value = false
    }

    /**
     * Advances [consoleClearBaseline] by the count of currently-visible
     * lines and clears [consoleLines]. The next cumulative engine snapshot
     * trims its leading slice, so cleared rows do not pop back in.
     */
    fun confirmConsoleClear() {
        val visible = _consoleLines.value
        _consoleClearConfirmRequested.value = false
        if (visible.isEmpty()) return
        consoleClearBaseline += visible.size
        _consoleLines.value = emptyList()
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
     * the surface into an arbitrary state. The picker UI is gated on
     * `BuildConfig.DEBUG`, but the VM accepts the call in any build so
     * unit tests can drive the state machine deterministically.
     */
    fun forceState(state: ChatHomeUiState) {
        _state.value = state
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
            _currentSessionId.value = sessionId
            applyCurrentSessionMetadata()
            observeMessages(sessionId)
        }
    }

    /**
     * Continuously observes the pipeline library. Used for three things:
     *  1. Caching the available pipelines so [sendMessage] can resolve
     *     the bound pipeline name.
     *  2. Recomputing the TopAppBar subtitle ([pipelineName]).
     *  3. Detecting deletion of the pipeline bound to the active chat
     *     and silently rebinding it to the default pipeline, surfacing
     *     a one-shot Snackbar via [pipelineFallbackEvents].
     */
    private fun observeAvailablePipelines() {
        viewModelScope.launch {
            pipelineRepository.getAllPipelines().collect { graphs ->
                val summaries = graphs.map { PipelineSummary(id = it.id, name = it.name) }
                availablePipelines = summaries
                _availablePipelines.value = summaries
                availablePipelinesObserved = true
                refreshPipelineName()
                handleDeletedBoundPipeline(summaries)
            }
        }
    }

    /**
     * Observes the installed-model list and mirrors it into [installedModels]
     * + [activeModelId]. Also keeps [_modelName] in sync with the currently
     * active model so the TopAppBar subtitle always reflects the real
     * inference engine bound to the chat.
     */
    private fun observeInstalledModels() {
        viewModelScope.launch {
            localModelRepository.getAllModels().collect { models ->
                _installedModels.value = models
                val active = models.firstOrNull { it.isActive }
                _activeModelId.value = active?.id
                _modelName.value = active?.name ?: DEFAULT_MODEL_NAME
            }
        }
    }

    /** Observes the user-set default pipeline id and refreshes the subtitle when it changes. */
    private fun observeDefaultPipelineId() {
        viewModelScope.launch {
            settingsRepository.defaultPipelineId.collect { id ->
                defaultPipelineId = id
                refreshPipelineName()
            }
        }
    }

    /** Observes the session list. Keeps a cache for pipeline-id lookups and refreshes the title + subtitle. */
    private fun observeSessions() {
        viewModelScope.launch {
            chatRepository.getSessionsFlow().collect { current ->
                sessions = current
                applyCurrentSessionMetadata()
                refreshThreadRows()
                handleDeletedBoundPipeline(availablePipelines)
            }
        }
    }

    /**
     * Rebuilds [_threadRows] from the live [sessions] cache. Favorited
     * sessions sort to the top of the drawer; the rest follow the
     * repository's `updatedAt DESC` ordering. The catalog drawer renders
     * the `selected`/`active` chrome from the matching flags here.
     */
    private fun refreshThreadRows() {
        val activeId = _currentSessionId.value
        val sorted = sessions.sortedWith(
            compareByDescending<ChatSession> { it.isStarred }
                .thenByDescending { it.updatedAt },
        )
        _threadRows.value = sorted.map { session ->
            ChatHomeThreadRow(
                id = session.id,
                title = session.name.ifBlank { DEFAULT_THREAD_TITLE },
                subtitle = formatThreadSubtitle(session.updatedAt),
                selected = session.id == activeId,
                active = session.id == activeId,
                starred = session.isStarred,
            )
        }
    }

    /** Observes the configured context-window cap and mirrors it into [tokensMax]. */
    private fun observeMaxContextSize() {
        viewModelScope.launch {
            settingsRepository.maxContextLength.collect { value ->
                _tokensMax.value = value
            }
        }
    }

    /**
     * Hydrates [consoleTab] from the persisted DataStore preference. An
     * unrecognised value (e.g. an enum entry removed in a future version)
     * falls back to [ConsoleTab.Logs] so the surface never renders an
     * undefined tab.
     */
    private fun observeConsolePreferredTab() {
        viewModelScope.launch {
            settingsRepository.consolePreferredConsoleTabName.collect { name ->
                _consoleTab.value = ConsoleTab.entries.firstOrNull { it.name == name } ?: ConsoleTab.Logs
            }
        }
    }

    /**
     * Subscribes the message stream to [sessionId], cancelling any prior
     * subscription. Each emission is projected through
     * [chatMessageToRow] and folded into [_messages]; the rough token
     * counter is recomputed *concurrently* via [GetContextWindowUseCase]
     * so that the suspending tokenisation never stalls the collector
     * (otherwise [rebalanceRestingState] would only fire after the use
     * case resumes, gating the UI behind a background computation that
     * could take dozens of milliseconds on a cold session).
     */
    private fun observeMessages(sessionId: String) {
        messagesJob?.cancel()
        tokenCounterJob?.cancel()
        if (sessionId.isBlank()) {
            _messages.value = emptyList()
            _tokensUsed.value = 0
            return
        }
        messagesJob = viewModelScope.launch {
            chatRepository.getDisplayMessagesForSession(sessionId).collect { incoming ->
                _messages.value = incoming.map { chatMessageToRow(it, _modelName.value) }
                rebalanceRestingState()
                tokenCounterJob?.cancel()
                tokenCounterJob = launch {
                    val approx = runCatching {
                        getContextWindowUseCase(sessionId).length / TOKEN_CHARS_PER_TOKEN
                    }.getOrDefault(0)
                    _tokensUsed.value = approx
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
                current is ChatHomeUiState.Empty && _messages.value.isNotEmpty() -> ChatHomeUiState.Idle
                current is ChatHomeUiState.Idle && _messages.value.isEmpty() -> ChatHomeUiState.Empty
                else -> current
            }
        }
    }

    /** Branches on the orchestrator emission. Terminal states settle UI; intermediate states keep `Generating`. */
    private fun handleOrchestratorState(state: AgentOrchestratorState) {
        when (state) {
            is AgentOrchestratorState.WaitingForApproval -> handleWaitingForApproval(state)
            is AgentOrchestratorState.AwaitingClarification -> handleAwaitingClarification(state.request)
            is AgentOrchestratorState.ConsoleLog -> handleConsoleLog(state.events)
            is AgentOrchestratorState.PipelineTrace -> handlePipelineTrace(state.steps)
            is AgentOrchestratorState.NodeIO -> handleNodeIo(state)
            is AgentOrchestratorState.Completed -> {
                clearPendingApprovalAndClarification()
                _state.value = if (_messages.value.isEmpty()) ChatHomeUiState.Empty else ChatHomeUiState.Idle
            }
            is AgentOrchestratorState.Error -> {
                clearPendingApprovalAndClarification()
                _state.value = ChatHomeUiState.Error(state.message)
            }
            // Intermediate states keep `Generating` while the request is in flight.
            else -> Unit
        }
    }

    /**
     * Mirrors a cumulative [AgentOrchestratorState.ConsoleLog.events]
     * snapshot into [consoleLines]. Applies the baseline so events the
     * user just dismissed via [confirmConsoleClear] stay hidden until the
     * next session switch / send.
     */
    private fun handleConsoleLog(events: List<ConsoleEvent>) {
        val trimmed = applyConsoleClearBaseline(events)
        _consoleLines.value = trimmed.map(ConsoleEvent::toConsoleLine)
    }

    /**
     * Mirrors the latest [AgentOrchestratorState.PipelineTrace.steps]
     * snapshot into [consoleTraces]. The catalog span requires a
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
        _consoleTraces.value = steps.mapIndexed { index, step ->
            traceStepToConsoleSpan(step, traceStepStartMs[index])
        }
    }

    /**
     * Captures a per-node I/O snapshot and re-projects [consoleVars] from
     * the accumulated map so repeated emissions for the same node id
     * overwrite (not duplicate) the previous Vars rows.
     */
    private fun handleNodeIo(io: AgentOrchestratorState.NodeIO) {
        nodeIoSnapshots[io.nodeId] = io
        _consoleVars.value = nodeIoSnapshots.values.flatMap(::nodeIoToVarRows)
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

    /** Drops every cached console-side projection. Called at the start of each new run. */
    private fun resetConsoleStateForNewRun() {
        consoleClearBaseline = 0
        nodeIoSnapshots.clear()
        traceStepStartMs.clear()
        _consoleLines.value = emptyList()
        _consoleVars.value = emptyList()
        _consoleTraces.value = emptyList()
    }

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
        val pending = _pendingTool.value ?: return
        if (pending.risk == ToolRisk.DESTRUCTIVE && !isTypedConfirmValid()) return
        val sessionId = _currentSessionId.value
        if (sessionId.isBlank()) return
        agentOrchestratorUseCase.resumeWithApproval(sessionId, true)
        _pendingTool.value = null
        _pendingTypedConfirm.value = ""
        _state.value = ChatHomeUiState.Generating
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
        val pending = _pendingTool.value ?: return
        val sessionId = _currentSessionId.value
        if (sessionId.isBlank()) return
        agentOrchestratorUseCase.resumeWithApproval(sessionId, false)
        _pendingTool.value = null
        _pendingTypedConfirm.value = ""
        // Resuming the pipeline restarts orchestrator emission — keep the
        // surface in `Generating` until the next state (or a terminal
        // Completed / Error) settles it, otherwise the chat appears idle
        // while the agent is actively producing the denial follow-up.
        _state.value = ChatHomeUiState.Generating
        viewModelScope.launch {
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
     * @param answer the user's reply text (option label or free-form).
     */
    fun submitClarificationReply(answer: String) {
        val pending = _pendingClarification.value ?: return
        // Allow an empty reply through — the orchestrator already accepts
        // `""` as the timeout fallback for free-form requests, so an
        // intentional blank submit is a legitimate "skip" affordance.
        val trimmed = answer.trim()
        clarificationTimeoutJob?.cancel()
        _pendingClarification.value = null
        _state.value = ChatHomeUiState.Generating
        viewModelScope.launch {
            clarificationRepository.submitClarification(pending.id, trimmed)
        }
    }

    /** Captures the orchestrator's pending approval and flips the UI to the HITL state. */
    private fun handleWaitingForApproval(state: AgentOrchestratorState.WaitingForApproval) {
        _pendingTool.value = HitlPending(
            toolName = state.toolName,
            arguments = state.arguments,
            risk = state.risk,
        )
        _pendingTypedConfirm.value = ""
        _state.value = ChatHomeUiState.HitlConfirm(state.risk.toCatalogRisk())
    }

    /**
     * Captures the orchestrator's pending clarification, flips the UI to
     * the Clarification state, and arms the watchdog timer. The repository
     * owns the authoritative timeout via `withTimeout`; this watchdog is a
     * UI safety-net that ensures the surface flips back to a resting state
     * even if the user backgrounds the app for longer than [timeoutMs].
     */
    private fun handleAwaitingClarification(request: ClarificationRequest) {
        _pendingClarification.value = request
        _state.value = ChatHomeUiState.Clarification
        clarificationTimeoutJob?.cancel()
        if (request.timeoutMs > 0) {
            clarificationTimeoutJob = viewModelScope.launch {
                delay(request.timeoutMs)
                onClarificationTimeout(request)
            }
        }
    }

    /**
     * Fires when the watchdog elapses without a user reply. Submits the
     * default answer (first option, or empty for free-form) so the
     * suspended pipeline coroutine resumes, drops the pending snapshot,
     * appends a SYSTEM chat row recording the fallback, and keeps the
     * surface in `Generating` while the agent produces the follow-up
     * (terminal `Completed` / `Error` settles the resting state).
     */
    private fun onClarificationTimeout(request: ClarificationRequest) {
        if (_pendingClarification.value?.id != request.id) return
        val defaultAnswer = request.options?.firstOrNull().orEmpty()
        val sessionId = _currentSessionId.value
        _pendingClarification.value = null
        _state.value = ChatHomeUiState.Generating
        viewModelScope.launch {
            clarificationRepository.submitClarification(request.id, defaultAnswer)
            if (sessionId.isNotBlank()) {
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = Role.SYSTEM,
                        content = SYSTEM_MESSAGE_CLARIFICATION_TIMED_OUT.format(
                            defaultAnswer.ifBlank { CLARIFICATION_DEFAULT_BLANK },
                        ),
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /** Whether the current typed-confirm input satisfies the destructive HITL gate. */
    private fun isTypedConfirmValid(): Boolean =
        _pendingTypedConfirm.value.trim().equals(DESTRUCTIVE_TYPED_CONFIRM_WORD, ignoreCase = true)

    /** Clears every pending HITL / clarification snapshot and cancels the watchdog. */
    private fun clearPendingApprovalAndClarification() {
        clarificationTimeoutJob?.cancel()
        clarificationTimeoutJob = null
        _pendingTool.value = null
        _pendingClarification.value = null
        _pendingTypedConfirm.value = ""
    }

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
     * Rebinds the active chat to the default pipeline when the bound
     * pipeline disappears from the library. No-op while the pipeline
     * flow has not produced its initial snapshot — without this guard a
     * startup race would misread the empty initial `availablePipelines`
     * as "the bound pipeline no longer exists" and silently rebind every
     * chat to the default.
     */
    private suspend fun handleDeletedBoundPipeline(summaries: List<PipelineSummary>) {
        if (!availablePipelinesObserved) return
        val session = sessions.firstOrNull { it.id == _currentSessionId.value } ?: return
        val boundId = session.pipelineId ?: return
        if (summaries.any { it.id == boundId }) return
        chatRepository.saveSession(session.copy(pipelineId = null))
        _pipelineFallbackEvents.tryEmit(Unit)
    }

    /** Refreshes [pipelineName] from the latest cache of sessions + pipelines + default-pipeline id. */
    private fun refreshPipelineName() {
        _pipelineName.value = resolvePipelineName(
            sessions = sessions,
            currentSessionId = _currentSessionId.value,
            summaries = availablePipelines,
            defaultPipelineId = defaultPipelineId,
        )
    }

    /**
     * Resolves the pipeline display name for the active chat — explicit
     * binding when set, otherwise the user-marked default (or the first
     * pipeline in the library as a final fallback). Returns `null` only
     * when the pipeline library is still empty.
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
        val defaultMatch = defaultPipelineId?.let { id -> summaries.firstOrNull { it.id == id } }
        return (defaultMatch ?: summaries.first()).name
    }

    /** Refreshes thread title + pipeline subtitle from the latest session/pipeline caches. */
    private fun applyCurrentSessionMetadata() {
        val session = sessions.firstOrNull { it.id == _currentSessionId.value }
        if (session != null) {
            _threadTitle.value = session.name.ifBlank { DEFAULT_THREAD_TITLE }
            _favorite.value = session.isStarred
        } else if (_currentSessionId.value.isBlank()) {
            _threadTitle.value = DEFAULT_THREAD_TITLE
            _favorite.value = false
        }
        refreshPipelineName()
        refreshThreadRows()
    }

    /**
     * Returns the active session's pipeline binding (or `null` when the
     * session inherits the default). Surfaced to the screen so the
     * new-thread picker can pre-select the same pipeline as the current
     * chat, matching legacy `ChatViewModel.requestNewSession` ergonomics.
     */
    fun currentPipelineId(): String? = sessions.firstOrNull { it.id == _currentSessionId.value }?.pipelineId

    /** Resting (non-overlay) state given the current message list — `Empty` if no messages, else `Idle`. */
    private fun restingState(): ChatHomeUiState =
        if (_messages.value.isEmpty()) ChatHomeUiState.Empty else ChatHomeUiState.Idle

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
        val sessionId = _currentSessionId.value
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
            runCatching { chatRepository.importChat(json) }
                .onSuccess { newId -> selectThread(newId) }
                .onFailure { error ->
                    _importErrorEvents.tryEmit(
                        error.localizedMessage ?: IMPORT_GENERIC_FAILURE_MESSAGE,
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
        val sessionId = _currentSessionId.value
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            runCatching {
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
                ChatExportPayload(sessionName = sessionName, json = root.toString(EXPORT_JSON_INDENT))
            }.onSuccess { payload -> _exportEvents.tryEmit(payload) }
        }
    }

    /**
     * Deletes the currently active session and auto-selects the next
     * available thread; when no other session exists, creates a fresh
     * unbound chat so the user is never stranded on a non-existent
     * session id.
     */
    fun deleteCurrentSession() {
        val sessionId = _currentSessionId.value
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

    companion object {
        /** Pre-formatted fallback thread title surfaced before any thread is selected. */
        const val DEFAULT_THREAD_TITLE: String = "New conversation"

        /** Pre-formatted fallback model name surfaced when no local model is loaded. */
        const val DEFAULT_MODEL_NAME: String = "Local model"

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
         * while no LLM model is loaded. Kept here as a constant rather
         * than read from `strings_errors.xml` so the VM stays free of
         * `Context`/`Resources` dependencies; the screen layer will swap
         * this for a localised string once Phase 22 / Task 5 audits
         * surface localisation (the equivalent error in legacy chat
         * lives under `errors_chat_load_model_first`).
         */
        const val LOAD_MODEL_FIRST_MESSAGE: String =
            "Please load a model in Settings before sending a message."

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
         * Template of the SYSTEM chat row persisted when the clarification
         * watchdog elapses without a user reply. `%s` is replaced with the
         * default answer that the pipeline actually consumed.
         */
        const val SYSTEM_MESSAGE_CLARIFICATION_TIMED_OUT: String =
            "Clarification timed out; default answer used: %s."

        /**
         * Placeholder inserted into the timeout SYSTEM message when the
         * agent fell back to an empty default (free-form request with no
         * options).
         */
        const val CLARIFICATION_DEFAULT_BLANK: String = "(blank)"

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
            return ChatHomeMessageRow(
                id = rowId,
                role = role,
                content = ChatContent.Text(message.content),
                metadata = metadata,
            )
        }
    }
}

/**
 * Minimal pipeline summary used by [ChatHomeViewModel] to resolve the
 * TopAppBar subtitle and the deleted-pipeline fallback. Mirrors the
 * legacy `chat.legacy.PipelineSummary` so the same shape is available
 * after the legacy package is deleted in Phase 22 / Task 17.
 *
 * @property id stable identifier of the pipeline.
 * @property name display name of the pipeline.
 */
internal data class PipelineSummary(val id: String, val name: String)

/**
 * Snapshot of the tool the orchestrator is currently paused on, exposed
 * by [ChatHomeViewModel.pendingTool] so the mapper can render the
 * trailing HITL confirmation card from real data instead of fixtures.
 *
 * @property toolName fully-qualified tool id (e.g. `fs.write_file`).
 * @property arguments raw JSON-encoded argument blob emitted by the agent.
 * @property risk per-tool risk tier resolved by `ToolRepository.getRisk`.
 */
data class HitlPending(val toolName: String, val arguments: String, val risk: ToolRisk)

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
