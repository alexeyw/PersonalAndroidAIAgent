package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.HardCeilingBreach

/**
 * Service to notify the user when a run has spent one of its ceilings and is
 * waiting to be told whether it may carry on.
 *
 * Third member of the parked-run notifier family, beside [ApprovalNotifier] and
 * [ClarificationNotifier], and closest to the second: the notification carries
 * no inline Continue / Stop actions and instead deep-links into the chat, where
 * the card can state which limit bound, how much the run spent, and what
 * continuing costs. Two buttons in the shade would ask the user to authorise
 * more work without showing them how much work has already happened, which is
 * the only fact the decision turns on.
 *
 * Unlike the other two there is no live in-process phase to precede it: nothing
 * is in flight when a ceiling binds, so the run parks immediately and this is
 * the only notification it ever posts. It is suppressed while the user is
 * looking at the session that raised it — the in-chat card is already on screen
 * — on the same terms as the approval notification.
 */
interface CeilingNotifier {

    /**
     * Posts the "run reached its limit" notification for a parked run.
     *
     * Ongoing and re-posted on dismissal, like the other persistent-phase
     * notifications: it is the user's only path back to a run that is waiting
     * and will otherwise sit until its window expires.
     *
     * @param runId Id of the parked run the decision must address.
     * @param sessionId Id of the chat session the run belongs to. Also the
     *   suppression key — nothing is posted while this session is on screen.
     * @param breach Which ceiling bound and by how much. Passed as the three
     *   facts rather than a rendered sentence because the sentence is localised
     *   copy, and the caller is the engine — resolving user-facing strings there
     *   is what put four different wordings of one event into the codebase
     *   before `RunTerminationCopy` gathered them.
     */
    fun sendCeilingPauseRequest(runId: String, sessionId: String, breach: HardCeilingBreach)

    /**
     * Removes the ceiling notification of [sessionId], if any is showing.
     * Called when the pause is answered from the chat, or settled by the
     * approval-window pass.
     *
     * @param sessionId The session whose ceiling notification to remove.
     */
    fun cancelCeilingNotification(sessionId: String)
}
