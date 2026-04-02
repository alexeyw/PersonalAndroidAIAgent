package ai.agent.android.presentation.ui.taskmonitor

import ai.agent.android.domain.repositories.ChatRepository
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
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for monitoring active chat sessions and WorkManager background tasks.
 * Combines data from ChatRepository and WorkManager into a unified UI state.
 */
@HiltViewModel
class TaskMonitorViewModel @Inject constructor(
    chatRepository: ChatRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _filter = MutableStateFlow(TaskFilterType.ALL)

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
        _filter
    ) { sessions, workInfos, filter ->
        val sessionTasks = sessions.map {
            TaskItem(
                id = it.id,
                title = "Chat Session: ${it.name}",
                status = TaskStatus.RUNNING, // Active sessions are considered running from a user's perspective
                progress = null,
                type = TaskType.SESSION
            )
        }

        val workTasks = workInfos.map { info ->
            TaskItem(
                id = info.id.toString(),
                title = "Background Task (${info.tags.firstOrNull() ?: "AgentWorker"})",
                status = when (info.state) {
                    WorkInfo.State.RUNNING -> TaskStatus.RUNNING
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> TaskStatus.QUEUED
                    WorkInfo.State.FAILED -> TaskStatus.FAILED
                    WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> TaskStatus.COMPLETED
                },
                progress = if (info.state == WorkInfo.State.RUNNING) -1f else 1f,
                type = TaskType.BACKGROUND_WORK
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
}
