package ai.agent.android.domain.models

/**
 * Represents the current state of the Agent Orchestrator during the ReAct cycle.
 */
sealed interface AgentOrchestratorState {
    /**
     * The agent is doing nothing.
     */
    data object Idle : AgentOrchestratorState

    /**
     * Initializing the orchestrator, preparing context and tools.
     */
    data object Loading : AgentOrchestratorState

    /**
     * The agent is generating a thought or answering the user.
     *
     * @property partialText The generated text so far.
     */
    data class Thinking(val partialText: String) : AgentOrchestratorState

    /**
     * The agent decided to execute a tool.
     *
     * @property toolName The name of the tool being executed.
     * @property arguments The arguments passed to the tool.
     */
    data class ExecutingTool(val toolName: String, val arguments: String) : AgentOrchestratorState

    /**
     * The agent wants to execute a tool, but user confirmation is required.
     *
     * @property toolName The name of the tool.
     * @property arguments The arguments for the tool.
     */
    data class WaitingForApproval(val toolName: String, val arguments: String) : AgentOrchestratorState

    /**
     * The tool execution finished.
     *
     * @property toolName The name of the tool.
     * @property result The result observation from the tool.
     */
    data class ObservationResult(val toolName: String, val result: String) : AgentOrchestratorState

    /**
     * The agent is answering the user directly.
     *
     * @property partialText The text response generated so far.
     */
    data class Answering(val partialText: String) : AgentOrchestratorState

    /**
     * The orchestration cycle is fully completed.
     *
     * @property finalResponse The complete answer from the agent.
     */
    data class Completed(val finalResponse: String) : AgentOrchestratorState

    /**
     * An error occurred during the orchestration.
     *
     * @property message The error message.
     */
    data class Error(val message: String) : AgentOrchestratorState

    /**
     * Indicates the current pipeline stage (node) the agent is executing.
     *
     * @property nodeName The name or type of the node.
     */
    data class PipelineStage(val nodeName: String) : AgentOrchestratorState
}
