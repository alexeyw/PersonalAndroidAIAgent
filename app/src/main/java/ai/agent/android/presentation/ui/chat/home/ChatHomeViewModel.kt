package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatMessageStatus
import app.knotwork.design.components.chat.ChatMetadata
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsoleFilter
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleSource
import app.knotwork.design.screens.chat.ChatHomeMessageRow
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

    private var messagesJob: Job? = null
    private var generationJob: Job? = null
    private var tokenCounterJob: Job? = null
    private var clarificationTimeoutJob: Job? = null
    private var sessions: List<ChatSession> = emptyList()
    private var availablePipelinesObserved: Boolean = false
    private var availablePipelines: List<PipelineSummary> = emptyList()
    private var defaultPipelineId: String? = null

    init {
        observeAvailablePipelines()
        observeDefaultPipelineId()
        observeSessions()
        observeMaxContextSize()
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

    /** Opens the console pane at the given [snap] (default: Partial). */
    fun openConsole(snap: ConsoleSnap = ConsoleSnap.Partial) {
        _state.value = ChatHomeUiState.ConsoleExpanded(snap)
    }

    /** Updates the snap point of the currently-open console pane. */
    fun setConsoleSnap(snap: ConsoleSnap) {
        _state.update { current ->
            if (current is ChatHomeUiState.ConsoleExpanded) ChatHomeUiState.ConsoleExpanded(snap) else current
        }
    }

    /** Dismisses the console pane and settles on the right resting state. */
    fun closeConsole() {
        if (_state.value is ChatHomeUiState.ConsoleExpanded) {
            _state.value = restingState()
        }
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
                availablePipelinesObserved = true
                refreshPipelineName()
                handleDeletedBoundPipeline(summaries)
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
                handleDeletedBoundPipeline(availablePipelines)
            }
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
            is AgentOrchestratorState.Completed -> {
                clearPendingApprovalAndClarification()
                _state.value = if (_messages.value.isEmpty()) ChatHomeUiState.Empty else ChatHomeUiState.Idle
            }
            is AgentOrchestratorState.Error -> {
                clearPendingApprovalAndClarification()
                _state.value = ChatHomeUiState.Error(state.message)
            }
            // Intermediate states keep `Generating` while the request is in flight.
            // Console handling lands in Phase 22 / Task 3/17.
            else -> Unit
        }
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
        _state.value = restingState()
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
        val trimmed = answer.trim()
        if (trimmed.isEmpty()) return
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
     * appends a SYSTEM chat row recording the fallback, and settles the
     * surface back on a resting state.
     */
    private fun onClarificationTimeout(request: ClarificationRequest) {
        if (_pendingClarification.value?.id != request.id) return
        val defaultAnswer = request.options?.firstOrNull().orEmpty()
        val sessionId = _currentSessionId.value
        _pendingClarification.value = null
        _state.value = restingState()
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
        } else if (_currentSessionId.value.isBlank()) {
            _threadTitle.value = DEFAULT_THREAD_TITLE
        }
        refreshPipelineName()
    }

    /** Resting (non-overlay) state given the current message list — `Empty` if no messages, else `Idle`. */
    private fun restingState(): ChatHomeUiState =
        if (_messages.value.isEmpty()) ChatHomeUiState.Empty else ChatHomeUiState.Idle

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
