package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.RunTraceRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CleanupPipelineRunsUseCase]: the two retention settings
 * are read fresh per pass, the max-age cutoff is derived from the configured
 * day count, and the outcome counters mirror what the repositories report.
 */
class CleanupPipelineRunsUseCaseTest {

    private lateinit var pipelineRunRepository: PipelineRunRepository
    private lateinit var runTraceRepository: RunTraceRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: CleanupPipelineRunsUseCase

    @Before
    fun setup() {
        pipelineRunRepository = mockk()
        runTraceRepository = mockk()
        settingsRepository = mockk()
        every { settingsRepository.traceRetentionRunsPerSession } returns flowOf(20)
        every { settingsRepository.traceRetentionMaxAgeDays } returns flowOf(30)
        coEvery { pipelineRunRepository.applyRetention(any(), any()) } returns 0
        coEvery { runTraceRepository.deleteLegacyTraceBefore(any()) } returns 0
        useCase = CleanupPipelineRunsUseCase(pipelineRunRepository, runTraceRepository, settingsRepository)
    }

    @Test
    fun `given configured limits when invoked then repositories receive them`() = runTest {
        every { settingsRepository.traceRetentionRunsPerSession } returns flowOf(5)
        every { settingsRepository.traceRetentionMaxAgeDays } returns flowOf(7)
        val runCutoff = slot<Long>()
        val traceCutoff = slot<Long>()
        coEvery { pipelineRunRepository.applyRetention(5, capture(runCutoff)) } returns 2
        coEvery { runTraceRepository.deleteLegacyTraceBefore(capture(traceCutoff)) } returns 1

        val before = System.currentTimeMillis()
        val outcome = useCase()
        val after = System.currentTimeMillis()

        // Cutoff = now - 7 days, bounded by the wall clock around the call.
        val sevenDaysMs = 7L * 24L * 60L * 60L * 1_000L
        assertTrue(runCutoff.captured in (before - sevenDaysMs)..(after - sevenDaysMs))
        // Both deletes share one cutoff so the pass is internally consistent.
        assertEquals(runCutoff.captured, traceCutoff.captured)
        assertEquals(2, outcome.deletedRuns)
        assertEquals(1, outcome.deletedLegacyTraceRows)
    }

    @Test
    fun `given nothing qualifies when invoked then outcome is all zeros`() = runTest {
        val outcome = useCase()

        assertEquals(0, outcome.deletedRuns)
        assertEquals(0, outcome.deletedLegacyTraceRows)
        coVerify(exactly = 1) { pipelineRunRepository.applyRetention(20, any()) }
        coVerify(exactly = 1) { runTraceRepository.deleteLegacyTraceBefore(any()) }
    }

    @Test
    fun `settings are re-read on every pass`() = runTest {
        useCase()
        every { settingsRepository.traceRetentionRunsPerSession } returns flowOf(50)
        useCase()

        coVerify(exactly = 1) { pipelineRunRepository.applyRetention(20, any()) }
        coVerify(exactly = 1) { pipelineRunRepository.applyRetention(50, any()) }
    }
}
