package app.knotwork.android.presentation.ui.chat.home

import app.knotwork.android.domain.models.ClarificationRequest
import app.knotwork.android.domain.models.LocalModel
import app.knotwork.design.screens.chat.ChatHomeConsoleState
import app.knotwork.design.screens.chat.ChatHomeMessageRow
import app.knotwork.design.screens.chat.ChatHomeThreadRow

/**
 * Single immutable snapshot of everything the chat-home surface renders.
 *
 * Replaces the former constellation of ~25 independent `StateFlow`s on
 * [ChatHomeViewModel] with one source of truth: the ViewModel owns a single
 * `MutableStateFlow<ChatHomeScreenState>` and every mutation goes through
 * `update { it.copy(...) }`, while the screen performs a single
 * `collectAsStateWithLifecycle` and hands the immutable sub-structures down
 * the tree as-is (each sub-structure is a stable `data class`, so child
 * composables skip recomposition when their slice did not change).
 *
 * One-shot events (export payloads, snackbars, pipeline-fallback signals)
 * intentionally stay outside this snapshot — they are delivered through the
 * ViewModel's `SharedFlow` channels because replaying them on
 * re-subscription would duplicate side effects.
 *
 * @property visual sealed visual axis of the surface (Loading / Empty /
 *   Idle / Generating / HitlConfirm / Clarification / Error / DrawerOpen).
 *   Kept as the pre-existing [ChatHomeUiState] hierarchy — the debug state
 *   picker and the catalog mapping both key off it.
 * @property composer composer input slice ([ChatHomeComposerState]).
 * @property console console-pane render state. Reuses the catalog
 *   [ChatHomeConsoleState] verbatim so the screen-side mapping can pass it
 *   through to `ChatHomeViewState` without re-projection.
 * @property consoleClearConfirmRequested whether the destructive "Clear
 *   console for this session?" confirmation dialog is up. Lives next to —
 *   not inside — [console] because the dialog is screen chrome, not part of
 *   the catalog console pane's render contract.
 * @property pending orchestrator pause snapshots ([ChatHomePendingState]).
 * @property thread active-thread metadata + drawer rows ([ChatHomeThreadState]).
 * @property model local-model picker slice ([ChatHomeModelState]).
 * @property tokens token-meter slice ([ChatHomeTokenState]).
 * @property messages chat rows projected from the repository display flow.
 * @property pipelineName display name of the pipeline bound to the active
 *   chat (explicit binding, else the user-marked default), or `null` when
 *   neither resolves — the TopAppBar subtitle must not advertise a pipeline
 *   that execution would never pick.
 * @property availablePipelines pipeline summaries surfaced by the
 *   new-thread pipeline picker.
 */
data class ChatHomeScreenState(
    val visual: ChatHomeUiState = ChatHomeUiState.Loading,
    val composer: ChatHomeComposerState = ChatHomeComposerState(),
    val console: ChatHomeConsoleState = ChatHomeConsoleState(),
    val consoleClearConfirmRequested: Boolean = false,
    val pending: ChatHomePendingState = ChatHomePendingState(),
    val thread: ChatHomeThreadState = ChatHomeThreadState(),
    val model: ChatHomeModelState = ChatHomeModelState(),
    val tokens: ChatHomeTokenState = ChatHomeTokenState(),
    val messages: List<ChatHomeMessageRow> = emptyList(),
    val pipelineName: String? = null,
    val availablePipelines: List<PipelineSummary> = emptyList(),
)

/**
 * Composer input slice of [ChatHomeScreenState].
 *
 * Named `ChatHomeComposerState` (not `ComposerState`) to avoid colliding
 * with the catalog's `app.knotwork.design.components.chat.ComposerState`,
 * which models the composer's visual mode rather than its text content.
 *
 * @property value current composer draft. Hoisted to the ViewModel so
 *   screen recompositions never own the text.
 * @property typedConfirm typed-confirm input shown next to the Destructive
 *   HITL confirmation row; must equal the magic word
 *   ([ChatHomeViewModel.DESTRUCTIVE_TYPED_CONFIRM_WORD], case-insensitive)
 *   before a destructive tool can be approved.
 */
data class ChatHomeComposerState(val value: String = "", val typedConfirm: String = "")

/**
 * Snapshots of whatever the orchestrator is currently paused on.
 *
 * @property tool tool invocation awaiting HITL approval (`null` when no
 *   approval gate is active). Renders the trailing confirmation card.
 * @property clarification clarification request awaiting the user's reply
 *   (`null` when the agent is not waiting). Renders the trailing
 *   clarification card.
 */
data class ChatHomePendingState(val tool: HitlPending? = null, val clarification: ClarificationRequest? = null)

/**
 * Active-thread metadata and the drawer thread list.
 *
 * @property title thread title rendered in the TopAppBar.
 * @property rows drawer thread list projected from the live session cache;
 *   favorited sessions sort to the top, the rest follow `updatedAt DESC`.
 * @property favorite whether the active session is favorited — drives the
 *   TopAppBar star icon.
 * @property currentSessionId id of the active chat session (blank before
 *   session initialisation completes).
 */
data class ChatHomeThreadState(
    val title: String = ChatHomeViewModel.DEFAULT_THREAD_TITLE,
    val rows: List<ChatHomeThreadRow> = emptyList(),
    val favorite: Boolean = false,
    val currentSessionId: String = "",
)

/**
 * Local-model slice feeding the TopAppBar subtitle and the model-picker
 * sheet.
 *
 * @property name display name of the currently active local model, or the
 *   [ChatHomeViewModel.DEFAULT_MODEL_NAME] placeholder when none is active.
 * @property installed locally installed LiteRT models listed by the picker.
 * @property activeId row id of the currently active model (`null` when none
 *   is active) — the picker renders the checkmark from this.
 */
data class ChatHomeModelState(
    val name: String = ChatHomeViewModel.DEFAULT_MODEL_NAME,
    val installed: List<LocalModel> = emptyList(),
    val activeId: Long? = null,
)

/**
 * Token-meter slice of [ChatHomeScreenState].
 *
 * @property used rough token usage of the active session (`text.length / 4`).
 * @property max configured context-window cap propagated from
 *   `SettingsRepository.maxContextLength`.
 * @property streaming running approximate count of tokens produced by the
 *   in-flight LLM stream; surfaced through the agent status pill as
 *   `generating · N tok`. Zero outside of [ChatHomeUiState.Generating].
 */
data class ChatHomeTokenState(val used: Int = 0, val max: Int = 0, val streaming: Int = 0)
