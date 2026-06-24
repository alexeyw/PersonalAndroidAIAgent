package app.knotwork.android.presentation.ui.triggers

import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.usecases.SyncTriggersUseCase
import app.knotwork.design.screens.triggers.TriggerConditionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TriggersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var triggerRepository: TriggerRepository
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var syncTriggers: SyncTriggersUseCase

    private val daily = trigger(
        id = "morning",
        condition = TriggerCondition.DailySchedule(hour = 8, minute = 0),
        pipelineId = "p1",
        enabled = true,
    )
    private val unbound = trigger(
        id = "backup",
        condition = TriggerCondition.IntervalSchedule(intervalMinutes = 360L),
        pipelineId = null,
        enabled = false,
    )

    private fun trigger(id: String, condition: TriggerCondition, pipelineId: String?, enabled: Boolean) = Trigger(
        id = id,
        name = id,
        condition = condition,
        pipelineId = pipelineId,
        prompt = "do it",
        enabled = enabled,
        armed = true,
        createdAt = 10L,
        lastFiredAt = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        triggerRepository = mockk(relaxed = true) {
            every { observeTriggers() } returns flowOf(listOf(daily, unbound))
        }
        pipelineRepository = mockk(relaxed = true) {
            every { getAllPipelines() } returns flowOf(listOf(PipelineGraph(id = "p1", name = "Daily briefing")))
        }
        syncTriggers = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TriggersViewModel(triggerRepository, pipelineRepository, syncTriggers)

    @Test
    fun `given repositories when initialised then loads triggers and pipelines`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(daily, unbound), state.triggers)
        assertEquals(1, state.pipelines.size)
        assertEquals(1, state.activeCount)
    }

    @Test
    fun `given a load failure when initialised then surfaces an error`() = runTest(testDispatcher) {
        every { triggerRepository.observeTriggers() } returns flow { throw RuntimeException("boom") }
        val vm = viewModel()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `given the new-trigger action when invoked then opens a fresh draft`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.openNewTrigger()

        val draft = vm.uiState.value.editor
        assertNotNull(draft)
        assertNull(draft?.id)
        assertNull(draft?.pipelineId)
        assertEquals(TriggerConditionType.Daily, draft?.type)
    }

    @Test
    fun `given a valid draft when saved then persists the trigger and re-syncs the runtime`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.openNewTrigger()
            vm.onNameChange("Morning")
            vm.onTypeChange(TriggerConditionType.Charging)
            vm.onPipelineSelect("p1")
            vm.onPromptChange("summarise")

            val saved = slot<Trigger>()
            coEvery { triggerRepository.saveTrigger(capture(saved)) } returns Unit

            vm.saveEditor()
            advanceUntilIdle()

            assertEquals("Morning", saved.captured.name)
            assertEquals(TriggerCondition.Charging, saved.captured.condition)
            assertEquals("p1", saved.captured.pipelineId)
            coVerify(exactly = 1) { syncTriggers() }
            assertNull(vm.uiState.value.editor)
        }

    @Test
    fun `given a blank name when saved then does nothing`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.openNewTrigger() // name is blank by default

        vm.saveEditor()
        advanceUntilIdle()

        coVerify(exactly = 0) { triggerRepository.saveTrigger(any()) }
        assertNotNull(vm.uiState.value.editor)
    }

    @Test
    fun `given a custom interval below the floor when editing then save is disabled`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.openNewTrigger()
        vm.onNameChange("Too fast")
        vm.onTypeChange(TriggerConditionType.Interval)
        vm.onIntervalCustomToggle()
        vm.onIntervalCustomAmountChange("5")

        val draft = vm.uiState.value.editor
        assertNotNull(draft)
        assertTrue(draft!!.intervalError)
        assertFalse(draft.canSave)
    }

    @Test
    fun `given a preset interval above the floor when editing then resolves the condition`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.openNewTrigger()
        vm.onNameChange("Every six hours")
        vm.onTypeChange(TriggerConditionType.Interval)
        vm.onIntervalPreset(360L)
        vm.onPipelineSelect("p1")

        val saved = slot<Trigger>()
        coEvery { triggerRepository.saveTrigger(capture(saved)) } returns Unit
        vm.saveEditor()
        advanceUntilIdle()

        assertEquals(TriggerCondition.IntervalSchedule(360L), saved.captured.condition)
    }

    @Test
    fun `given an existing trigger when editing then the draft round-trips the condition`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.openEditTrigger("backup")

        val draft = vm.uiState.value.editor
        assertNotNull(draft)
        assertEquals(TriggerConditionType.Interval, draft?.type)
        assertEquals(360L, draft?.intervalPresetMinutes)
        assertFalse(draft!!.intervalCustom)
    }

    @Test
    fun `given a trigger row when toggled then flips enabled and re-syncs`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleEnabled("morning")
        advanceUntilIdle()

        coVerify(exactly = 1) { triggerRepository.setEnabled("morning", false) }
        coVerify(exactly = 1) { syncTriggers() }
    }

    @Test
    fun `given a delete request when confirmed then deletes and re-syncs`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.requestDelete("morning")
        assertNotNull(vm.uiState.value.deleteTarget)

        vm.confirmDelete()
        advanceUntilIdle()

        coVerify(exactly = 1) { triggerRepository.deleteTrigger("morning") }
        coVerify(exactly = 1) { syncTriggers() }
        assertNull(vm.uiState.value.deleteTarget)
    }

    @Test
    fun `given a delete request when cancelled then nothing is deleted`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.requestDelete("morning")
        vm.cancelDelete()
        advanceUntilIdle()

        coVerify(exactly = 0) { triggerRepository.deleteTrigger(any()) }
        assertNull(vm.uiState.value.deleteTarget)
    }
}
