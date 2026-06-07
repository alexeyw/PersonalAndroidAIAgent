package app.knotwork.android.presentation.ui.taskmonitor

import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TaskMonitorViewModelTest {

    private lateinit var viewModel: TaskMonitorViewModel
    private val chatRepository: ChatRepository = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val taskQueueManager: TaskQueueManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val sessions = listOf(
            ChatSession("session1", "First Session", 1000L),
            ChatSession("session2", "Second Session", 2000L),
        )

        val workInfo1 = mockk<WorkInfo>(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { tags } returns setOf("AgentWorker")
            every { state } returns WorkInfo.State.RUNNING
        }
        val workInfo2 = mockk<WorkInfo>(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { tags } returns setOf("OtherWorker")
            every { state } returns WorkInfo.State.SUCCEEDED
        }

        every { chatRepository.getSessionsFlow() } returns flowOf(sessions)
        every { workManager.getWorkInfosFlow(any()) } returns flowOf(listOf(workInfo1, workInfo2))

        val activeMap = mapOf(
            "session1" to AgentOrchestratorState.PipelineStage(
                AgentOrchestratorState.PipelineStepInfo(1, 3, "LITE_RT"),
            ),
            "session2" to AgentOrchestratorState.Idle,
        )
        every { taskQueueManager.activeSessionsState } returns kotlinx.coroutines.flow.MutableStateFlow(activeMap)

        viewModel = TaskMonitorViewModel(chatRepository, workManager, settingsRepository, taskQueueManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state shows active tasks by default`() = runTest(testDispatcher) {
        val uiState = viewModel.uiState.first { !it.isLoading }

        assertEquals(TaskFilterType.ACTIVE, uiState.filter)
        assertEquals(2, uiState.tasks.size) // 1 active session + 1 running task

        val sessions = uiState.tasks.filter { it.type == TaskType.SESSION }
        assertEquals(1, sessions.size)
        assertTrue(sessions.all { it.status == TaskStatus.RUNNING })

        val backgroundTasks = uiState.tasks.filter { it.type == TaskType.BACKGROUND_WORK }
        assertEquals(1, backgroundTasks.size)
        assertTrue(backgroundTasks.all { it.status == TaskStatus.RUNNING })
    }

    @Test
    fun `filter ACTIVE returns only running tasks and sessions`() = runTest(testDispatcher) {
        viewModel.onFilterChanged(TaskFilterType.ACTIVE)
        val uiState = viewModel.uiState.first { it.filter == TaskFilterType.ACTIVE }

        assertEquals(2, uiState.tasks.size) // 1 active session + 1 running task
        assertTrue(uiState.tasks.all { it.status == TaskStatus.RUNNING })
    }

    @Test
    fun `filter COMPLETED returns only completed tasks`() = runTest(testDispatcher) {
        viewModel.onFilterChanged(TaskFilterType.COMPLETED)
        val uiState = viewModel.uiState.first { it.filter == TaskFilterType.COMPLETED }

        assertEquals(2, uiState.tasks.size) // 1 idle session + 1 succeeded task
        assertTrue(uiState.tasks.all { it.status == TaskStatus.COMPLETED })
    }

    @Test
    fun `cancel task invokes work manager`() {
        val uuid = UUID.randomUUID()
        viewModel.onCancelTaskClicked(uuid.toString())

        verify { workManager.cancelWorkById(uuid) }
    }
}
