package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.TriggerDao
import app.knotwork.android.data.local.models.TriggerEntity
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.triggerio.TriggerConditionCodec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TriggerRepositoryImpl] — entity ↔ domain mapping (condition
 * encoded via [TriggerConditionCodec]) and the undecodable-row skip contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TriggerRepositoryImplTest {

    private lateinit var dao: TriggerDao
    private lateinit var repository: TriggerRepositoryImpl

    private val goodEntity = TriggerEntity(
        id = "t1",
        name = "Morning brief",
        pipelineId = "pipe-1",
        prompt = "brief me",
        conditionJson = TriggerConditionCodec.encode(TriggerCondition.DailySchedule(hour = 8, minute = 0)),
        enabled = true,
        armed = false,
        createdAt = 100L,
        lastFiredAt = 200L,
        sessionId = "sess-1",
    )
    private val badEntity = goodEntity.copy(id = "t2", conditionJson = "garbage")

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = TriggerRepositoryImpl(dao)
    }

    @Test
    fun `given rows when observed then maps domain and skips undecodable rows`() = runTest {
        every { dao.getAll() } returns flowOf(listOf(goodEntity, badEntity))

        val triggers = repository.observeTriggers().first()

        assertEquals(1, triggers.size)
        val trigger = triggers.single()
        assertEquals("t1", trigger.id)
        assertEquals("Morning brief", trigger.name)
        assertEquals("pipe-1", trigger.pipelineId)
        assertEquals("brief me", trigger.prompt)
        assertEquals(TriggerCondition.DailySchedule(hour = 8, minute = 0), trigger.condition)
        assertEquals(true, trigger.enabled)
        assertEquals(false, trigger.armed)
        assertEquals(100L, trigger.createdAt)
        assertEquals(200L, trigger.lastFiredAt)
        assertEquals("sess-1", trigger.sessionId)
    }

    @Test
    fun `given active rows when observed then mapped through the active query`() = runTest {
        every { dao.getActive() } returns flowOf(listOf(goodEntity))

        val triggers = repository.observeActiveTriggers().first()

        assertEquals(listOf("t1"), triggers.map { it.id })
    }

    @Test
    fun `given existing id when getTriggerById then decodes the row`() = runTest {
        coEvery { dao.getById("t1") } returns goodEntity

        val trigger = repository.getTriggerById("t1")

        assertEquals(TriggerCondition.DailySchedule(hour = 8, minute = 0), trigger?.condition)
    }

    @Test
    fun `given undecodable row when getTriggerById then null`() = runTest {
        coEvery { dao.getById("t2") } returns badEntity

        assertNull(repository.getTriggerById("t2"))
    }

    @Test
    fun `given a trigger when saved then upserts with encoded condition`() = runTest {
        val entitySlot = slot<TriggerEntity>()
        coEvery { dao.upsert(capture(entitySlot)) } returns Unit
        val trigger = Trigger(
            id = "t3",
            name = "On charge",
            condition = TriggerCondition.Charging,
            pipelineId = "pipe-9",
            prompt = "run",
            enabled = false,
            armed = false,
            createdAt = 5L,
            lastFiredAt = null,
            sessionId = "sess-9",
        )

        repository.saveTrigger(trigger)

        val captured = entitySlot.captured
        assertEquals("t3", captured.id)
        assertEquals("pipe-9", captured.pipelineId)
        assertEquals(false, captured.enabled)
        assertEquals(false, captured.armed)
        assertEquals("sess-9", captured.sessionId)
        assertEquals(TriggerCondition.Charging, TriggerConditionCodec.decode(captured.conditionJson))
    }

    @Test
    fun `given mutations then they delegate to the dao`() = runTest {
        repository.deleteTrigger("t1")
        repository.setEnabled("t1", false)
        repository.setArmed("t1", true)
        repository.markFired("t1", 999L)
        repository.setSessionId("t1", "sess-2")

        coVerify(exactly = 1) { dao.deleteById("t1") }
        coVerify(exactly = 1) { dao.setEnabled("t1", false) }
        coVerify(exactly = 1) { dao.setArmed("t1", true) }
        coVerify(exactly = 1) { dao.markFired("t1", 999L) }
        coVerify(exactly = 1) { dao.setSessionId("t1", "sess-2") }
    }
}
