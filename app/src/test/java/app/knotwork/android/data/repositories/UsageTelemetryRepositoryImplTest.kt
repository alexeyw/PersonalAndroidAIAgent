package app.knotwork.android.data.repositories

import androidx.room.Room
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.dao.UsageTelemetryDao
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Verifies the on-device [UsageTelemetryRepositoryImpl] against a real in-memory
 * Room database: opt-in gating, per-pipeline / per-outcome / per-kind tallies,
 * the daily-active set, and reset.
 */
@RunWith(RobolectricTestRunner::class)
class UsageTelemetryRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: UsageTelemetryDao
    private lateinit var settingsRepository: SettingsRepository

    // 2026-06-25T10:00:00Z in UTC → local day "2026-06-25".
    private val zone = ZoneId.of("UTC")
    private val day1 = Instant.parse("2026-06-25T10:00:00Z").toEpochMilli()
    private val day1Later = Instant.parse("2026-06-25T23:00:00Z").toEpochMilli()
    private val day2 = Instant.parse("2026-06-26T08:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.usageTelemetryDao()
        settingsRepository = mockk()
        every { settingsRepository.usageTelemetryEnabled } returns flowOf(true)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(): UsageTelemetryRepositoryImpl =
        UsageTelemetryRepositoryImpl(dao, settingsRepository, { Clock.fixed(Instant.ofEpochMilli(day1), zone) })
            .apply { dispatcher = Dispatchers.Unconfined }

    @Test
    fun `given recording enabled when a root run finishes then it is tallied by pipeline outcome and day`() = runTest {
        val repository = repository()

        repository.recordPipelineRunOutcome("pipe-1", PipelineRunStatus.COMPLETED, day1)

        val summary = repository.summary.first()
        assertEquals(1, summary.totalRuns)
        assertEquals(1, summary.runsByOutcome[PipelineRunStatus.COMPLETED])
        assertEquals("pipe-1", summary.runsByPipeline.single().pipelineId)
        assertEquals(1, summary.runsByPipeline.single().runCount)
        assertEquals(1, summary.activeDays)
        assertEquals("2026-06-25", summary.firstActiveDay)
        assertEquals("2026-06-25", summary.lastActiveDay)
    }

    @Test
    fun `given recording disabled when a run finishes then nothing is tallied`() = runTest {
        every { settingsRepository.usageTelemetryEnabled } returns flowOf(false)
        val repository = repository()

        repository.recordPipelineRunOutcome("pipe-1", PipelineRunStatus.COMPLETED, day1)

        val summary = repository.summary.first()
        assertTrue(summary.isEmpty)
        assertEquals(0, summary.totalRuns)
    }

    @Test
    fun `given a non-terminal status when recorded then it is ignored`() = runTest {
        val repository = repository()

        repository.recordPipelineRunOutcome("pipe-1", PipelineRunStatus.RUNNING, day1)

        assertTrue(repository.summary.first().isEmpty)
    }

    @Test
    fun `given a run with no resolved pipeline when recorded then it tallies under a null pipeline id`() = runTest {
        val repository = repository()

        repository.recordPipelineRunOutcome(null, PipelineRunStatus.INTERRUPTED, day1)

        val summary = repository.summary.first()
        assertNull(summary.runsByPipeline.single().pipelineId)
        assertEquals(1, summary.runsByOutcome[PipelineRunStatus.INTERRUPTED])
    }

    @Test
    fun `given trigger firings when recorded then they tally by kind`() = runTest {
        val repository = repository()

        repository.recordTriggerFired("CHARGING", day1)
        repository.recordTriggerFired("CHARGING", day1)
        repository.recordTriggerFired("INTERVAL", day1)

        val summary = repository.summary.first()
        assertEquals(2, summary.triggerFiresByKind["CHARGING"])
        assertEquals(1, summary.triggerFiresByKind["INTERVAL"])
        assertEquals(3, summary.totalTriggerFires)
    }

    @Test
    fun `given events on the same day when recorded then the active-day set keeps one entry`() = runTest {
        val repository = repository()

        repository.recordPipelineRunOutcome("pipe-1", PipelineRunStatus.COMPLETED, day1)
        repository.recordPipelineRunOutcome("pipe-1", PipelineRunStatus.FAILED, day1Later)
        repository.recordTriggerFired("CHARGING", day2)

        val summary = repository.summary.first()
        assertEquals(2, summary.activeDays)
        assertEquals("2026-06-25", summary.firstActiveDay)
        assertEquals("2026-06-26", summary.lastActiveDay)
        assertEquals(2, summary.runsByPipeline.single().runCount)
    }

    @Test
    fun `given recorded statistics when reset then everything is cleared`() = runTest {
        val repository = repository()
        repository.recordPipelineRunOutcome("pipe-1", PipelineRunStatus.COMPLETED, day1)
        repository.recordTriggerFired("CHARGING", day1)

        repository.reset()

        assertTrue(repository.summary.first().isEmpty)
    }

    @Test
    fun `isEnabled mirrors the settings flag`() = runTest {
        assertTrue(repository().isEnabled())

        every { settingsRepository.usageTelemetryEnabled } returns flowOf(false)
        assertFalse(repository().isEnabled())
    }
}
