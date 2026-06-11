package app.knotwork.android.presentation.ui.chat.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.ClarificationRequest
import app.knotwork.android.domain.models.ConsoleEvent
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.models.RunTraceRecord
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.ClarificationRepository
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.RunTraceRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.MemoryAutoExtractionCoordinator
import app.knotwork.android.domain.usecases.AgentOrchestratorUseCase
import app.knotwork.android.domain.usecases.GetContextWindowUseCase
import app.knotwork.android.domain.usecases.LoadModelUseCase
import app.knotwork.android.domain.usecases.SaveMessageToMemoryUseCase
import app.knotwork.android.domain.usecases.SaveToMemoryOutcome
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
import app.knotwork.design.screens.chat.ChatHomeMessageRow
import app.knotwork.design.screens.chat.ChatHomeThreadRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

    private var messagesJob: Job? = null
    private var generationJob: Job? = null
    private var tokenCounterJob: Job? = null
    private var clarificationTimeoutJob: Job? = null
    private var sessions: List<ChatSession> = emptyList()
    private var availablePipelinesObserved: Boolean = false
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

    /**
     * Id of the run whose persisted trace is currently loaded as the console
     * replay baseline, or `null` when no baseline is loaded. A live
     * [AgentOrchestratorState.ConsoleLog] snapshot merges with the baseline
     * only when it carries the same run id — events of a *different* run
     * replace the baseline outright (a fresh send already cleared it via
     * [resetConsoleCachesForNewRun]).
     */
    private var replayedRunId: String? = null

    /**
     * Replayed console events of [replayedRunId], loaded from the persistent
     * run trace when the session opens. Merged with live cumulative
     * snapshots by [ConsoleEvent.seq] (live wins on collision), so the seam
     * between replayed history and the live stream renders without
     * duplicates regardless of how much of the run the live snapshot covers.
     */
    private var replayedConsoleBaseline: List<ConsoleEvent> = emptyList()

    /** Serializes replay loads so a rapid thread switch cannot interleave baselines. */
    private var consoleReplayJob: Job? = null

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

        cancelClarificationWatchdog()
        // Fresh run = fresh cumulative log upstream; the baseline carried
        // over from a previous run's mid-stream Clear no longer applies
        // (the engine restarts events from scratch). Mirrors legacy
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
     * Cancels the active generation job. Mirrors legacy behaviour — does
     * not call into the orchestrator (no cancel-task API on
     * [app.knotwork.android.domain.engine.TaskQueueManager] today); the
     * coroutine cancellation propagates through the engine via the
     * collector teardown.
     */
    fun stopGeneration() {
        generationJob?.cancel()
        cancelClarificationWatchdog()
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
        cancelClarificationWatchdog()
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
        replayConsoleTrace(threadId)
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
            replayConsoleTrace(sessionId)
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
                cancelClarificationWatchdog()
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
                cancelClarificationWatchdog()
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
        val baseline = replayedConsoleBaseline
        if (baseline.isEmpty() || liveRunId == null || liveRunId != replayedRunId) return live
        return mergeConsoleEventsBySeq(baseline, live)
    }

    /**
     * Loads the persisted trace of the session's most relevant run — the
     * active one if any, otherwise the most recently started — and installs
     * it as the console baseline: Logs from the replayed console events,
     * Vars and Traces re-projected from the replayed per-node I/O records
     * through the exact same mappers as the live path. A session without
     * runs (or without a persisted trace) leaves the console empty, which
     * matches the pre-replay behaviour.
     */
    private fun replayConsoleTrace(sessionId: String) {
        consoleReplayJob?.cancel()
        consoleReplayJob = viewModelScope.launch {
            val run = pipelineRunRepository.getActiveRunForSession(sessionId)
                ?: pipelineRunRepository.getLatestRunForSession(sessionId)
                ?: return@launch
            val trace = runTraceRepository.getTraceForRun(run.id)
            if (trace.isEmpty()) return@launch
            replayedRunId = run.id
            replayedConsoleBaseline = trace
                .filterIsInstance<RunTraceRecord.ConsoleEntry>()
                .map(::consoleEntryToConsoleEvent)
            val nodeIoRecords = trace.filterIsInstance<RunTraceRecord.NodeIo>()
            nodeIoSnapshots.clear()
            nodeIoRecords.forEach { record -> nodeIoSnapshots[record.nodeId] = nodeIoRecordToNodeIo(record) }
            val logs = applyConsoleClearBaseline(replayedConsoleBaseline).map(ConsoleEvent::toConsoleLine)
            val vars = nodeIoSnapshots.values.flatMap(::nodeIoToVarRows)
            val traces = nodeIoRecords.map(::nodeIoRecordToConsoleSpan)
            _state.update {
                it.copy(console = it.console.copy(logs = logs, vars = vars, traces = traces))
            }
        }
    }

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
        // switch reloads its own baseline via replayConsoleTrace.
        consoleReplayJob?.cancel()
        replayedRunId = null
        replayedConsoleBaseline = emptyList()
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
        agentOrchestratorUseCase.resumeWithApproval(sessionId, true)
        _state.update {
            it.copy(
                pending = it.pending.copy(tool = null),
                composer = it.composer.copy(typedConfirm = ""),
                visual = ChatHomeUiState.Generating,
            )
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
        agentOrchestratorUseCase.resumeWithApproval(sessionId, false)
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
        val pending = _state.value.pending.clarification ?: return
        // Allow an empty reply through — the orchestrator already accepts
        // `""` as the timeout fallback for free-form requests, so an
        // intentional blank submit is a legitimate "skip" affordance.
        val trimmed = answer.trim()
        cancelClarificationWatchdog()
        _state.update {
            it.copy(
                pending = it.pending.copy(clarification = null),
                visual = ChatHomeUiState.Generating,
            )
        }
        viewModelScope.launch {
            clarificationRepository.submitClarification(pending.id, trimmed)
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
     * Captures the orchestrator's pending clarification, flips the UI to
     * the Clarification state, and arms the watchdog timer. The repository
     * owns the authoritative timeout via `withTimeout`; this watchdog is a
     * UI safety-net that ensures the surface flips back to a resting state
     * even if the user backgrounds the app for longer than [ClarificationRequest.timeoutMs].
     */
    private fun handleAwaitingClarification(request: ClarificationRequest) {
        _state.update {
            it.copy(
                pending = it.pending.copy(clarification = request),
                visual = ChatHomeUiState.Clarification,
            )
        }
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
        if (_state.value.pending.clarification?.id != request.id) return
        val defaultAnswer = request.options?.firstOrNull().orEmpty()
        val sessionId = _state.value.thread.currentSessionId
        _state.update {
            it.copy(
                pending = it.pending.copy(clarification = null),
                visual = ChatHomeUiState.Generating,
            )
        }
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
        _state.value.composer.typedConfirm.trim().equals(DESTRUCTIVE_TYPED_CONFIRM_WORD, ignoreCase = true)

    /** Cancels and drops the clarification watchdog timer. */
    private fun cancelClarificationWatchdog() {
        clarificationTimeoutJob?.cancel()
        clarificationTimeoutJob = null
    }

    /**
     * Pure transformer: drops every pending HITL / clarification snapshot
     * and resets the typed-confirm input. Composed into the caller's
     * `_state.update` block (never its own emission) so multi-field
     * transitions remain a single atomic flow emission. Callers cancel the
     * watchdog separately via [cancelClarificationWatchdog] — job
     * cancellation is a side effect and must not live inside the `update`
     * lambda, which may re-run on contention.
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
     * Resting (non-overlay) visual for this snapshot — `Empty` if the
     * message list is empty, else `Idle`. Derived from the receiver (never
     * `_state.value`) so calls inside an `_state.update` lambda stay
     * consistent with the snapshot being transformed.
     */
    private fun ChatHomeScreenState.restingVisual(): ChatHomeUiState =
        if (messages.isEmpty()) ChatHomeUiState.Empty else ChatHomeUiState.Idle

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
