package ai.agent.android.presentation.ui.taskmonitor

import ai.agent.android.domain.engine.TaskQueueManager
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for monitoring active chat sessions and WorkManager background tasks.
 * Combines data from ChatRepository and WorkManager into a unified UI state.
 */
@HiltViewModel
class TaskMonitorViewModel @Inject constructor(
    chatRepository: ChatRepository,
    private val workManager: WorkManager,
    private val settingsRepository: SettingsRepository,
    private val taskQueueManager: TaskQueueManager
) : ViewModel() {

    private val _filter = MutableStateFlow(TaskFilterType.ACTIVE)

    private val workInfosFlow = workManager.getWorkInfosFlow(
        WorkQuery.Builder.fromStates(
            listOf(
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING,
                WorkInfo.State.SUCCEEDED,
                WorkInfo.State.FAILED,
                WorkInfo.State.BLOCKED,
                WorkInfo.State.CANCELLED
            )
        ).build()
    )

    /**
     * The unified UI state containing filtered tasks and loading status.
     */
    val uiState: StateFlow<TaskMonitorState> = combine(
        chatRepository.getSessionsFlow(),
        workInfosFlow,
        taskQueueManager.activeSessionsState,
        _filter
    ) { sessions, workInfos, activeSessionsMap, filter ->
        val sessionTasks = sessions.mapNotNull { session ->
            val orchestratorState = activeSessionsMap[session.id] ?: AgentOrchestratorState.Idle
            
            // Map Orchestrator State to TaskStatus
            val status = when (orchestratorState) {
                is AgentOrchestratorState.Idle -> TaskStatus.COMPLETED
                is AgentOrchestratorState.Completed -> TaskStatus.COMPLETED
                is AgentOrchestratorState.Error -> TaskStatus.FAILED
                else -> TaskStatus.RUNNING
            }
            
            // Map Orchestrator State to Pipeline Stage
            val stage = when (orchestratorState) {
                is AgentOrchestratorState.PipelineStage -> orchestratorState.stepInfo.nodeName
                is AgentOrchestratorState.Thinking -> "Thinking"
                is AgentOrchestratorState.ExecutingTool -> "Tool Execution"
                is AgentOrchestratorState.WaitingForApproval -> "Waiting Approval"
                is AgentOrchestratorState.Loading -> "Loading"
                is AgentOrchestratorState.Answering -> "Answering"
                else -> null
            }

            TaskItem(
                id = session.id,
                title = "Chat Session: ${session.name}",
                status = status,
                progress = null,
                type = TaskType.SESSION,
                pipelineStage = stage
            )
        }

        val workTasks = workInfos.map { info ->
            val stage = info.progress.getString("current_stage")
            val isPassedOutput = stage == "OUTPUT" || stage == "COMPLETED"

            TaskItem(
                id = info.id.toString(),
                title = "Background Task (${info.tags.firstOrNull() ?: "AgentWorker"})",
                status = when {
                    isPassedOutput -> TaskStatus.COMPLETED
                    info.state == WorkInfo.State.RUNNING -> TaskStatus.RUNNING
                    info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.BLOCKED -> TaskStatus.QUEUED
                    info.state == WorkInfo.State.FAILED -> TaskStatus.FAILED
                    info.state == WorkInfo.State.SUCCEEDED || info.state == WorkInfo.State.CANCELLED -> TaskStatus.COMPLETED
                    else -> TaskStatus.QUEUED
                },
                progress = if (info.state == WorkInfo.State.RUNNING) -1f else 1f,
                type = TaskType.BACKGROUND_WORK,
                pipelineStage = stage
            )
        }

        val allTasks = sessionTasks + workTasks

        val filteredTasks = when (filter) {
            TaskFilterType.ALL -> allTasks
            TaskFilterType.ACTIVE -> allTasks.filter { it.status == TaskStatus.RUNNING }
            TaskFilterType.BACKGROUND -> allTasks.filter { it.type == TaskType.BACKGROUND_WORK && it.status == TaskStatus.QUEUED }
            TaskFilterType.COMPLETED -> allTasks.filter { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
        }

        TaskMonitorState(
            tasks = filteredTasks,
            filter = filter,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskMonitorState()
    )

    /**
     * Updates the current filter for the task list.
     *
     * @param newFilter The new [TaskFilterType] to apply.
     */
    fun onFilterChanged(newFilter: TaskFilterType) {
        _filter.value = newFilter
    }

    /**
     * Cancels a specific WorkManager task by its ID.
     *
     * @param taskId The UUID string of the work request.
     */
    fun onCancelTaskClicked(taskId: String) {
        try {
            workManager.cancelWorkById(UUID.fromString(taskId))
        } catch (e: IllegalArgumentException) {
            // Ignored: Invalid UUID format
        }
    }

    /**
     * Sets the current chat session before navigating.
     *
     * @param sessionId The ID of the chat session to open.
     * @param onComplete Callback invoked when the session is set.
     */
    fun onOpenChatClicked(sessionId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setCurrentChatSessionId(sessionId)
            onComplete()
        }
    }
}
