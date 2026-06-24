package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.repositories.TriggerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SaveTriggerUseCase] — the domain owner of a trigger's runtime
 * lifecycle fields (`armed` / `lastFiredAt` / `createdAt`). Verifies the
 * create-stamps-now, edit-preserves, and condition-change-resets rules.
 */
class SaveTriggerUseCaseTest {

    private lateinit var repository: TriggerRepository
    private lateinit var useCase: SaveTriggerUseCase

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = SaveTriggerUseCase(repository)
    }

    private fun intent(id: String?, condition: TriggerCondition = TriggerCondition.Charging) = TriggerDraftIntent(
        id = id,
        name = "Trigger",
        condition = condition,
        pipelineId = "p1",
        prompt = "do it",
        enabled = true,
    )

    private fun existing(condition: TriggerCondition) = Trigger(
        id = "t1",
        name = "Old name",
        condition = condition,
        pipelineId = "p1",
        prompt = "old",
        enabled = true,
        armed = false,
        createdAt = 1_000L,
        lastFiredAt = 5_000L,
    )

    @Test
    fun `given a new trigger when saved then stamps createdAt, arms, and clears the fire time`() = runTest {
        val saved = slot<Trigger>()
        coEvery { repository.saveTrigger(capture(saved)) } returns Unit

        useCase(intent(id = null), nowMillis = 42L)

        assertEquals(42L, saved.captured.createdAt)
        assertTrue(saved.captured.armed)
        assertNull(saved.captured.lastFiredAt)
        assertTrue(saved.captured.id.isNotBlank())
    }

    @Test
    fun `given an edit that keeps the condition when saved then preserves armed, createdAt and lastFiredAt`() =
        runTest {
            coEvery { repository.getTriggerById("t1") } returns existing(TriggerCondition.Charging)
            val saved = slot<Trigger>()
            coEvery { repository.saveTrigger(capture(saved)) } returns Unit

            useCase(intent(id = "t1", condition = TriggerCondition.Charging), nowMillis = 99L)

            assertEquals(1_000L, saved.captured.createdAt)
            assertEquals(false, saved.captured.armed)
            assertEquals(5_000L, saved.captured.lastFiredAt)
            assertEquals("t1", saved.captured.id)
            assertEquals("Trigger", saved.captured.name)
        }

    @Test
    fun `given an edit that changes the condition when saved then re-arms and clears the fire time`() = runTest {
        coEvery { repository.getTriggerById("t1") } returns existing(TriggerCondition.Charging)
        val saved = slot<Trigger>()
        coEvery { repository.saveTrigger(capture(saved)) } returns Unit

        useCase(intent(id = "t1", condition = TriggerCondition.NetworkConnected(wifiOnly = true)), nowMillis = 99L)

        assertEquals(1_000L, saved.captured.createdAt)
        assertTrue(saved.captured.armed)
        assertNull(saved.captured.lastFiredAt)
        coVerify(exactly = 1) { repository.saveTrigger(any()) }
    }
}
