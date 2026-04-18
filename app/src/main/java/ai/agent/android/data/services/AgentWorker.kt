package ai.agent.android.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import ai.agent.android.domain.models.AgentOrchestratorState
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
        const val KEY_CURRENT_STAGE = "current_stage"
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
            
            // Observe the state and update WorkManager progress
            var finalState: AgentOrchestratorState? = null
            agentOrchestratorUseCase(sessionId, prompt).collect { state ->
                finalState = state
                if (state is AgentOrchestratorState.PipelineStage) {
                    setProgress(Data.Builder().putString(KEY_CURRENT_STAGE, state.stepInfo.nodeName).build())
                } else if (state is AgentOrchestratorState.Completed) {
                    setProgress(Data.Builder().putString(KEY_CURRENT_STAGE, "COMPLETED").build())
                } else if (state is AgentOrchestratorState.Error) {
                    setProgress(Data.Builder().putString(KEY_CURRENT_STAGE, "ERROR").build())
                }
            }
            
            Timber.d("AgentWorker completed with state: \$finalState")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "AgentWorker encountered an error.")
            Result.retry()
        }
    }
}
