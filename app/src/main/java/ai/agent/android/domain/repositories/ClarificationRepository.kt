package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.ClarificationRequest
import kotlinx.coroutines.flow.Flow

/**
 * Bridges the agent's pipeline (which needs to ask the user a question and wait for a
 * reply) and the UI layer (which renders the question and forwards the user's answer).
 *
 * Lifecycle of a single clarification round:
 * 1. A node executor calls [requestAnswer] with a [ClarificationRequest]; the call
 *    suspends.
 * 2. The repository publishes the request via [pendingRequest] so the UI can render it.
 * 3. The UI calls [submitClarification] with the user's reply; [requestAnswer] resumes
 *    and returns that reply.
 * 4. If no reply arrives within [ClarificationRequest.timeoutMs], a default answer is
 *    used (first option, or empty string for free-form requests) and the timeout is
 *    logged.
 *
 * Implementations must be thread-safe — multiple concurrent pipelines may issue
 * requests, and the UI may submit answers from arbitrary dispatchers.
 */
interface ClarificationRepository {
    /**
     * Stream of the currently pending clarification request, or `null` when the agent
     * is not waiting on the user. The UI subscribes to render/dismiss the clarification
     * card.
     */
    val pendingRequest: Flow<ClarificationRequest?>

    /**
     * Publishes [request] and suspends until the user submits an answer via
     * [submitClarification] or until [ClarificationRequest.timeoutMs] elapses.
     *
     * On timeout the returned value is:
     * - `request.options.first()` when [ClarificationRequest.options] is non-null and
     *   non-empty;
     * - an empty string otherwise (free-form input or empty options list).
     *
     * @param request The clarification to ask the user.
     * @return The user's answer, or the default value if the request timed out.
     */
    suspend fun requestAnswer(request: ClarificationRequest): String

    /**
     * Forwards the user's reply to the suspended [requestAnswer] call identified by
     * [requestId].
     *
     * If [requestId] does not correspond to an active request (e.g. the request has
     * already been resolved by a timeout, or the id is unknown) the call is a no-op.
     *
     * @param requestId The id of the [ClarificationRequest] being answered.
     * @param answer The user's reply text.
     */
    suspend fun submitClarification(requestId: String, answer: String)
}
