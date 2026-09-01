package app.knotwork.android.presentation.ui.automation

import app.knotwork.android.domain.models.ExternalAutomationJournalEntry
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationStatus
import app.knotwork.android.domain.models.ExternalAutomationTarget
import app.knotwork.android.domain.repositories.ExternalAutomationJournalRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.usecases.automation.BuildExternalAutomationJournalExportUseCase
import app.knotwork.android.domain.usecases.automation.ExportExternalAutomationJournalUseCase
import app.knotwork.android.presentation.ui.common.JournalExportEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies that the external-automation journal screen reports the contract's
 * posture and its request log faithfully.
 *
 * The posture assertions carry the weight here: the screen is what a user reads
 * to tell "nothing arrived" from "everything was refused", and each of the three
 * postures produces a different banner and a different fix.
 */
class ExternalAutomationJournalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settings: SettingsRepository
    private lateinit var pipelines: PipelineRepository
    private lateinit var journal: ExternalAutomationJournalRepository

    /**
     * Real export use case over the mocked store: the assertions are about the
     * document the screen hands out, which a mocked renderer could not verify.
     */
    private lateinit var exportJournal: ExportExternalAutomationJournalUseCase

    private val refusal = ExternalAutomationJournalEntry(
        id = "row-1",
        requestId = "tsk-1",
        receivedAt = 1_000L,
        action = "app.knotwork.android.action.RUN_PIPELINE",
        target = ExternalAutomationTarget.ByName("Expense report"),
        declaredReturnPackage = "net.dinglisch.android.taskerm",
        returnAction = "app.knotwork.android.action.RUN_RESULT",
        attestedSenderPackage = null,
        status = ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.TARGET_NOT_ALLOWED),
        runId = null,
        repeatCount = 7,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settings = mockk(relaxed = true)
        pipelines = mockk()
        journal = mockk(relaxed = true)
        every { settings.externalAutomationEnabled } returns flowOf(true)
        every { settings.externalAutomationPipelineId } returns flowOf("p1")
        every { pipelines.observePipelineNames() } returns flowOf(mapOf("p1" to "Morning digest"))
        every { journal.observeAll() } returns flowOf(emptyList())
        coEvery { journal.readAll() } returns emptyList()
        exportJournal = ExportExternalAutomationJournalUseCase(journal, BuildExternalAutomationJournalExportUseCase())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ExternalAutomationJournalViewModel(settings, pipelines, journal, exportJournal)

    @Test
    fun `given no read yet when observed then entries are null so the screen shows a skeleton`() = runTest {
        // Nothing collected yet: the initial state must be distinguishable from an
        // empty journal, or a first read renders as "nothing ever arrived".
        assertNull(newViewModel().uiState.value.entries)
    }

    @Test
    fun `given a bound enabled contract when observed then the posture names the bound pipeline`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.contractEnabled)
        assertEquals("Morning digest", state.boundPipelineName)
    }

    @Test
    fun `given the contract switched off when observed then the posture reports it off`() = runTest {
        every { settings.externalAutomationEnabled } returns flowOf(false)

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.contractEnabled)
    }

    @Test
    fun `given no binding when observed then the bound pipeline is absent`() = runTest {
        every { settings.externalAutomationPipelineId } returns flowOf(null)

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.boundPipelineName)
    }

    @Test
    fun `given a binding whose pipeline was deleted when observed then it reads as unbound`() = runTest {
        // The authorizer cannot honour an id that resolves to nothing, so the
        // screen must not claim a pipeline the app would refuse to run.
        every { pipelines.observePipelineNames() } returns flowOf(emptyMap())

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.boundPipelineName)
    }

    @Test
    fun `given journal rows when observed then they reach the state newest-first and intact`() = runTest {
        val newer = refusal.copy(id = "row-2", receivedAt = 2_000L)
        every { journal.observeAll() } returns flowOf(listOf(newer, refusal))

        val viewModel = newViewModel()
        advanceUntilIdle()

        val entries = viewModel.uiState.value.entries
        assertEquals(listOf("row-2", "row-1"), entries?.map { it.id })
        // The collapsed repeat count is what keeps a looping caller readable as
        // one recurring fault; losing it in the mapping would be silent.
        assertEquals(7, entries?.last()?.repeatCount)
    }

    @Test
    fun `given an empty journal when observed then entries are empty rather than null`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(emptyList<ExternalAutomationJournalEntry>(), viewModel.uiState.value.entries)
    }

    // --- Journal export ----------------------------------------------------

    @Test
    fun `given a journal when exported then the document carries every request the store holds`() =
        runTest(testDispatcher) {
            coEvery { journal.readAll() } returns listOf(refusal)
            val vm = newViewModel()
            val received = mutableListOf<JournalExportEvent>()
            val collector = launch { vm.journalExportEvents.collect(received::add) }

            vm.journalExport.share()
            advanceUntilIdle()
            collector.cancel()

            val event = received.single() as JournalExportEvent.Share
            assertEquals(1, event.document.entryCount)
            assertTrue(event.document.json.contains("\"requestId\": \"tsk-1\""))
            // The collapsed repeat count is what turns a looping profile into one
            // recurring problem, so it has to survive the export.
            assertTrue(event.document.json.contains("\"repeatCount\": 7"))
            assertTrue(event.document.json.contains("\"statusReason\": \"TARGET_NOT_ALLOWED\""))
        }

    @Test
    fun `given the store holds requests the screen has not rendered when exported then they still travel`() =
        runTest(testDispatcher) {
            // The screen renders `observeAll`; the export reads the store afresh.
            // Exporting the rendered copy would silently produce a stale document.
            every { journal.observeAll() } returns flowOf(emptyList())
            coEvery { journal.readAll() } returns listOf(refusal)
            val vm = newViewModel()
            advanceUntilIdle()
            val received = mutableListOf<JournalExportEvent>()
            val collector = launch { vm.journalExportEvents.collect(received::add) }

            vm.journalExport.share()
            advanceUntilIdle()
            collector.cancel()

            assertEquals(0, vm.uiState.value.entries?.size)
            assertEquals(1, (received.single() as JournalExportEvent.Share).document.entryCount)
        }

    @Test
    fun `given the export filename when minted then it names the request journal`() {
        // A user who exports both journals ends up with two files in one folder;
        // the stem is the only thing that tells them apart.
        assertTrue(newViewModel().journalExport.newFileName().startsWith("external-requests-"))
    }
}
