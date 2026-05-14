package ai.agent.android.domain.services

import ai.agent.android.domain.models.ToolRisk

/**
 * Service to notify the user when the agent requires approval to execute an action.
 */
interface ApprovalNotifier {
    /**
     * Sends an approval request.
     *
     * The notification's channel, icon and title are derived from [risk]:
     * [ToolRisk.DESTRUCTIVE] routes through a dedicated high-importance channel
     * with a warning glyph so the user can distinguish hard-to-reverse actions
     * from reversible [ToolRisk.SENSITIVE] ones at a glance; [ToolRisk.READ_ONLY]
     * is only reached when the user has globally opted into "ask on every tool
     * call" and reuses the SENSITIVE channel.
     *
     * @param sessionId The ID of the session that triggered the request.
     * @param toolName The name of the tool.
     * @param arguments The arguments passed to the tool.
     * @param risk Risk classification of the tool, used to pick channel / icon / copy.
     */
    fun sendApprovalRequest(sessionId: String, toolName: String, arguments: String, risk: ToolRisk)
}
