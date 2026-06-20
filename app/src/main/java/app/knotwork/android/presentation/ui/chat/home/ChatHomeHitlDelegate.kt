package app.knotwork.android.presentation.ui.chat.home

import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.ClarificationRequest
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.usecases.PendingSubmissionOutcome
import app.knotwork.android.domain.usecases.SubmitApprovalDecisionUseCase
import app.knotwork.android.domain.usecases.SubmitClarificationAnswerUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Human-in-the-loop / clarification delegate of [ChatHomeViewModel].
 *
 * Owns the approval gate and the clarification reply: it writes the
 * `pending.tool` / `pending.clarification` snapshots, the destructive
 * typed-confirm input, and (because both flip the surface) the `visual` axis.
 * On a parked-run resume it re-attaches the live collector through the
 * [attachToLiveRun] seam (the ViewModel owns the collector), and surfaces
 * resume failures through [emitResumeFeedback] (the reattach delegate owns the
 * shared `resumeFeedbackEvents` channel).
 *
 * The capture handlers [handleWaitingForApproval] / [handleAwaitingClarification]
 * are public because the ViewModel's orchestrator router and the reattach
 * delegate's suspension-card restore both drive them.
 *
 * Shares the ViewModel's [scope] and single [state] reducer (see
 * `docs/architecture.md` §1.2).
 *
 * @property scope The ViewModel's [androidx.lifecycle.viewModelScope].
 * @property state The ViewModel's single source-of-truth state flow.
 * @property chatRepository Persists the SYSTEM rows recording a denial / undelivered reply.
 * @property submitApprovalDecisionUseCase Routes an approve/deny to the live gate or parked record.
 * @property submitClarificationAnswerUseCase Routes a clarification reply likewise.
 * @property attachToLiveRun Seam into the ViewModel's live-run collector (a parked resume re-attaches).
 * @property emitResumeFeedback Seam into the shared resume-feedback channel (owned by the reattach delegate).
 */
class ChatHomeHitlDelegate(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatHomeScreenState>,
    private val chatRepository: ChatRepository,
    private val submitApprovalDecisionUseCase: SubmitApprovalDecisionUseCase,
    private val submitClarificationAnswerUseCase: SubmitClarificationAnswerUseCase,
    private val attachToLiveRun: suspend (String, PipelineRunStatus) -> Unit,
    private val emitResumeFeedback: (ResumeFeedbackEvent) -> Unit,
) {

    /** Updates the typed-confirm input shown next to the Destructive HITL confirmation row. */
    fun onTypedConfirmChange(value: String) {
        state.update { it.copy(composer = it.composer.copy(typedConfirm = value)) }
    }

    /**
     * Approves the tool the orchestrator is paused on. For a destructive tool
     * the approval is gated on the typed-confirm matching the canonical magic
     * word (`"yes"`, trimmed, case-insensitive) — the catalog
     * `HitlConfirmationCard` already disables the Allow CTA in that case, but
     * the gate is mirrored defensively so a programmatic caller cannot bypass
     * it. No-op when no tool is pending.
     */
    fun approveTool() {
        val pending = state.value.pending.tool ?: return
        if (pending.risk == ToolRisk.DESTRUCTIVE && !isTypedConfirmValid()) return
        val sessionId = state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        state.update {
            it.copy(
                pending = it.pending.copy(tool = null),
                composer = it.composer.copy(typedConfirm = ""),
                visual = ChatHomeUiState.Generating,
            )
        }
        scope.launch { submitApprovalDecision(sessionId, isApproved = true) }
    }

    /**
     * Rejects the tool the orchestrator is paused on. Persists a SYSTEM chat row
     * recording the denial so the user can see in-thread what happened. No-op
     * when no tool is pending.
     */
    fun rejectTool() {
        val pending = state.value.pending.tool ?: return
        val sessionId = state.value.thread.currentSessionId
        if (sessionId.isBlank()) return
        // Resuming the pipeline restarts orchestrator emission — keep the
        // surface in `Generating` until the next state (or a terminal
        // Completed / Error) settles it, otherwise the chat appears idle
        // while the agent is actively producing the denial follow-up.
        state.update {
            it.copy(
                pending = it.pending.copy(tool = null),
                composer = it.composer.copy(typedConfirm = ""),
                visual = ChatHomeUiState.Generating,
            )
        }
        scope.launch {
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
     * Submits the user's reply to the active clarification request. The pipeline
     * resumes immediately; the agent then publishes its next state through the
     * live collector. When the repository reports the reply was NOT consumed
     * (the request already resolved by timeout or an earlier answer), a SYSTEM
     * chat row records that the agent proceeded without it — silently dropping
     * the choice would misrepresent what the pipeline consumed.
     *
     * @param answer the user's reply text (option label or free-form).
     */
    fun submitClarificationReply(answer: String) {
        val pending = state.value.pending.clarification ?: return
        val sessionId = state.value.thread.currentSessionId
        // Allow an empty reply through — the orchestrator already accepts
        // `""` as the timeout fallback for free-form requests, so an
        // intentional blank submit is a legitimate "skip" affordance.
        val trimmed = answer.trim()
        state.update {
            it.copy(
                pending = it.pending.copy(clarification = null),
                visual = ChatHomeUiState.Generating,
            )
        }
        scope.launch {
            routePendingOutcome(submitClarificationAnswerUseCase(sessionId, pending.id, trimmed), sessionId) {
                // Neither a live deferred nor a parked record consumed the reply
                // — record in-thread that the agent proceeded without it, exactly
                // like the legacy undelivered path.
                state.update { it.copy(visual = it.restingVisual()) }
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

    /** Captures the orchestrator's pending approval and flips the UI to the HITL state. */
    fun handleWaitingForApproval(approval: AgentOrchestratorState.WaitingForApproval) {
        state.update {
            it.copy(
                pending = it.pending.copy(
                    tool = HitlPending(
                        toolName = approval.toolName,
                        arguments = approval.arguments,
                        risk = approval.risk,
                    ),
                ),
                composer = it.composer.copy(typedConfirm = ""),
                visual = ChatHomeUiState.HitlConfirm(approval.risk.toCatalogRisk()),
            )
        }
    }

    /**
     * Captures the orchestrator's pending clarification and flips the UI to the
     * Clarification state. No UI-side timeout runs against the card: the
     * repository owns the live waiting window, and on elapse the run parks
     * persistently so the card stays answerable through the parked-run path.
     *
     * @param request the pending clarification to surface.
     */
    fun handleAwaitingClarification(request: ClarificationRequest) {
        state.update {
            it.copy(
                pending = it.pending.copy(clarification = request),
                visual = ChatHomeUiState.Clarification,
            )
        }
    }

    /**
     * Routes the user's approve / deny decision through
     * [SubmitApprovalDecisionUseCase] (live gate first, then the parked record)
     * and folds the outcome onto the resume plumbing via [routePendingOutcome].
     */
    private suspend fun submitApprovalDecision(sessionId: String, isApproved: Boolean) {
        routePendingOutcome(submitApprovalDecisionUseCase(sessionId, isApproved), sessionId) {
            state.update { it.copy(visual = it.restingVisual()) }
        }
    }

    /**
     * Maps a [PendingSubmissionOutcome] from a parked approval / clarification
     * submission onto the shared resume plumbing: a resumed parked run attaches
     * exactly like a resumed interrupted run; `GraphChanged` / `Expired` surface
     * through [emitResumeFeedback] and settle the visual back to its resting
     * state so the chat is not stuck on `Generating`. The only caller-specific
     * behaviour is [onNothingPending] — the approval path just settles the
     * visual, while the clarification path also records an undelivered-reply
     * SYSTEM row.
     *
     * @param outcome The submission outcome to route.
     * @param sessionId The session whose run is being resumed.
     * @param onNothingPending Caller-specific handling when neither a live
     *   deferred nor a parked record consumed the submission.
     */
    private suspend fun routePendingOutcome(
        outcome: PendingSubmissionOutcome,
        sessionId: String,
        onNothingPending: suspend () -> Unit,
    ) {
        when (outcome) {
            PendingSubmissionOutcome.LiveResumed -> Unit
            PendingSubmissionOutcome.Resumed -> attachToLiveRun(sessionId, PipelineRunStatus.QUEUED)
            PendingSubmissionOutcome.GraphChanged -> {
                emitResumeFeedback(ResumeFeedbackEvent.GraphChanged)
                state.update { it.copy(visual = it.restingVisual()) }
            }
            PendingSubmissionOutcome.Expired -> {
                emitResumeFeedback(ResumeFeedbackEvent.Expired)
                state.update { it.copy(visual = it.restingVisual()) }
            }
            PendingSubmissionOutcome.NothingPending -> onNothingPending()
        }
    }

    /** Whether the current typed-confirm input satisfies the destructive HITL gate. */
    private fun isTypedConfirmValid(): Boolean =
        state.value.composer.typedConfirm.trim().equals(DESTRUCTIVE_TYPED_CONFIRM_WORD, ignoreCase = true)

    companion object {
        /**
         * Magic word the user must type to confirm a destructive tool call.
         * Mirrors the catalog `HitlConfirmationState.DESTRUCTIVE_CONFIRM_WORD`
         * but duplicated here so the gate stays free of `:catalog` imports.
         */
        const val DESTRUCTIVE_TYPED_CONFIRM_WORD: String = "yes"

        /** Template of the SYSTEM chat row persisted when the user rejects a tool call. `%s` is the tool name. */
        const val SYSTEM_MESSAGE_TOOL_DENIED: String = "Tool '%s' denied by user."

        /**
         * SYSTEM chat row persisted when the user's clarification reply was not
         * consumed by the pipeline (the request had already resolved — typically
         * the repository's timeout fired while a reattach-restored card was still
         * on screen).
         */
        const val SYSTEM_MESSAGE_CLARIFICATION_REPLY_NOT_DELIVERED: String =
            "Reply was not delivered — the clarification had already been resolved with a default answer."
    }
}
