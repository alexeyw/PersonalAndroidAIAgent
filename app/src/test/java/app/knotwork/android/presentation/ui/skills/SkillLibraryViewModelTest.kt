package app.knotwork.android.presentation.ui.skills

import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.Skill
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.prompt.PromptVariableProvider
import app.knotwork.android.domain.repositories.SkillRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.usecases.FindPipelinesUsingSkillUseCase
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.screens.skills.SkillLibraryTab
import app.knotwork.design.screens.skills.SkillToolMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SkillLibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var skillRepository: SkillRepository
    private lateinit var toolRepository: ToolRepository
    private lateinit var findPipelinesUsingSkillUseCase: FindPipelinesUsingSkillUseCase

    private val bundled = skill("summarizer", toolAllowlist = emptyList(), isBundled = true)
    private val user = skill("standup", toolAllowlist = listOf("write_file"), isBundled = false)

    private fun skill(id: String, toolAllowlist: List<String>?, isBundled: Boolean) = Skill(
        id = id,
        name = id,
        description = "desc",
        instruction = "do it",
        toolAllowlist = toolAllowlist,
        contextConfig = NodeContextConfig.ALL_ENABLED,
        isBundled = isBundled,
        createdAt = 10L,
        updatedAt = 20L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        skillRepository = mockk(relaxed = true) {
            every { getBundledSkills() } returns flowOf(listOf(bundled))
            every { getUserSkills() } returns flowOf(listOf(user))
        }
        toolRepository = mockk {
            coEvery { getAvailableTools() } returns listOf(
                AgentTool(
                    name = "write_file",
                    description = "Write a file",
                    parameters = "{}",
                    risk = ToolRisk.DESTRUCTIVE,
                ),
                AgentTool(name = "search_tool", description = "Search", parameters = "{}", risk = ToolRisk.READ_ONLY),
            )
        }
        findPipelinesUsingSkillUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SkillLibraryViewModel {
        val provider = mockk<PromptVariableProvider> { every { key() } returns "DATE" }
        return SkillLibraryViewModel(
            skillRepository = skillRepository,
            toolRepository = toolRepository,
            findPipelinesUsingSkillUseCase = findPipelinesUsingSkillUseCase,
            promptVariableProviders = setOf(provider),
        )
    }

    @Test
    fun `given repositories when initialised then bundled and user skills load`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(bundled), state.bundledSkills)
        assertEquals(listOf(user), state.userSkills)
        assertEquals(listOf("\$DATE"), state.availableVariables)
        assertEquals(2, state.availableTools.size)
        assertEquals(Risk.Destructive, state.availableTools.first { it.id == "write_file" }.risk)
    }

    @Test
    fun `given a tab when selected then it updates`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.selectTab(SkillLibraryTab.Mine)
        assertEquals(SkillLibraryTab.Mine, vm.uiState.value.tab)
    }

    @Test
    fun `given new-skill when opened then editor starts in All-tools mode`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.openNewSkill()
        val editor = vm.uiState.value.editor
        assertNotNull(editor)
        assertNull(editor!!.id)
        assertEquals(SkillToolMode.All, editor.toolMode)
    }

    @Test
    fun `given an existing user skill when edited then tool mode is derived as Restrict`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.openEditSkill("standup")
        val editor = vm.uiState.value.editor!!
        assertEquals("standup", editor.id)
        assertEquals(SkillToolMode.Restrict, editor.toolMode)
        assertEquals(setOf("write_file"), editor.selectedToolIds)
    }

    @Test
    fun `given an all-tools draft when saved then the skill has a null allowlist`() = runTest {
        val saved = slot<Skill>()
        coEvery { skillRepository.saveUserSkill(capture(saved)) } returns Unit
        val vm = createViewModel()
        advanceUntilIdle()
        vm.openNewSkill()
        vm.onEditorNameChange("My skill")
        vm.onEditorInstructionChange("Do the thing.")
        vm.saveEditor()
        advanceUntilIdle()
        assertNull(saved.captured.toolAllowlist)
        assertEquals("My skill", saved.captured.name)
        assertNull(vm.uiState.value.editor)
    }

    @Test
    fun `given Restrict with selections when saved then the allowlist is the subset`() = runTest {
        val saved = slot<Skill>()
        coEvery { skillRepository.saveUserSkill(capture(saved)) } returns Unit
        val vm = createViewModel()
        advanceUntilIdle()
        vm.openNewSkill()
        vm.onEditorNameChange("Picky")
        vm.onEditorInstructionChange("Use one tool.")
        vm.onEditorToolModeChange(SkillToolMode.Restrict)
        vm.onEditorToolToggle("write_file")
        vm.saveEditor()
        advanceUntilIdle()
        assertEquals(listOf("write_file"), saved.captured.toolAllowlist)
    }

    @Test
    fun `given No-tools mode when saved then the allowlist is empty not null`() = runTest {
        val saved = slot<Skill>()
        coEvery { skillRepository.saveUserSkill(capture(saved)) } returns Unit
        val vm = createViewModel()
        advanceUntilIdle()
        vm.openNewSkill()
        vm.onEditorNameChange("Quiet")
        vm.onEditorInstructionChange("No tools.")
        vm.onEditorToolModeChange(SkillToolMode.None)
        vm.saveEditor()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), saved.captured.toolAllowlist)
    }

    @Test
    fun `given a blank draft when saved then nothing is persisted`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.openNewSkill()
        vm.saveEditor()
        advanceUntilIdle()
        coVerify(exactly = 0) { skillRepository.saveUserSkill(any()) }
        assertNotNull(vm.uiState.value.editor)
    }

    @Test
    fun `given a context toggle when flipped then the config field flips`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.openNewSkill()
        // ALL_ENABLED → chatHistory starts true; toggling 'chat' turns it off.
        vm.onEditorContextToggle("chat")
        assertFalse(vm.uiState.value.editor!!.contextConfig.chatHistory)
    }

    @Test
    fun `given a duplicate request when invoked then the repository duplicates`() = runTest {
        coEvery { skillRepository.duplicateSkill("summarizer") } returns null
        val vm = createViewModel()
        advanceUntilIdle()
        vm.duplicateSkill("summarizer")
        advanceUntilIdle()
        coVerify { skillRepository.duplicateSkill("summarizer") }
    }

    @Test
    fun `given a delete request when confirmed then the user skill is removed`() = runTest {
        coEvery { findPipelinesUsingSkillUseCase("standup") } returns emptyList()
        val vm = createViewModel()
        advanceUntilIdle()
        vm.requestDelete("standup")
        advanceUntilIdle()
        val target = vm.uiState.value.deleteTarget
        assertNotNull(target)
        assertEquals("standup", target!!.name)
        assertTrue(target.dependentPipelineNames.isEmpty())
        vm.confirmDelete()
        advanceUntilIdle()
        coVerify { skillRepository.deleteUserSkill("standup") }
        assertNull(vm.uiState.value.deleteTarget)
    }

    @Test
    fun `given delete dependents when resolved then they surface in the target`() = runTest {
        coEvery { findPipelinesUsingSkillUseCase("standup") } returns
            listOf(PipelineGraph(id = "p1", name = "weekly-report"))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.requestDelete("standup")
        advanceUntilIdle()
        assertEquals(listOf("weekly-report"), vm.uiState.value.deleteTarget!!.dependentPipelineNames)
    }
}
