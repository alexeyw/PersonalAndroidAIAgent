package ai.agent.android.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.lastOrNull
import timber.log.Timber
import java.util.UUID

/**
 * Worker responsible for executing delayed agent tasks in the background.
 *
 * It initializes an instance of the agent orchestrator, passes the stored prompt,
 * and waits for completion.
 */
@HiltWorker
class AgentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val agentOrchestratorUseCase: AgentOrchestratorUseCase
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PROMPT = "agent_prompt"
    }

    override suspend fun doWork(): Result {
        val prompt = inputData.getString(KEY_PROMPT)
        
        if (prompt.isNullOrBlank()) {
            Timber.e("AgentWorker failed: Prompt is null or empty.")
            return Result.failure()
        }

        return try {
            Timber.d("AgentWorker starting execution for prompt: \$prompt")
            
            // Generate a unique session ID for this background execution
            val sessionId = "worker-\${UUID.randomUUID()}"
            
            // Wait until the orchestration completes (we consume the flow)
            val finalState = agentOrchestratorUseCase(sessionId, prompt).lastOrNull()
            
            Timber.d("AgentWorker completed with state: \$finalState")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "AgentWorker encountered an error.")
            Result.retry()
        }
    }
}
