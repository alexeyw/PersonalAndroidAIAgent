package app.knotwork.android.domain.services

/**
 * Service to notify the user when a parked run waits for an answer to a
 * clarifying question.
 *
 * Counterpart of [ApprovalNotifier] for the clarification gate. Only the
 * persistent waiting phase posts a notification — the live in-process phase
 * renders an in-chat card and needs none. The notification carries no answer
 * actions (a clarification expects arbitrary input); it deep-links into the
 * chat session where the persisted question is re-rendered for answering.
 */
interface ClarificationNotifier {

    /**
     * Sends the persistent-phase clarification notification for a parked run.
     *
     * Posted when the live in-process waiting phase times out and the run
     * parks on its pending-interaction record: the notification must outlive
     * the engine coroutine (ongoing, re-posted on dismissal) because it is
     * the user's primary path back to the parked run.
     *
     * @param runId Id of the parked run the answer must address.
     * @param sessionId The ID of the session that asked the question.
     * @param question The clarifying question awaiting an answer.
     */
    fun sendPersistentClarificationRequest(runId: String, sessionId: String, question: String)

    /**
     * Removes the clarification notification of [sessionId], if any is
     * showing. Called when the pending question is settled from the chat or
     * expired by the approval-window pass.
     *
     * @param sessionId The session whose clarification notification to remove.
     */
    fun cancelClarificationNotification(sessionId: String)
}
