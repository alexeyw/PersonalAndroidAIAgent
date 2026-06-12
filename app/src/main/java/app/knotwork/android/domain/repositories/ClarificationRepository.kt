package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.ClarificationOutcome
import app.knotwork.android.domain.models.ClarificationRequest
import kotlinx.coroutines.flow.Flow

/**
 * Bridges the agent's pipeline (which needs to ask the user a question and wait for a
 * reply) and the UI layer (which renders the question and forwards the user's answer).
 *
 * Lifecycle of a single clarification round:
 * 1. A node executor calls [requestAnswer] with a [ClarificationRequest]; the call
 *    suspends.
 * 2. The repository appends the request to [pendingRequests] so the UI can render it.
 * 3. The UI calls [submitClarification] with the user's reply; [requestAnswer] resumes
 *    and returns that reply.
 * 4. If no reply arrives within [ClarificationRequest.timeoutMs], [requestAnswer]
 *    returns [ClarificationOutcome.TimedOut] and the caller decides what the elapsed
 *    live phase means (park the run persistently, or fall back to a default answer).
 *
 * Implementations must be thread-safe and must keep every pending request addressable
 * via [pendingRequests] — multiple concurrent pipelines may issue overlapping requests
 * and each must remain answerable, so a newer request must NOT supersede or hide an
 * older one. The UI may submit answers from arbitrary dispatchers.
 */
interface ClarificationRepository {
    /**
     * Stream of all currently pending clarification requests. Empty when the agent is
     * not waiting on the user. Each new request is appended; resolving a request (by
     * answer or timeout) removes it. Order within the list is the order in which the
     * requests were issued.
     *
     * The UI subscribes to render and dismiss clarification cards; each request is
     * uniquely addressable by [ClarificationRequest.id] when calling
     * [submitClarification].
     */
    val pendingRequests: Flow<List<ClarificationRequest>>

    /**
     * Publishes [request] and suspends until the user submits an answer via
     * [submitClarification] or until [ClarificationRequest.timeoutMs] elapses.
     *
     * @param request The clarification to ask the user.
     * @return [ClarificationOutcome.Answered] carrying the user's reply, or
     *   [ClarificationOutcome.TimedOut] when the live waiting window elapsed
     *   unanswered — the caller owns the timeout policy.
     */
    suspend fun requestAnswer(request: ClarificationRequest): ClarificationOutcome

    /**
     * Forwards the user's reply to the suspended [requestAnswer] call identified by
     * [requestId].
     *
     * Returns `true` only if this call actually delivered [answer] to the suspended
     * coroutine. Returns `false` when [requestId] is unknown, was already resolved by
     * a timeout, or was already resolved by a previous reply — i.e. whenever the
     * agent will NOT consume [answer]. Callers (typically the UI) must use the
     * return value to avoid showing the user a "your reply was used" message when
     * the pipeline has already moved on with a different value.
     *
     * @param requestId The id of the [ClarificationRequest] being answered.
     * @param answer The user's reply text.
     * @return `true` if the suspended pipeline coroutine received [answer], `false`
     *   if the request was no longer active (unknown id, timed out, or already answered).
     */
    suspend fun submitClarification(requestId: String, answer: String): Boolean
}
