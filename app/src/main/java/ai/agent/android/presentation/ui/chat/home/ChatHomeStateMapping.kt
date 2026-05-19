package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.domain.models.ClarificationRequest
import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatMessageStatus
import app.knotwork.design.components.chat.ChatMetadata
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.chat.ClarificationCardModel
import app.knotwork.design.components.chat.ComposerState
import app.knotwork.design.components.chat.HitlConfirmationModel
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsoleFilter
import app.knotwork.design.components.console.ConsoleLine
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleTab
import app.knotwork.design.components.console.ConsoleTraceSpan
import app.knotwork.design.components.console.ConsoleVarRow
import app.knotwork.design.screens.chat.ChatHomeConsoleState
import app.knotwork.design.screens.chat.ChatHomeMessageRow
import app.knotwork.design.screens.chat.ChatHomeViewState
import app.knotwork.design.screens.chat.ChatHomeVisualState
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure-Kotlin projection of the sealed [ChatHomeUiState] onto the catalog
 * [ChatHomeViewState] consumed by `ChatHomeContent`. Lives in `:app` because
 * the catalog cannot reach `ai.agent.android.*` (Clean Architecture +
 * `decisions.md §3` keep `:catalog` free of `:app` types).
 *
 * The mapping is intentionally chunky — every state owns a complete fixture
 * block. The stub-VM stage holds no real conversation history, so a
 * deterministic story unique to each variant is the cheapest way to make
 * the state picker meaningful while the real backend wiring is still
 * pending (post-v0.1).
 *
 * @param state which UI state to render.
 * @param threadTitle pre-formatted thread title for the TopAppBar.
 * @param modelName display name of the currently-active model.
 * @param composerValue current composer input value.
 * @param pendingTypedConfirm typed-confirm input for Destructive HITL.
 * @return the immutable view-state passed directly to `ChatHomeContent`.
 */
// Single switch over 8 variants; splitting would just shuffle the fixtures.
@Suppress("LongMethod", "LongParameterList")
fun ChatHomeUiState.toViewState(
    threadTitle: String,
    modelName: String,
    fixtures: ChatHomeFixtures = ChatHomeFixtures.forTesting(),
    messages: List<ChatHomeMessageRow> = emptyList(),
    composerValue: String = "",
    pendingTypedConfirm: String = "",
    consoleSearchQuery: String? = null,
    consoleFilter: ConsoleFilter = ConsoleFilter.allOn,
    consoleLogs: List<ConsoleLine> = emptyList(),
    consoleVars: List<ConsoleVarRow> = emptyList(),
    consoleTraces: List<ConsoleTraceSpan> = emptyList(),
    consoleTab: ConsoleTab = ConsoleTab.Logs,
    consoleSnap: ConsoleSnap? = null,
    pipelineName: String = "default",
    tokensUsed: Int = 0,
    tokensMax: Int = 0,
    favorite: Boolean = false,
    pendingTool: HitlPending? = null,
    pendingClarification: ClarificationRequest? = null,
): ChatHomeViewState {
    val consoleState = ChatHomeConsoleState(
        snap = consoleSnap,
        tab = consoleTab,
        logs = consoleLogs,
        vars = consoleVars,
        traces = consoleTraces,
        filter = consoleFilter,
        searchQuery = consoleSearchQuery,
    )
    return when (this) {
        is ChatHomeUiState.Empty -> ChatHomeViewState(
            visualState = ChatHomeVisualState.Empty,
            threadTitle = threadTitle,
            modelName = modelName,
            composerValue = composerValue,
            samplePromptCards = fixtures.suggestionCards,
            pipelineName = pipelineName,
            tokensUsed = tokensUsed,
            tokensMax = tokensMax,
            favorite = favorite,
            agentStatusLine = fixtures.statusIdle,
            console = consoleState,
        )

        is ChatHomeUiState.Idle -> ChatHomeViewState(
            visualState = ChatHomeVisualState.Idle,
            threadTitle = threadTitle,
            modelName = modelName,
            messages = messages,
            composerValue = composerValue,
            pipelineName = pipelineName,
            tokensUsed = tokensUsed,
            tokensMax = tokensMax,
            favorite = favorite,
            agentStatusLine = fixtures.statusIdle,
            console = consoleState,
        )

        is ChatHomeUiState.Generating -> ChatHomeViewState(
            visualState = ChatHomeVisualState.Generating,
            threadTitle = threadTitle,
            modelName = modelName,
            messages = messages,
            composerValue = composerValue,
            composerState = ComposerState.Generating,
            pipelineName = pipelineName,
            tokensUsed = tokensUsed,
            tokensMax = tokensMax,
            favorite = favorite,
            agentStatusLine = fixtures.statusGenerating,
            console = consoleState,
        )

        is ChatHomeUiState.HitlConfirm -> ChatHomeViewState(
            visualState = ChatHomeVisualState.HitlConfirm,
            threadTitle = threadTitle,
            modelName = modelName,
            messages = messages + (pendingTool?.let { liveHitlRow(modelName, it) } ?: hitlRow(modelName, risk)),
            composerValue = composerValue,
            pendingTypedConfirm = pendingTypedConfirm,
            pipelineName = pipelineName,
            tokensUsed = tokensUsed,
            tokensMax = tokensMax,
            favorite = favorite,
            agentStatusLine = fixtures.statusHitl,
            console = consoleState,
        )

        is ChatHomeUiState.Clarification -> ChatHomeViewState(
            visualState = ChatHomeVisualState.Clarification,
            threadTitle = threadTitle,
            modelName = modelName,
            messages = messages + (
                pendingClarification?.let { liveClarificationRow(modelName, it) }
                    ?: clarificationRow(modelName)
                ),
            composerValue = composerValue,
            pipelineName = pipelineName,
            tokensUsed = tokensUsed,
            tokensMax = tokensMax,
            favorite = favorite,
            agentStatusLine = fixtures.statusClarification,
            console = consoleState,
        )

        is ChatHomeUiState.Error -> ChatHomeViewState(
            visualState = ChatHomeVisualState.Error,
            threadTitle = threadTitle,
            modelName = modelName,
            messages = messages,
            composerValue = composerValue,
            composerState = ComposerState.Error(message = message),
            errorMessage = message,
            pipelineName = pipelineName,
            tokensUsed = tokensUsed,
            tokensMax = tokensMax,
            favorite = favorite,
            agentStatusLine = fixtures.statusError,
            console = consoleState,
        )

        is ChatHomeUiState.DrawerOpen -> ChatHomeViewState(
            visualState = ChatHomeVisualState.DrawerOpen,
            threadTitle = threadTitle,
            modelName = modelName,
            messages = messages,
            composerValue = composerValue,
            threads = fixtures.sessionRows,
            pipelineName = pipelineName,
            tokensUsed = tokensUsed,
            tokensMax = tokensMax,
            favorite = favorite,
            agentStatusLine = fixtures.statusIdle,
            console = consoleState,
        )
    }
}

/** Pre-canned baseline conversation used by every non-Empty state. */
internal fun baselineMessages(modelName: String): List<ChatHomeMessageRow> = listOf(
    ChatHomeMessageRow(
        id = "u1",
        role = ChatRole.User,
        content = ChatContent.Text("Summarise the three PRs that landed yesterday."),
        metadata = ChatMetadata(timestamp = "09:14"),
    ),
    ChatHomeMessageRow(
        id = "a1",
        role = ChatRole.Assistant,
        content = ChatContent.Markdown(
            source = "Pipeline editor refactor, context-window meter for the chat header, " +
                "and a memory-summary regression fix.",
        ),
        metadata = ChatMetadata(
            timestamp = "09:14",
            model = modelName,
            tokens = 64,
            status = ChatMessageStatus.Sent,
        ),
    ),
    ChatHomeMessageRow(
        id = "u2",
        role = ChatRole.User,
        content = ChatContent.Text("Add a 30-minute meeting tomorrow at 10:00 to discuss the rollout."),
        metadata = ChatMetadata(timestamp = "09:15"),
    ),
)

/** Trailing HITL confirmation row appended in the HitlConfirm state. */
internal fun hitlRow(modelName: String, risk: Risk): ChatHomeMessageRow {
    val toolName = when (risk) {
        Risk.Readonly -> "calendar.list_events"
        Risk.Sensitive -> "calendar.create_event"
        Risk.Destructive -> "fs.delete_file"
    }
    val summary = when (risk) {
        Risk.Readonly -> "Read tomorrow's events from your work calendar."
        Risk.Sensitive -> "Add a 30-minute meeting \"Rollout sync\" to your work calendar tomorrow at 10:00."
        Risk.Destructive -> "Permanently remove /Users/me/old-notes.md (4.2 KB)."
    }
    val arguments = when (risk) {
        Risk.Readonly -> mapOf("calendar" to "\"work\"", "range" to "\"tomorrow\"")
        Risk.Sensitive -> mapOf(
            "title" to "\"Rollout sync\"",
            "duration" to "30",
            "calendar" to "\"work\"",
        )
        Risk.Destructive -> mapOf(
            "path" to "\"/Users/me/old-notes.md\"",
            "recursive" to "false",
        )
    }
    return ChatHomeMessageRow(
        id = "a-hitl",
        role = ChatRole.Assistant,
        content = ChatContent.Confirmation(
            model = HitlConfirmationModel(
                risk = risk,
                toolName = toolName,
                summary = summary,
                arguments = arguments,
                timestamp = "09:16",
            ),
        ),
        metadata = ChatMetadata(timestamp = "09:16", model = modelName),
    )
}

/**
 * Trailing HITL confirmation row driven by the live [HitlPending]
 * snapshot the orchestrator captured. Renders the real tool name, risk
 * tier, and JSON-decoded argument map; the user-visible "summary" line
 * falls back to the tool name when the agent did not attach one.
 */
internal fun liveHitlRow(modelName: String, pending: HitlPending): ChatHomeMessageRow {
    val argumentsMap = parseHitlArguments(pending.arguments)
    val timestamp = SimpleDateFormat(HITL_TIMESTAMP_PATTERN, Locale.getDefault())
        .format(Date(System.currentTimeMillis()))
    return ChatHomeMessageRow(
        id = "a-hitl-${pending.toolName}",
        role = ChatRole.Assistant,
        content = ChatContent.Confirmation(
            model = HitlConfirmationModel(
                risk = pending.risk.toCatalogRisk(),
                toolName = pending.toolName,
                summary = pending.toolName,
                arguments = argumentsMap,
                timestamp = timestamp,
            ),
        ),
        metadata = ChatMetadata(timestamp = timestamp, model = modelName),
    )
}

/**
 * Trailing clarification row driven by the live [ClarificationRequest]
 * snapshot the orchestrator captured. Renders the real question text and
 * options as quick-reply chips; free-form fallback is supplied by the
 * catalog `ClarificationCard`.
 */
internal fun liveClarificationRow(modelName: String, request: ClarificationRequest): ChatHomeMessageRow {
    val timestamp = SimpleDateFormat(HITL_TIMESTAMP_PATTERN, Locale.getDefault())
        .format(Date(System.currentTimeMillis()))
    return ChatHomeMessageRow(
        id = "a-clar-${request.id}",
        role = ChatRole.Assistant,
        content = ChatContent.Clarification(
            model = ClarificationCardModel(
                question = request.question,
                quickReplies = request.options ?: emptyList(),
            ),
        ),
        metadata = ChatMetadata(timestamp = timestamp, model = modelName),
    )
}

/**
 * Parses the orchestrator-emitted JSON argument blob into the
 * `Map<String, String>` of rendered JSON fragments the catalog
 * `HitlConfirmationCard` expects. Each value is re-serialised through
 * [JSONObject] so strings keep their surrounding double-quotes, numbers /
 * booleans render bare, and nested objects/arrays stay compact JSON.
 * Falls back to a single `args` entry holding the raw blob when the
 * payload is not a parseable object — defensive against agents emitting
 * non-JSON or array-shaped argument payloads.
 */
internal fun parseHitlArguments(raw: String): Map<String, String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyMap()
    return try {
        val obj = JSONObject(trimmed)
        val result = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = renderJsonFragment(obj.get(key))
        }
        result
    } catch (_: JSONException) {
        mapOf(RAW_ARGS_FALLBACK_KEY to trimmed)
    }
}

/**
 * Renders a single JSON value as the fragment string the catalog
 * `HitlConfirmationCard` expects: strings are wrapped in double quotes,
 * numbers and booleans render bare, nested objects/arrays render as
 * compact JSON, and `JSONObject.NULL` becomes `null`.
 */
private fun renderJsonFragment(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is String -> JSONObject.quote(value)
    is JSONObject -> value.toString()
    is JSONArray -> value.toString()
    else -> value.toString()
}

/** Pattern used for the HITL / clarification row timestamp. */
private const val HITL_TIMESTAMP_PATTERN: String = "HH:mm"

/** Key used when the orchestrator's argument blob cannot be parsed as a JSON object. */
internal const val RAW_ARGS_FALLBACK_KEY: String = "args"

/** Trailing clarification row appended in the Clarification state. */
internal fun clarificationRow(modelName: String): ChatHomeMessageRow = ChatHomeMessageRow(
    id = "a-clar",
    role = ChatRole.Assistant,
    content = ChatContent.Clarification(
        model = ClarificationCardModel(
            question = "Which calendar should I add the meeting to?",
            quickReplies = listOf("Work", "Personal", "Family"),
        ),
    ),
    metadata = ChatMetadata(timestamp = "09:16", model = modelName),
)

/**
 * Stable identifiers for every entry in the debug state picker. Kept apart
 * from [ChatHomeUiState] so adding a state to the picker does not force
 * every consumer of the sealed hierarchy to recompile.
 */
internal object DebugStateIds {
    const val EMPTY: String = "empty"
    const val IDLE: String = "idle"
    const val GENERATING: String = "generating"
    const val HITL_READONLY: String = "hitl_readonly"
    const val HITL_SENSITIVE: String = "hitl_sensitive"
    const val HITL_DESTRUCTIVE: String = "hitl_destructive"
    const val CLARIFICATION: String = "clarification"
    const val ERROR: String = "error"
    const val DRAWER_OPEN: String = "drawer_open"
    const val CONSOLE_PARTIAL: String = "console_partial"
    const val CONSOLE_FULL: String = "console_full"
}

/** Maps a [DebugStateIds] value back to the concrete state the picker should set. */
internal fun debugStateForId(id: String): ChatHomeUiState? = when (id) {
    DebugStateIds.EMPTY -> ChatHomeUiState.Empty
    DebugStateIds.IDLE -> ChatHomeUiState.Idle
    DebugStateIds.GENERATING -> ChatHomeUiState.Generating
    DebugStateIds.HITL_READONLY -> ChatHomeUiState.HitlConfirm(Risk.Readonly)
    DebugStateIds.HITL_SENSITIVE -> ChatHomeUiState.HitlConfirm(Risk.Sensitive)
    DebugStateIds.HITL_DESTRUCTIVE -> ChatHomeUiState.HitlConfirm(Risk.Destructive)
    DebugStateIds.CLARIFICATION -> ChatHomeUiState.Clarification
    DebugStateIds.ERROR -> ChatHomeUiState.Error(message = "Something went wrong while generating the reply.")
    DebugStateIds.DRAWER_OPEN -> ChatHomeUiState.DrawerOpen
    // Console snaps are handled separately via [debugConsoleSnapForId] —
    // they no longer correspond to a top-level [ChatHomeUiState] because
    // the console is rendered as an independent overlay.
    else -> null
}

/**
 * Resolves a [DebugStateIds] entry into the [ConsoleSnap] the debug
 * picker should open the console at. Returns `null` for non-console
 * picker entries — the caller falls back to [debugStateForId] for those.
 */
internal fun debugConsoleSnapForId(id: String): ConsoleSnap? = when (id) {
    DebugStateIds.CONSOLE_PARTIAL -> ConsoleSnap.Partial
    DebugStateIds.CONSOLE_FULL -> ConsoleSnap.Full
    else -> null
}
