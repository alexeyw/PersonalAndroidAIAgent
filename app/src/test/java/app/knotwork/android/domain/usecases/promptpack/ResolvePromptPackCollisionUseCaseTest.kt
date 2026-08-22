package app.knotwork.android.domain.usecases.promptpack

import app.knotwork.android.domain.constants.PromptPresetConstants
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PromptPackCandidate
import app.knotwork.android.domain.models.PromptPreset
import app.knotwork.android.domain.repositories.PromptPresetRepository
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests for the two ways a re-import collision can be resolved. */
class ResolvePromptPackCollisionUseCaseTest {

    private lateinit var repository: PromptPresetRepository
    private lateinit var useCase: ResolvePromptPackCollisionUseCase

    private val candidate = PromptPackCandidate(
        id = "standup-digest",
        name = "Standup digest",
        description = "",
        nodeType = NodeType.OUTPUT,
        systemPrompt = "Three bullets.",
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = ResolvePromptPackCollisionUseCase(repository)
    }

    private fun saved(): PromptPreset {
        val captured = slot<PromptPreset>()
        coVerify { repository.saveUserPreset(capture(captured)) }
        return captured.captured
    }

    @Test
    fun `given replace when resolved then the colliding id is kept`() = runTest {
        useCase(candidate = candidate, choice = PromptPackCollisionChoice.REPLACE)

        assertEquals("standup-digest", saved().id)
        assertEquals("Standup digest", saved().name)
        assertFalse(saved().isBundled)
    }

    @Test
    fun `given keep both when resolved then a fresh id and a marked name are written`() = runTest {
        useCase(candidate = candidate, choice = PromptPackCollisionChoice.KEEP_BOTH)

        assertNotEquals("standup-digest", saved().id)
        assertEquals("Standup digest (imported)", saved().name)
    }

    @Test
    fun `given a name at the length ceiling when keeping both then the marker survives the trim`() = runTest {
        // The marker is the part that must survive: two rows reading exactly
        // the same thing is the failure this action exists to avoid.
        val long = "n".repeat(PromptPresetConstants.MAX_NAME_LENGTH)

        useCase(candidate = candidate.copy(name = long), choice = PromptPackCollisionChoice.KEEP_BOTH)

        assertTrue(saved().name.endsWith(" (imported)"))
        assertTrue(saved().name.length <= PromptPresetConstants.MAX_NAME_LENGTH)
    }
}
