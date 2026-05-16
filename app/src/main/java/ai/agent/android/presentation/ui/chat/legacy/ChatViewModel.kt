package ai.agent.android.presentation.ui.chat.legacy

import ai.agent.android.R
import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.agent.android.presentation.state.ActiveSessionTracker
import ai.agent.android.presentation.ui.common.UiText
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for managing the state of the Chat screen.
 * Orchestrates the interaction between the user input, the chat history, and the AI agent.
 */
@HiltViewModel
@Suppress(
    // Reason: chat hosts every user-visible agent flow: messaging, session
    // switching, pipeline binding, clarification handling, console log,
    // starred filter, export, snackbar one-shots. Splitting would require a
    // shared store (the screen has 12+ user actions and one source-of-truth
    // UI state). Tracked for a future refactor; out of scope for the
    // static-analysis gate.
    "TooManyFunctions",
    "LargeClass",
)
class ChatViewModel @Inject constructor(
    private val agentOrchestratorUseCase: AgentOrchestratorUseCase,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val loadModelUseCase: LoadModelUseCase,
    private val getContextWindowUseCase: GetContextWindowUseCase,
    private val activeSessionTracker: ActiveSessionTracker,
    private val llmInferenceEngine: LlmInferenceEngine,
    private val clarificationRepository: ClarificationRepository,
    private val pipelineRepository: PipelineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())

    /**
     * The current UI state of the Chat screen.
     */
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _exportEvents = MutableSharedFlow<ChatExportPayload>(extraBufferCapacity = 1)

    /**
     * One-shot events emitted when the user triggers a chat export. Consumed by the UI to
     * launch a system share sheet ([android.content.Intent.ACTION_SEND]).
     */
    val exportEvents: SharedFlow<ChatExportPayload> = _exportEvents.asSharedFlow()

    private var messagesJob: Job? = null
    private var generationJob: Job? = null

    /**
     * Number of leading entries of the orchestrator's cumulative
     * [AgentOrchestratorState.ConsoleLog.events] list that the user has
     * already cleared via [clearConsoleLog] during the current run.
     *
     * The orchestrator emits a snapshot of the *full* event log on every
     * step, so simply emptying [ChatUiState.consoleLines] is not durable —
     * the very next snapshot would reintroduce the dismissed entries.
     * Storing a baseline lets the collector slice
     * `events.subList(baseline, events.size)` on every emission so cleared
     * entries stay gone until the run ends. Reset to `0` whenever a fresh
     * run begins ([sendMessage]) or the user switches sessions
     * ([switchSession]).
     */
    private var consoleClearBaseline: Int = 0

    /**
     * Becomes `true` after [pipelineRepository] has emitted its initial snapshot
     * of available pipelines. Used by [handleDeletedBoundPipeline] to avoid a
     * false-positive deleted-pipeline fallback during the brief window where
     * the sessions flow has emitted (with a bound `pipelineId`) but the
     * pipelines flow has not — initial `availablePipelines` is `emptyList()`
     * by default, which is indistinguishable from "no pipelines exist" without
     * this flag.
     */
    private var availablePipelinesObserved: Boolean = false

    /**
     * Mirrors whether the chat screen is currently visible to the user. Updated
     * exclusively by [setChatVisible]; read inside an `init { }` collector that
     * combines this flag with the active session id and pushes the result into
     * [activeSessionTracker], so the tracker stays in sync with the chat
     * visibility *and* with the asynchronously-loaded session id (the previous
     * read-once-on-ON_START approach raced with `initializeSession()` and
     * left the tracker as `null` for an open chat — the inline approval prompt
     * was visible but the push notification still fired because the
     * tracker never caught the late session id).
     */
    private val _isChatVisible = MutableStateFlow(false)

    init {
        loadSessions()
        observeAvailablePipelines()
        observeDefaultPipelineId()
        initializeSession()
        observeMaxContextSize()
        observeActiveSessionTracking()
    }

    /**
     * Observes the user-set default pipeline id from settings and folds it
     * into [ChatUiState.defaultPipelineId]. Recomputes the TopAppBar subtitle
     * because the resolution of "default pipeline" depends on this id —
     * changing it from the library should update the subtitle in real time.
     */
    private fun observeDefaultPipelineId() {
        viewModelScope.launch {
            settingsRepository.defaultPipelineId.collect { id ->
                _uiState.update { state ->
                    state.copy(
                        defaultPipelineId = id,
                        currentPipelineName = resolvePipelineName(
                            sessions = state.sessions,
                            currentSessionId = state.currentSessionId,
                            summaries = state.availablePipelines,
                            defaultPipelineId = id,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Keeps [activeSessionTracker] in lock-step with the latest
     * (`isVisible`, `currentSessionId`) pair. Replaces the previous one-shot
     * read inside [setChatVisible], which fired on `ON_START` and missed the
     * session id that `initializeSession()` saves a few milliseconds later.
     *
     * The tracker becomes:
     *  - the active session id while the chat is visible AND the id is set;
     *  - `null` whenever the chat is hidden or the id is still loading.
     */
    private fun observeActiveSessionTracking() {
        viewModelScope.launch {
            combine(
                _isChatVisible,
                _uiState.map { it.currentSessionId }.distinctUntilChanged(),
            ) { visible, sessionId ->
                if (visible && sessionId.isNotBlank()) sessionId else null
            }
                .distinctUntilChanged()
                .collect { trackedId ->
                    activeSessionTracker.setActiveSessionId(trackedId)
                }
        }
    }

    /**
     * Continuously observes the pipeline library and recomputes:
     *  1. [ChatUiState.availablePipelines] — feeds the new-chat selector and
     *     chat-settings dialog.
     *  2. [ChatUiState.currentPipelineName] — TopAppBar subtitle: name of the
     *     pipeline bound to the current chat (or of the default pipeline when
     *     `pipelineId == null`).
     *  3. Deleted-pipeline fallback: if the pipeline currently bound to the
     *     active chat disappears from the library, silently rebind the chat
     *     to the default pipeline and emit a one-shot Snackbar via
     *     [ChatUiState.pipelineFallbackMessage].
     *
     * The observation is keyed on the pair `(availablePipelines, sessions)` so
     * that switching chats while pipelines are unchanged still triggers a
     * subtitle recompute.
     */
    private fun observeAvailablePipelines() {
        viewModelScope.launch {
            pipelineRepository.getAllPipelines().collect { graphs ->
                val summaries = graphs.map { PipelineSummary(id = it.id, name = it.name) }
                availablePipelinesObserved = true
                _uiState.update { state ->
                    state.copy(
                        availablePipelines = summaries,
                        currentPipelineName = resolvePipelineName(
                            sessions = state.sessions,
                            currentSessionId = state.currentSessionId,
                            summaries = summaries,
                            defaultPipelineId = state.defaultPipelineId,
                        ),
                    )
                }
                handleDeletedBoundPipeline(summaries)
            }
        }
    }

    /**
     * Detects whether the pipeline bound to the currently active chat has been
     * deleted. When it has, persists `pipelineId = null` on the session
     * (falling back to the default pipeline) and surfaces a one-shot Snackbar.
     *
     * No-op when the chat is unbound (`pipelineId == null`), when the bound
     * pipeline still exists, or when the pipelines flow has not yet produced
     * its initial snapshot. The last condition prevents a startup race in
     * which a sessions emission with a bound `pipelineId` arrives before the
     * pipelines flow does — without the [availablePipelinesObserved] guard
     * the empty default `availablePipelines` would be misread as "the bound
     * pipeline no longer exists" and silently rebind the chat to the default.
     */
    private suspend fun handleDeletedBoundPipeline(summaries: List<PipelineSummary>) {
        if (!availablePipelinesObserved) return
        val state = _uiState.value
        val session = state.sessions.firstOrNull { it.id == state.currentSessionId } ?: return
        val boundId = session.pipelineId ?: return
        if (summaries.any { it.id == boundId }) return

        chatRepository.saveSession(session.copy(pipelineId = null))
        _uiState.update {
            it.copy(pipelineFallbackMessage = UiText(R.string.errors_chat_pipeline_removed))
        }
    }

    /**
     * Resolves the display name of the pipeline currently bound to the active
     * chat — either the explicit binding when set, or the default pipeline
     * otherwise. The "default" is the user-marked id from settings
     * ([defaultPipelineId]); when that is `null` or points to a pipeline
     * that no longer exists, the resolution falls back to the first
     * pipeline in [summaries]. Returns `null` when no pipelines exist yet,
     * so the TopAppBar can omit the subtitle entirely.
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

    private fun loadSessions() {
        viewModelScope.launch {
            chatRepository.getSessionsFlow().collect { sessions ->
                _uiState.update { state ->
                    state.copy(
                        sessions = sessions,
                        currentPipelineName = resolvePipelineName(
                            sessions = sessions,
                            currentSessionId = state.currentSessionId,
                            summaries = state.availablePipelines,
                            defaultPipelineId = state.defaultPipelineId,
                        ),
                    )
                }
                handleDeletedBoundPipeline(_uiState.value.availablePipelines)
            }
        }
    }

    private fun observeMaxContextSize() {
        viewModelScope.launch {
            settingsRepository.maxContextLength.collect { maxLength ->
                _uiState.update { it.copy(maxContextSize = maxLength) }
            }
        }
    }

    /**
     * Marks the chat screen as visible (or not). Tracker updates are driven
     * by [observeActiveSessionTracking]'s combine() — flipping this flag is
     * enough; the collector immediately reads the latest session id and
     * pushes the right value into [activeSessionTracker].
     *
     * @param isVisible True if the screen is actively displayed, false otherwise.
     */
    fun setChatVisible(isVisible: Boolean) {
        _isChatVisible.value = isVisible
    }

    /**
     * Initializes the chat session by either restoring the last active session ID
     * or generating a new one if none exists.
     * Also verifies that the LLM model is loaded and ready.
     */
    private fun initializeSession() {
        viewModelScope.launch {
            // Check if model is loaded by attempting to load it
            val modelResult = loadModelUseCase()
            if (modelResult is Result.Error) {
                _uiState.update {
                    it.copy(
                        errorMessage = UiText.of(
                            R.string.errors_chat_model_init_failure,
                            modelResult.message ?: GENERIC_ERROR_FALLBACK,
                        ),
                    )
                }
            }

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

            _uiState.update { it.copy(currentSessionId = sessionId) }
            loadMessages(sessionId)
            // activeSessionTracker is managed exclusively by setChatVisible via Lifecycle events
        }
    }

    /**
     * Opens the new-chat pipeline selector `ModalBottomSheet`. The bottom
     * sheet pre-selects the application-wide default pipeline so a user who
     * just taps "Create" gets the same behaviour as before Phase 17.2.
     *
     * When no pipelines exist yet, falls back to creating an unbound chat
     * directly without prompting — the selector would be empty otherwise.
     */
    fun requestNewSession() {
        val pipelines = _uiState.value.availablePipelines
        if (pipelines.isEmpty()) {
            createNewSession(pipelineId = null)
            return
        }
        _uiState.update {
            it.copy(
                newChatPipelinePrompt = NewChatPipelinePrompt(
                    preselectedPipelineId = pipelines.first().id,
                ),
            )
        }
    }

    /**
     * Dismisses the new-chat pipeline selector without creating a chat.
     */
    fun dismissNewChatPrompt() {
        _uiState.update { it.copy(newChatPipelinePrompt = null) }
    }

    /**
     * Confirms the new-chat pipeline selection and creates the session.
     *
     * @param pipelineId Identifier of the pipeline the new chat should bind
     *   to, or `null` to use the application-wide default pipeline.
     */
    fun confirmNewSession(pipelineId: String?) {
        _uiState.update { it.copy(newChatPipelinePrompt = null) }
        createNewSession(pipelineId = pipelineId)
    }

    /**
     * Creates a new chat session bound to [pipelineId] and switches to it.
     *
     * Internal entry-point used by both [requestNewSession] (no-pipelines
     * shortcut) and [confirmNewSession] (after the selector resolves).
     *
     * @param pipelineId Pipeline identifier to bind, or `null` for the
     *   default pipeline.
     */
    private fun createNewSession(pipelineId: String?) {
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
            switchSession(newId)
        }
    }

    /**
     * Switches the active chat session.
     *
     * @param sessionId The ID of the session to switch to.
     */
    fun switchSession(sessionId: String) {
        viewModelScope.launch {
            settingsRepository.setCurrentChatSessionId(sessionId)
            // The new session has its own (empty) cumulative log, so any
            // mid-stream clear from the previous session is no longer
            // applicable. Reset before the state update so a stray
            // in-flight ConsoleLog snapshot (cancelled below but possibly
            // already in flight) cannot reintroduce dropped entries.
            consoleClearBaseline = 0
            _uiState.update { state ->
                state.copy(
                    currentSessionId = sessionId,
                    isGenerating = false,
                    orchestratorState = null,
                    clarificationCards = emptyList(),
                    consoleLines = emptyList(),
                    currentPipelineName = resolvePipelineName(
                        sessions = state.sessions,
                        currentSessionId = sessionId,
                        summaries = state.availablePipelines,
                        defaultPipelineId = state.defaultPipelineId,
                    ),
                )
            }
            loadMessages(sessionId)
            handleDeletedBoundPipeline(_uiState.value.availablePipelines)
            // No need to poke `activeSessionTracker` here — the
            // `observeActiveSessionTracking()` combiner above watches the
            // session id flow and updates the tracker automatically.
        }
    }

    /**
     * Renames an existing chat session.
     *
     * @param sessionId The ID of the session to rename.
     * @param newName The new name for the session.
     */
    fun renameSession(sessionId: String, newName: String) {
        viewModelScope.launch {
            val session = chatRepository.getSessionById(sessionId)
            if (session != null) {
                chatRepository.saveSession(session.copy(name = newName))
            }
        }
    }

    /**
     * Deletes a chat session and its history.
     *
     * @param sessionId The ID of the session to delete.
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            val currentState = _uiState.value
            if (currentState.currentSessionId == sessionId) {
                // Switch to the first available or create new
                val sessions = currentState.sessions.filter { it.id != sessionId }
                if (sessions.isNotEmpty()) {
                    switchSession(sessions.first().id)
                } else {
                    createNewSession(pipelineId = null)
                }
            }
        }
    }

    /**
     * Loads the chat messages for the given session ID and observes changes.
     *
     * The source flow depends on [ChatUiState.showStarredOnly]:
     *  - `false` (default): only user-facing messages of the active session
     *    (`isFinal = true`) — intermediate node outputs are excluded so the
     *    main chat stays clean.
     *  - `true`: every starred message across all sessions, most recent first.
     *
     * The context-size readout still reflects the active session's *full*
     * (unfiltered) history regardless of the filter, so the user sees the
     * accurate token-window cost even while the starred filter is active.
     *
     * @param sessionId The ID of the chat session to load.
     */
    private fun loadMessages(sessionId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            val source = if (_uiState.value.showStarredOnly) {
                chatRepository.getStarredMessages()
            } else {
                chatRepository.getDisplayMessagesForSession(sessionId)
            }
            source.collect { messages ->
                val contextString = getContextWindowUseCase(sessionId)
                _uiState.update {
                    it.copy(
                        messages = messages,
                        contextSize = contextString.length,
                    )
                }
            }
        }
    }

    /**
     * Toggles the "starred only" filter on the chat list and re-starts the
     * message observation so the [ChatUiState.messages] source flow swaps
     * between the per-session display flow and the global starred flow.
     */
    fun toggleStarredFilter() {
        _uiState.update { it.copy(showStarredOnly = !it.showStarredOnly) }
        loadMessages(_uiState.value.currentSessionId)
    }

    /**
     * Toggles the starred state of [messageId] in the database. The Room flow
     * underlying [ChatUiState.messages] re-emits automatically once the row
     * changes, so the UI updates without further action from the caller.
     *
     * @param messageId Database id of the message to update.
     * @param starred New starred state to persist.
     */
    fun setMessageStarred(messageId: Long, starred: Boolean) {
        viewModelScope.launch {
            chatRepository.setMessageStarred(messageId, starred)
        }
    }

    /**
     * Surfaces the "Copied" Snackbar after the UI has placed message text on
     * the system clipboard. The actual `ClipboardManager` interaction happens
     * inside the Composable layer (which has access to `LocalClipboardManager`),
     * so the ViewModel itself stays free of Android-framework dependencies.
     */
    fun signalCopiedToClipboard() {
        _uiState.update { it.copy(snackbarMessage = UiText(R.string.chat_snackbar_copied)) }
    }

    /**
     * Clears [ChatUiState.snackbarMessage] after the UI has displayed it.
     * Called from a `LaunchedEffect` once the auto-dismiss timeout elapses.
     */
    fun consumeSnackbar() {
        if (_uiState.value.snackbarMessage != null) {
            _uiState.update { it.copy(snackbarMessage = null) }
        }
    }

    /**
     * Sends a user prompt to the agent and starts the generation cycle.
     * Updates the UI state with intermediate orchestrator steps (streaming).
     *
     * @param prompt The user's input text.
     */
    fun sendMessage(prompt: String) {
        val currentState = _uiState.value
        if (currentState.isGenerating || prompt.isBlank()) return

        if (!llmInferenceEngine.isInitialized) {
            _uiState.update {
                it.copy(inlineError = UiText(R.string.errors_chat_load_model_first))
            }
            return
        }

        generationJob = viewModelScope.launch {
            // Auto-rename logic for new chats
            val currentSession = currentState.sessions.find { it.id == currentState.currentSessionId }
            if (currentSession?.name == DEFAULT_NEW_CHAT_NAME) {
                val newName = if (prompt.length > AUTO_RENAME_CHAR_LIMIT) {
                    prompt.take(AUTO_RENAME_CHAR_LIMIT) + "..."
                } else {
                    prompt
                }
                renameSession(currentState.currentSessionId, newName)
            }

            // Fresh run = fresh cumulative log upstream; the carry-over
            // baseline from a previous run's mid-stream Clear no longer
            // applies because the orchestrator restarts events from scratch.
            consoleClearBaseline = 0
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    errorMessage = null,
                    inlineError = null,
                    orchestratorState = null,
                    pipelineTrace = emptyList(),
                    currentStep = null,
                    clarificationCards = emptyList(),
                    consoleLines = emptyList(),
                )
            }

            val pipelineId = currentState.sessions
                .firstOrNull { it.id == currentState.currentSessionId }
                ?.pipelineId
            agentOrchestratorUseCase(currentState.currentSessionId, prompt, pipelineId)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            currentStep = null,
                            errorMessage = error.message
                                ?.let { msg -> UiText.Dynamic(msg) }
                                ?: UiText(R.string.errors_generic_unexpected),
                            orchestratorState = AgentOrchestratorState.Error(
                                error.message ?: UNKNOWN_ERROR_FALLBACK,
                            ),
                        )
                    }
                }
                .collect { state ->
                    val isTerminal = state is AgentOrchestratorState.Completed || state is AgentOrchestratorState.Error
                    _uiState.update { current ->
                        val updatedCards = if (state is AgentOrchestratorState.AwaitingClarification) {
                            appendClarificationCard(current.clarificationCards, state.request)
                        } else {
                            current.clarificationCards
                        }
                        current.copy(
                            orchestratorState = state,
                            currentStep = when {
                                isTerminal -> null
                                state is AgentOrchestratorState.PipelineStage -> state.stepInfo
                                else -> current.currentStep
                            },
                            pipelineTrace = if (state is AgentOrchestratorState.PipelineTrace) {
                                state.steps
                            } else {
                                current.pipelineTrace
                            },
                            consoleLines = if (state is AgentOrchestratorState.ConsoleLog) {
                                applyConsoleClearBaseline(state.events)
                            } else {
                                current.consoleLines
                            },
                            isGenerating = if (isTerminal) false else current.isGenerating,
                            clarificationCards = updatedCards,
                        )
                    }
                }
        }
    }

    /**
     * Cancels the active generation job and saves the partial response as a stopped message.
     * If the orchestrator was in a [AgentOrchestratorState.Thinking] or [AgentOrchestratorState.Answering]
     * state, the accumulated text is persisted with a "[остановлено]" suffix so the user
     * does not lose the partial output.
     */
    fun stopGeneration() {
        generationJob?.cancel()
        val partial = when (val s = _uiState.value.orchestratorState) {
            is AgentOrchestratorState.Thinking -> s.partialText
            is AgentOrchestratorState.Answering -> s.partialText
            else -> null
        }
        if (!partial.isNullOrBlank()) {
            viewModelScope.launch {
                chatRepository.saveMessage(
                    ChatMessage(
                        sessionId = _uiState.value.currentSessionId,
                        role = Role.AGENT,
                        content = "$partial $STOPPED_SUFFIX",
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }
        }
        _uiState.update {
            it.copy(
                isGenerating = false,
                currentStep = null,
                orchestratorState = null,
                clarificationCards = it.clarificationCards.filterNot { card ->
                    card.status == ClarificationCardUiModel.Status.PENDING
                },
            )
        }
    }

    /**
     * Clears the current error message from the UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Clears the inline error banner (e.g. after the user starts typing a new message or loads a model).
     */
    fun clearInlineError() {
        if (_uiState.value.inlineError != null) {
            _uiState.update { it.copy(inlineError = null) }
        }
    }

    /**
     * Exports the chat history for the given session as a JSON document and emits a
     * [ChatExportPayload] through [exportEvents]. The UI layer is responsible for handing
     * the payload to the Android share sheet via [android.content.Intent.ACTION_SEND].
     *
     * The exported JSON has the shape:
     * ```
     * {
     *   "sessionId": "...",
     *   "sessionName": "...",
     *   "exportedAt": 1700000000000,
     *   "messages": [
     *     { "role": "USER", "text": "...", "timestamp": 1700000000000 },
     *     ...
     *   ]
     * }
     * ```
     *
     * @param sessionId The ID of the session to export.
     */
    fun exportChat(sessionId: String) {
        viewModelScope.launch {
            try {
                val messages = chatRepository.getMessagesForSession(sessionId).first()
                val session = chatRepository.getSessionById(sessionId)
                val sessionName = session?.name ?: EXPORT_FALLBACK_SESSION_NAME

                val messagesArray = JSONArray()
                messages.forEach { message ->
                    val item = JSONObject()
                        .put("role", message.role.name)
                        .put("text", message.content)
                        .put("timestamp", message.timestamp)
                    messagesArray.put(item)
                }
                val root = JSONObject()
                    .put("sessionId", sessionId)
                    .put("sessionName", sessionName)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("messages", messagesArray)

                _exportEvents.emit(ChatExportPayload(sessionName = sessionName, json = root.toString(2)))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = UiText.of(
                            R.string.errors_chat_export_failed,
                            e.localizedMessage ?: GENERIC_ERROR_FALLBACK,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Imports a chat from the provided JSON string into a newly-created session.
     * Messages are parsed and stored via [ChatRepository], and the UI switches to the
     * newly created session on success.
     *
     * Accepted shapes are the one produced by [exportChat] (`{ messages: [...] }`) and
     * a bare top-level array of message objects.
     *
     * @param json The JSON content to import.
     */
    fun importChat(json: String) {
        viewModelScope.launch {
            try {
                val trimmed = json.trim()
                val messagesArray: JSONArray
                var importedSessionName = DEFAULT_IMPORTED_CHAT_NAME
                when {
                    trimmed.startsWith("{") -> {
                        val root = JSONObject(trimmed)
                        importedSessionName = root.optString("sessionName").takeIf { it.isNotBlank() }
                            ?: importedSessionName
                        messagesArray = root.optJSONArray("messages")
                            ?: throw JSONException("Missing 'messages' array")
                    }
                    trimmed.startsWith("[") -> {
                        messagesArray = JSONArray(trimmed)
                    }
                    else -> throw JSONException("Unsupported JSON root")
                }

                val newId = UUID.randomUUID().toString()
                chatRepository.saveSession(
                    ChatSession(
                        id = newId,
                        name = importedSessionName,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )

                for (i in 0 until messagesArray.length()) {
                    val item = messagesArray.getJSONObject(i)
                    val roleStr = item.optString("role").ifBlank { Role.USER.name }
                    val role = runCatching { Role.valueOf(roleStr) }.getOrDefault(Role.USER)
                    val text = item.optString("text")
                    val timestamp = item.optLong("timestamp", System.currentTimeMillis())
                    chatRepository.saveMessage(
                        ChatMessage(
                            sessionId = newId,
                            role = role,
                            content = text,
                            timestamp = timestamp,
                        ),
                    )
                }

                switchSession(newId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = UiText.of(
                            R.string.errors_chat_import_failed,
                            e.localizedMessage ?: INVALID_JSON_FALLBACK,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Resumes the paused orchestrator cycle by approving or denying a tool execution.
     *
     * @param isApproved True to execute the tool, False to cancel.
     */
    fun resumeWithApproval(isApproved: Boolean) {
        val currentState = _uiState.value
        agentOrchestratorUseCase.resumeWithApproval(currentState.currentSessionId, isApproved)
    }

    /**
     * Forwards the user's reply to the suspended pipeline coroutine via
     * [ClarificationRepository.submitClarification] and updates the matching
     * [ClarificationCardUiModel] based on whether the repository accepted the reply.
     *
     * - When the repository returns `true` (the suspended coroutine consumed
     *   [answer]), the card flips to [ClarificationCardUiModel.Status.ANSWERED]
     *   showing what the user typed.
     * - When the repository returns `false` (the request already timed out,
     *   was already answered, or the id is unknown), the card flips to
     *   [ClarificationCardUiModel.Status.TIMED_OUT] showing the default answer the
     *   pipeline actually consumed. This prevents "You answered: …" from being
     *   shown when the agent in fact moved on with a different value.
     *
     * No-op if [requestId] does not match any currently pending card.
     *
     * @param requestId The id of the clarification card being answered.
     * @param answer The user's reply text (option label or free-form input).
     */
    fun submitClarification(requestId: String, answer: String) {
        val matched = _uiState.value.clarificationCards.firstOrNull {
            it.id == requestId && it.status == ClarificationCardUiModel.Status.PENDING
        } ?: return

        viewModelScope.launch {
            val accepted = clarificationRepository.submitClarification(matched.id, answer)
            _uiState.update { current ->
                current.copy(
                    clarificationCards = current.clarificationCards.map { card ->
                        if (card.id == requestId && card.status == ClarificationCardUiModel.Status.PENDING) {
                            if (accepted) {
                                card.copy(
                                    status = ClarificationCardUiModel.Status.ANSWERED,
                                    answer = answer,
                                )
                            } else {
                                card.copy(
                                    status = ClarificationCardUiModel.Status.TIMED_OUT,
                                    answer = card.options?.firstOrNull().orEmpty(),
                                )
                            }
                        } else {
                            card
                        }
                    },
                )
            }
        }
    }

    /**
     * Marks a clarification card as timed out from the UI's perspective.
     *
     * The repository owns the authoritative timeout (`withTimeout`); this call only
     * updates the visual state of the card so the user sees that the agent fell back
     * to a default answer. It deliberately does NOT call the repository — the
     * suspended pipeline coroutine resumes on its own when the repository's timer
     * fires, and double-submitting would race against that resumption.
     *
     * @param requestId The id of the clarification card whose visual countdown elapsed.
     * @param defaultAnswer The default answer the agent will receive (first option, or
     *   empty string for free-form requests).
     */
    fun markClarificationTimedOut(requestId: String, defaultAnswer: String) {
        _uiState.update { current ->
            current.copy(
                clarificationCards = current.clarificationCards.map { card ->
                    if (card.id == requestId && card.status == ClarificationCardUiModel.Status.PENDING) {
                        card.copy(status = ClarificationCardUiModel.Status.TIMED_OUT, answer = defaultAnswer)
                    } else {
                        card
                    }
                },
            )
        }
    }

    /**
     * Opens the chat-settings dialog (pipeline rebind for the active chat).
     * Pre-selects the currently bound pipeline so an accidental "Save" is a
     * no-op.
     */
    fun openChatSettings() {
        val state = _uiState.value
        val currentBoundId = state.sessions
            .firstOrNull { it.id == state.currentSessionId }
            ?.pipelineId
        _uiState.update {
            it.copy(chatSettingsDialog = ChatSettingsDialogState(selectedPipelineId = currentBoundId))
        }
    }

    /**
     * Updates the highlighted pipeline inside the open chat-settings dialog
     * without persisting anything yet.
     */
    fun updateChatSettingsSelection(pipelineId: String?) {
        _uiState.update { state ->
            state.chatSettingsDialog?.let { dialog ->
                state.copy(chatSettingsDialog = dialog.copy(selectedPipelineId = pipelineId))
            } ?: state
        }
    }

    /**
     * Closes the chat-settings dialog without applying the selection.
     */
    fun dismissChatSettings() {
        _uiState.update { it.copy(chatSettingsDialog = null) }
    }

    /**
     * Confirms the chat-settings dialog: writes the chosen pipeline id (or
     * `null` for default) into the active session.
     *
     * If a generation is currently in flight, instead of mutating the
     * session immediately the ViewModel raises a `PipelineSwitchConfirm`
     * dialog asking the user whether to cancel generation and switch, or
     * wait. This implements UX option (a) agreed in the plan.
     *
     * The chat-settings dialog is *not* dismissed when the confirm is
     * raised — it stays open behind the confirm so that "Wait" returns the
     * user to a stable surface (and so the second `Dialog` window is not
     * swallowed by the simultaneous dismiss-and-show transition that would
     * otherwise happen).
     */
    fun confirmChatSettings() {
        val state = _uiState.value
        val dialog = state.chatSettingsDialog ?: return
        val targetId = dialog.selectedPipelineId
        val currentBoundId = state.sessions
            .firstOrNull { it.id == state.currentSessionId }
            ?.pipelineId

        if (targetId == currentBoundId) {
            _uiState.update { it.copy(chatSettingsDialog = null) }
            return
        }

        if (state.isGenerating) {
            _uiState.update {
                it.copy(
                    pipelineSwitchConfirm = PipelineSwitchConfirmState(targetPipelineId = targetId),
                )
            }
            return
        }

        applySessionPipeline(targetId)
        _uiState.update { it.copy(chatSettingsDialog = null) }
    }

    /**
     * Resolves the pipeline-switch confirmation: cancels the in-flight
     * generation, applies the requested pipeline id, and dismisses both the
     * confirm dialog and the chat-settings dialog underneath it.
     */
    fun confirmPipelineSwitchCancelGeneration() {
        val target = _uiState.value.pipelineSwitchConfirm?.targetPipelineId
        stopGeneration()
        applySessionPipeline(target)
        _uiState.update { it.copy(pipelineSwitchConfirm = null, chatSettingsDialog = null) }
    }

    /**
     * Resolves the pipeline-switch confirmation by waiting: dismisses only
     * the confirm overlay, leaving the chat-settings dialog open underneath
     * so the user can change their pick or cancel out of settings entirely.
     */
    fun dismissPipelineSwitchConfirm() {
        _uiState.update { it.copy(pipelineSwitchConfirm = null) }
    }

    /**
     * Persists the new pipeline binding for the currently active session and
     * refreshes the TopAppBar subtitle. No-op when no chat is loaded.
     */
    private fun applySessionPipeline(pipelineId: String?) {
        viewModelScope.launch {
            val state = _uiState.value
            val session = state.sessions.firstOrNull { it.id == state.currentSessionId } ?: return@launch
            chatRepository.saveSession(session.copy(pipelineId = pipelineId))
        }
    }

    /**
     * Clears the one-shot deleted-pipeline fallback Snackbar after the UI has
     * displayed it.
     */
    fun clearPipelineFallback() {
        if (_uiState.value.pipelineFallbackMessage != null) {
            _uiState.update { it.copy(pipelineFallbackMessage = null) }
        }
    }

    /**
     * Opens the expanded-console `ModalBottomSheet` (Phase 17.5). Triggered
     * by tapping the collapsed mini-console; the sheet renders the full
     * chronological event log of the current session with filter chips and
     * `Clear` / `Copy all` controls.
     */
    fun openConsoleSheet() {
        if (!_uiState.value.consoleSheetVisible) {
            _uiState.update { it.copy(consoleSheetVisible = true) }
        }
    }

    /**
     * Closes the expanded-console sheet without altering the log itself.
     * Called from the sheet's dismiss callbacks (drag-down, scrim tap).
     */
    fun dismissConsoleSheet() {
        if (_uiState.value.consoleSheetVisible) {
            _uiState.update { it.copy(consoleSheetVisible = false) }
        }
    }

    /**
     * Persists the user's currently-selected [ConsoleLogFilter] chip in the
     * expanded console. Kept on the ViewModel rather than as sheet-local
     * state so the chip survives configuration changes and a quick
     * dismiss + reopen — both of which would otherwise reset the user's
     * picked lens to [ConsoleLogFilter.All].
     */
    fun setConsoleFilter(filter: ConsoleLogFilter) {
        if (_uiState.value.consoleSheetFilter != filter) {
            _uiState.update { it.copy(consoleSheetFilter = filter) }
        }
    }

    /**
     * Clears the in-memory console log of the current session. Surfaced
     * through the expanded console's `Clear` action after the user
     * confirms the destructive `AlertDialog`.
     *
     * The log lives only in [ChatUiState.consoleLines] and is intentionally
     * not persisted; resetting the list also makes the collapsed
     * mini-console drain to its three empty slots until the next pipeline
     * event arrives.
     *
     * Durable mid-generation: the orchestrator emits cumulative
     * [AgentOrchestratorState.ConsoleLog] snapshots on every step, so this
     * also advances [consoleClearBaseline] by the count of currently
     * visible events. The next snapshot's
     * `events.subList(consoleClearBaseline, ...)` projection drops every
     * entry the user just dismissed, so cleared rows do not pop back in
     * on the next emission.
     */
    fun clearConsoleLog() {
        val visible = _uiState.value.consoleLines
        if (visible.isEmpty()) return
        consoleClearBaseline += visible.size
        _uiState.update { it.copy(consoleLines = emptyList()) }
    }

    /**
     * Trims the leading [consoleClearBaseline] entries off a cumulative
     * [AgentOrchestratorState.ConsoleLog.events] snapshot before it is
     * mirrored into [ChatUiState.consoleLines]. When the baseline already
     * covers the snapshot (because no new events have arrived since the
     * last Clear) the result is an empty list, leaving the panel blank
     * until the next event lands.
     *
     * @param events Cumulative event list emitted by the orchestrator on
     *   the current step.
     */
    private fun applyConsoleClearBaseline(events: List<ConsoleEvent>): List<ConsoleEvent> {
        if (consoleClearBaseline <= 0) return events
        if (consoleClearBaseline >= events.size) return emptyList()
        return events.subList(consoleClearBaseline, events.size)
    }

    /**
     * Surfaces the "Console log copied" Snackbar after the UI has placed
     * the rendered plain-text dump on the system clipboard. Mirrors the
     * pattern used by [signalCopiedToClipboard] for chat messages — the
     * `ClipboardManager` interaction itself happens inside the Composable
     * layer (which has access to `LocalClipboardManager`).
     */
    fun signalConsoleCopied() {
        _uiState.update { it.copy(snackbarMessage = UiText(R.string.chat_snackbar_console_copied)) }
    }

    /**
     * Appends a UI card for [request] to [existing], deduplicating by request id so the
     * same `AwaitingClarification` state replayed by re-collection (e.g. after a
     * recomposition or state restoration) does not create a duplicate card.
     */
    private fun appendClarificationCard(
        existing: List<ClarificationCardUiModel>,
        request: ClarificationRequest,
    ): List<ClarificationCardUiModel> {
        if (existing.any { it.id == request.id }) return existing
        return existing + ClarificationCardUiModel(
            id = request.id,
            question = request.question,
            options = request.options,
            timeoutMs = request.timeoutMs,
            startedAtMs = SystemClock.uptimeMillis(),
        )
    }

    companion object {
        /**
         * Default name assigned to a freshly-created chat session. Persisted
         * in the database as the session label until the auto-rename logic
         * replaces it on the first user message. Also compared against the
         * stored name to gate that auto-rename — must stay in sync everywhere.
         */
        const val DEFAULT_NEW_CHAT_NAME = "New Chat"

        /**
         * Default name assigned to a chat session created via the JSON-import
         * path when the incoming document carries no `sessionName` field.
         */
        const val DEFAULT_IMPORTED_CHAT_NAME = "Imported Chat"

        /**
         * Fallback session name used as the `EXTRA_SUBJECT` of the share-sheet
         * intent when the exported chat has no stored name yet (defensive —
         * sessions always carry a name in normal flows).
         */
        const val EXPORT_FALLBACK_SESSION_NAME = "Chat"

        /**
         * Maximum number of characters of the first user message used as the
         * auto-generated chat session name (`"<first-N chars>…"`). Keeps the
         * drawer entry concise without losing the user's framing.
         */
        const val AUTO_RENAME_CHAR_LIMIT: Int = 20

        /**
         * Suffix appended to a partial agent message persisted by
         * [stopGeneration] so the chat history records that the user
         * interrupted generation rather than the agent producing this exact
         * content.
         */
        const val STOPPED_SUFFIX = "[stopped]"

        /**
         * Fallback message attached to [AgentOrchestratorState.Error] when the
         * thrown exception has no `message`. Internal — never expected to
         * surface to the user under normal operation.
         */
        const val UNKNOWN_ERROR_FALLBACK = "Unknown error"

        /** Inline fallback used in the export-failure message string. */
        const val GENERIC_ERROR_FALLBACK = "unknown error"

        /** Inline fallback used in the import-failure message string. */
        const val INVALID_JSON_FALLBACK = "invalid JSON"
    }
}
