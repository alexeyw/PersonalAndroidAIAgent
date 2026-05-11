package ai.agent.android.data.services

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages the idle timeout logic for the AI Agent.
 * Listens to the [AgentOrchestratorState] and triggers a safe unload of the
 * [LlmInferenceEngine] if the agent remains idle for a specified duration.
 *
 * @property scope The [CoroutineScope] used for launching the idle timer and collecting state.
 * @property engine The [LlmInferenceEngine] to unload when idle.
 * @property agentState The [StateFlow] representing the current state of the agent.
 * @property idleTimeoutMs The duration in milliseconds before the agent is considered idle.
 */
class AgentIdleManager(
    private val scope: CoroutineScope,
    private val engine: LlmInferenceEngine,
    private val agentState: StateFlow<AgentOrchestratorState>,
    private val idleTimeoutMs: Long = 5 * 60 * 1000L, // 5 minutes default
) {
    private var idleJob: Job? = null

    // A coroutine-safe mutex guards the engine unload path. Using `synchronized(engine)` from
    // inside a coroutine would block the dispatcher thread without participating in
    // structured concurrency or cancellation; `Mutex.withLock` suspends instead and lets
    // the surrounding scope cancel cleanly.
    private val engineMutex = Mutex()

    /**
     * Starts observing the agent state to manage the idle timer.
     */
    fun startObserving() {
        scope.launch {
            agentState.collectLatest { state ->
                handleStateChange(state)
            }
        }
    }

    private fun handleStateChange(state: AgentOrchestratorState) {
        val isIdle = state is AgentOrchestratorState.Idle ||
            state is AgentOrchestratorState.Completed ||
            state is AgentOrchestratorState.Error

        if (isIdle) {
            startIdleTimer()
        } else {
            cancelIdleTimer()
        }
    }

    private fun startIdleTimer() {
        cancelIdleTimer()
        idleJob = scope.launch {
            delay(idleTimeoutMs)
            engineMutex.withLock {
                if (engine.isInitialized) {
                    engine.unload()
                }
            }
        }
    }

    private fun cancelIdleTimer() {
        idleJob?.cancel()
        idleJob = null
    }
}
