package app.knotwork.android.data.repositories

import androidx.room.Room
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.dao.ExternalAutomationJournalDao
import app.knotwork.android.data.local.models.ExternalAutomationRequestEntity
import app.knotwork.android.domain.models.ExternalAutomationJournalEntry
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationStatus
import app.knotwork.android.domain.models.ExternalAutomationTarget
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Verifies [ExternalAutomationJournalRepositoryImpl] against a real in-memory Room
 * database: the status / target mapping round-trips, the rate ceiling counts and
 * admits atomically, repeated refusals collapse, retention applies both limits, and
 * an undecodable row is dropped on read. A second section drives the best-effort
 * contract — and the deliberate fail-closed exception — with a mocked DAO.
 */
@RunWith(RobolectricTestRunner::class)
class ExternalAutomationJournalRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ExternalAutomationJournalDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.externalAutomationJournalDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(realDao: ExternalAutomationJournalDao = dao): ExternalAutomationJournalRepositoryImpl =
        ExternalAutomationJournalRepositoryImpl(realDao).apply { dispatcher = Dispatchers.Unconfined }

    private fun entry(
        id: String,
        requestId: String = "req-$id",
        receivedAt: Long = 0L,
        action: String = ACTION,
        target: ExternalAutomationTarget? = ExternalAutomationTarget.ById("pipe-1"),
        declaredReturnPackage: String? = "com.example.caller",
        returnAction: String = "app.knotwork.android.action.RUN_RESULT",
        attestedSenderPackage: String? = null,
        status: ExternalAutomationStatus = ExternalAutomationStatus.Accepted,
        runId: String? = "run-$id",
        repeatCount: Int = 1,
    ) = ExternalAutomationJournalEntry(
        id = id,
        requestId = requestId,
        receivedAt = receivedAt,
        action = action,
        target = target,
        declaredReturnPackage = declaredReturnPackage,
        returnAction = returnAction,
        attestedSenderPackage = attestedSenderPackage,
        status = status,
        runId = runId,
        repeatCount = repeatCount,
    )

    private fun refusal(
        id: String,
        reason: ExternalAutomationRejectionReason = ExternalAutomationRejectionReason.CONTRACT_DISABLED,
        receivedAt: Long = 0L,
        requestId: String = "req-$id",
        target: ExternalAutomationTarget? = ExternalAutomationTarget.ById("pipe-1"),
        status: ExternalAutomationStatus = ExternalAutomationStatus.Rejected(reason),
    ) = entry(
        id = id,
        requestId = requestId,
        receivedAt = receivedAt,
        target = target,
        status = status,
        runId = null,
    )

    @Test
    fun `given every status and target form when recorded then each round-trips through the store`() = runTest {
        val repo = repository()
        repo.admitAcceptedWithinCeiling(entry("a", receivedAt = 1), windowStartEpochMs = 0, limitPerWindow = 10)
        repo.admitAcceptedWithinCeiling(
            entry("b", receivedAt = 2, target = ExternalAutomationTarget.ByName("Daily digest")),
            windowStartEpochMs = 0,
            limitPerWindow = 10,
        )
        repo.recordRefusal(refusal("c", receivedAt = 3, target = null))
        repo.recordRefusal(
            refusal(
                "d",
                receivedAt = 4,
                target = ExternalAutomationTarget.ByName("Other"),
                status = ExternalAutomationStatus.Blocked(ExternalAutomationRejectionReason.RATE_LIMITED),
            ),
        )

        val stored = repo.observeAll().first().associateBy { it.id }
        assertEquals(4, stored.size)
        assertEquals(ExternalAutomationStatus.Accepted, stored.getValue("a").status)
        assertEquals(ExternalAutomationTarget.ById("pipe-1"), stored.getValue("a").target)
        assertEquals(ExternalAutomationTarget.ByName("Daily digest"), stored.getValue("b").target)
        // A request that named nothing must read back as "named nothing", never as
        // a target the app filled in.
        assertNull(stored.getValue("c").target)
        assertEquals(
            ExternalAutomationStatus.Rejected(ExternalAutomationRejectionReason.CONTRACT_DISABLED),
            stored.getValue("c").status,
        )
        assertEquals(
            ExternalAutomationStatus.Blocked(ExternalAutomationRejectionReason.RATE_LIMITED),
            stored.getValue("d").status,
        )
    }

    @Test
    fun `given the journal when observed then it is ordered newest request first`() = runTest {
        val repo = repository()
        repo.recordRefusal(refusal("old", receivedAt = 1, requestId = "r1"))
        repo.recordRefusal(
            refusal("new", receivedAt = 9, requestId = "r2", reason = ExternalAutomationRejectionReason.TARGET_MISSING),
        )

        assertEquals(listOf("new", "old"), repo.observeAll().first().map { it.id })
    }

    // --- The rate ceiling -------------------------------------------------

    @Test
    fun `given the ceiling is reached when another request arrives then it is not admitted`() = runTest {
        val repo = repository()
        repeat(3) {
            assertTrue(
                repo.admitAcceptedWithinCeiling(
                    entry("adm-$it", receivedAt = 100L + it),
                    windowStartEpochMs = 0,
                    limitPerWindow = 3,
                ),
            )
        }

        val admitted = repo.admitAcceptedWithinCeiling(
            entry("adm-over", receivedAt = 104),
            windowStartEpochMs = 0,
            limitPerWindow = 3,
        )

        assertFalse(admitted)
        // The refused request must not leave an accepted row behind — otherwise it
        // would consume a slot in every later window it still falls inside.
        assertNull(repo.observeAll().first().firstOrNull { it.id == "adm-over" })
    }

    @Test
    fun `given admissions older than the window when a request arrives then they do not count against it`() = runTest {
        val repo = repository()
        repeat(3) {
            repo.admitAcceptedWithinCeiling(entry("stale-$it", receivedAt = 10L + it), 0, limitPerWindow = 3)
        }

        val admitted = repo.admitAcceptedWithinCeiling(
            entry("fresh", receivedAt = 1_000),
            windowStartEpochMs = 500,
            limitPerWindow = 3,
        )

        assertTrue(admitted)
    }

    @Test
    fun `given refusals inside the window when a request arrives then they do not count against the ceiling`() =
        runTest {
            val repo = repository()
            // Refusals are the high-volume case — a third-party app looping against
            // a switched-off contract. Counting them would let that app rate-limit
            // the user's own legitimate automation.
            repeat(5) { repo.recordRefusal(refusal("ref-$it", receivedAt = 10L + it, requestId = "r$it")) }

            val admitted = repo.admitAcceptedWithinCeiling(entry("adm", receivedAt = 20), 0, limitPerWindow = 3)

            assertTrue(admitted)
        }

    @Test
    fun `given an admission without a run id when admitted then it is rejected as a caller bug`() = runTest {
        val repo = repository()

        // The ceiling counts `runId IS NOT NULL`; an admission without one would be
        // invisible to the ceiling that governs it.
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.admitAcceptedWithinCeiling(entry("no-run", runId = null), 0, limitPerWindow = 3)
            }
        }
    }

    @Test
    fun `given a non-positive ceiling when admitting then it is rejected as a caller bug`() = runTest {
        val repo = repository()

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.admitAcceptedWithinCeiling(entry("x"), 0, limitPerWindow = 0)
            }
        }
    }

    // --- Refusal collapsing ------------------------------------------------

    @Test
    fun `given the same refusal repeated when recorded then it folds onto one row`() = runTest {
        val repo = repository()
        repo.recordRefusal(refusal("first", receivedAt = 10, requestId = "r1"))
        repo.recordRefusal(refusal("second", receivedAt = 20, requestId = "r2"))
        repo.recordRefusal(refusal("third", receivedAt = 30, requestId = "r3"))

        val rows = repo.observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(3, rows.single().repeatCount)
        // The row ages from the latest occurrence, so retention keeps a problem
        // visible while it is still happening.
        assertEquals(30L, rows.single().receivedAt)
        assertEquals("r3", rows.single().requestId)
    }

    @Test
    fun `given a different refusal reason when recorded then it starts its own row`() = runTest {
        val repo = repository()
        repo.recordRefusal(refusal("a", receivedAt = 10, requestId = "r1"))
        repo.recordRefusal(
            refusal("b", receivedAt = 20, requestId = "r2", reason = ExternalAutomationRejectionReason.TARGET_MISSING),
        )

        val rows = repo.observeAll().first()
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.repeatCount == 1 })
    }

    @Test
    fun `given a refusal for a different target when recorded then it starts its own row`() = runTest {
        val repo = repository()
        repo.recordRefusal(refusal("a", receivedAt = 10, requestId = "r1"))
        repo.recordRefusal(
            refusal("b", receivedAt = 20, requestId = "r2", target = ExternalAutomationTarget.ById("pipe-2")),
        )

        assertEquals(2, repo.observeAll().first().size)
    }

    @Test
    fun `given a refusal with no target repeated when recorded then it still folds`() = runTest {
        val repo = repository()
        // SQLite's `=` is never true against NULL, so a naive equality join would
        // never fold the commonest refusal of all: a call that named no pipeline.
        repo.recordRefusal(refusal("a", receivedAt = 10, requestId = "r1", target = null))
        repo.recordRefusal(refusal("b", receivedAt = 20, requestId = "r2", target = null))

        val rows = repo.observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(2, rows.single().repeatCount)
    }

    @Test
    fun `given an admission between two identical refusals when recorded then the refusal does not fold`() = runTest {
        val repo = repository()
        repo.recordRefusal(refusal("a", receivedAt = 10, requestId = "r1"))
        repo.admitAcceptedWithinCeiling(entry("adm", receivedAt = 20), 0, limitPerWindow = 5)
        repo.recordRefusal(refusal("b", receivedAt = 30, requestId = "r2"))

        // Only an uninterrupted run of the same refusal collapses; the journal
        // stays a chronological record rather than a tally.
        assertEquals(3, repo.observeAll().first().size)
    }

    @Test
    fun `given an admission repeated when recorded then it never folds`() = runTest {
        val repo = repository()
        repo.admitAcceptedWithinCeiling(entry("a", receivedAt = 10, requestId = "r1"), 0, limitPerWindow = 5)
        repo.admitAcceptedWithinCeiling(entry("b", receivedAt = 20, requestId = "r1"), 0, limitPerWindow = 5)

        // Two runs really started; folding them would under-count the ceiling.
        assertEquals(2, repo.observeAll().first().size)
    }

    // --- Outcome settlement -------------------------------------------------

    @Test
    fun `given an admitted request when its run settles then the row carries the terminal status`() = runTest {
        val repo = repository()
        repo.admitAcceptedWithinCeiling(entry("a", receivedAt = 1), 0, limitPerWindow = 5)

        repo.recordOutcome("run-a", ExternalAutomationStatus.Completed)

        assertEquals(ExternalAutomationStatus.Completed, repo.findByRunId("run-a")?.status)
    }

    @Test
    fun `given a run that owns no row when its outcome is recorded then nothing happens`() = runTest {
        val repo = repository()
        repo.admitAcceptedWithinCeiling(entry("a", receivedAt = 1), 0, limitPerWindow = 5)

        // A nested sub-pipeline child inherits its parent's origin but owns no row.
        // This no-op is what keeps the terminal callback root-only for free.
        repo.recordOutcome("child-run", ExternalAutomationStatus.Failed)

        assertEquals(ExternalAutomationStatus.Accepted, repo.findByRunId("run-a")?.status)
        assertNull(repo.findByRunId("child-run"))
    }

    // --- Retention -----------------------------------------------------------

    @Test
    fun `given rows older than the cutoff when retention runs then they are deleted`() = runTest {
        val repo = repository()
        repo.admitAcceptedWithinCeiling(entry("old", receivedAt = 10), 0, limitPerWindow = 9)
        repo.admitAcceptedWithinCeiling(entry("new", receivedAt = 100), 0, limitPerWindow = 9)

        val deleted = repo.applyRetention(olderThanEpochMs = 50, maxRecords = 100)

        assertEquals(1, deleted)
        assertEquals(listOf("new"), repo.observeAll().first().map { it.id })
    }

    @Test
    fun `given more rows than the cap when retention runs then the newest are kept`() = runTest {
        val repo = repository()
        repeat(5) { repo.admitAcceptedWithinCeiling(entry("r$it", receivedAt = it.toLong()), 0, limitPerWindow = 9) }

        repo.applyRetention(olderThanEpochMs = 0, maxRecords = 2)

        assertEquals(listOf("r4", "r3"), repo.observeAll().first().map { it.id })
    }

    @Test
    fun `given a non-positive cap when retention runs then it throws instead of wiping the journal`() = runTest {
        val repo = repository()
        repo.admitAcceptedWithinCeiling(entry("a"), 0, limitPerWindow = 9)

        // `maxRecords <= 0` makes the enforce-cap DELETE keep zero rows — a wipe,
        // not a no-op. Asserted rather than absorbed.
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.applyRetention(olderThanEpochMs = 0, maxRecords = 0) }
        }
        assertEquals(1, repo.observeAll().first().size)
    }

    // --- Tolerant decode ------------------------------------------------------

    @Test
    fun `given a row with an unknown status when read then it is skipped rather than crashing the journal`() = runTest {
        dao.insert(row(id = "bad", statusKind = "Teleported"))
        dao.insert(row(id = "good", statusKind = "Accepted", runId = "run-good"))

        assertEquals(listOf("good"), repository().observeAll().first().map { it.id })
    }

    @Test
    fun `given a refusal row with an unknown reason when read then it is skipped`() = runTest {
        // The reason is the whole content of a refusal row; a row that cannot say
        // why it refused must not read as a refusal with an invented reason.
        dao.insert(row(id = "bad", statusKind = "Rejected", statusReason = "BECAUSE"))

        assertTrue(repository().observeAll().first().isEmpty())
    }

    @Test
    fun `given a row whose target kind is unknown when read then it is skipped rather than reading as targetless`() =
        runTest {
            // Decoding it as "named nothing" would silently turn one event into a
            // materially different one.
            dao.insert(row(id = "bad", targetKind = "BY_VIBE", targetValue = "something"))

            assertTrue(repository().observeAll().first().isEmpty())
        }

    // --- Best-effort contract, and the fail-closed exception ------------------

    @Test
    fun `given the store fails when a refusal is recorded then the failure is absorbed`() = runTest {
        val failing = mockk<ExternalAutomationJournalDao>()
        coEvery { failing.recordRefusal(any()) } throws IllegalStateException("db down")

        repository(failing).recordRefusal(refusal("a"))
    }

    @Test
    fun `given the store fails when admitting then the request is refused rather than admitted`() = runTest {
        val failing = mockk<ExternalAutomationJournalDao>()
        coEvery { failing.insertIfUnderCeiling(any(), any(), any()) } throws IllegalStateException("db down")

        val admitted = repository(failing).admitAcceptedWithinCeiling(entry("a"), 0, limitPerWindow = 3)

        // Fail closed. Failing open here would let an unreadable database lift the
        // ceiling on an entry point whose call rate a third-party app controls.
        assertFalse(admitted)
    }

    @Test
    fun `given the store fails when the journal is observed then it degrades to empty`() = runTest {
        val failing = mockk<ExternalAutomationJournalDao>()
        every { failing.observeAll() } returns flow { throw IllegalStateException("db down") }

        assertTrue(repository(failing).observeAll().first().isEmpty())
    }

    @Test
    fun `given the store fails when a row is looked up by run then it reads as absent`() = runTest {
        val failing = mockk<ExternalAutomationJournalDao>()
        coEvery { failing.findByRunId(any()) } throws IllegalStateException("db down")

        assertNull(repository(failing).findByRunId("run-a"))
    }

    private fun row(
        id: String,
        requestId: String = "req",
        receivedAt: Long = 1L,
        action: String = ACTION,
        targetKind: String? = "ID",
        targetValue: String? = "pipe-1",
        statusKind: String = "Accepted",
        statusReason: String? = null,
        runId: String? = "run-$id",
    ) = ExternalAutomationRequestEntity(
        id = id,
        requestId = requestId,
        receivedAt = receivedAt,
        action = action,
        targetKind = targetKind,
        targetValue = targetValue,
        declaredReturnPackage = null,
        returnAction = "app.knotwork.android.action.RUN_RESULT",
        attestedSenderPackage = null,
        statusKind = statusKind,
        statusReason = statusReason,
        runId = runId,
        repeatCount = 1,
    )

    private companion object {
        const val ACTION = "app.knotwork.android.action.RUN_PIPELINE"
    }
}
