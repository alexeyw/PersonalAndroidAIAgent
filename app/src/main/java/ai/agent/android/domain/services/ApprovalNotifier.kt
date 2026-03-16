package ai.agent.android.domain.services

/**
 * Service to notify the user when the agent requires approval to execute an action.
 */
interface ApprovalNotifier {
    /**
     * Sends an approval request.
     *
     * @param sessionId The ID of the session that triggered the request.
     * @param toolName The name of the tool.
     * @param arguments The arguments passed to the tool.
     */
    fun sendApprovalRequest(sessionId: String, toolName: String, arguments: String)
}
