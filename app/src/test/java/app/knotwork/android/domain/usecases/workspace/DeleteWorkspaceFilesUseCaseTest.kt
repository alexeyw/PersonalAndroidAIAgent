package app.knotwork.android.domain.usecases.workspace

import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.services.AgentWorkspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies [DeleteWorkspaceFilesUseCase] deletes every path independently and partitions the outcomes. */
class DeleteWorkspaceFilesUseCaseTest {

    private val workspace = mockk<AgentWorkspace>()
    private val useCase = DeleteWorkspaceFilesUseCase(workspace)

    @Test
    fun `given all deletes succeed when invoked then summary lists them all as deleted`() = runTest {
        coEvery { workspace.delete(any()) } returns WorkspaceResult.Success(Unit)

        val summary = useCase(listOf("a.txt", "b.txt"))

        assertTrue(summary.allSucceeded)
        assertEquals(listOf("a.txt", "b.txt"), summary.deleted)
        assertTrue(summary.failed.isEmpty())
        coVerify { workspace.delete("a.txt") }
        coVerify { workspace.delete("b.txt") }
    }

    @Test
    fun `given one delete fails when invoked then it does not abort the others`() = runTest {
        coEvery { workspace.delete("a.txt") } returns WorkspaceResult.Success(Unit)
        coEvery { workspace.delete("missing.txt") } returns WorkspaceResult.Failure(WorkspaceError.NotFound)
        coEvery { workspace.delete("c.txt") } returns WorkspaceResult.Success(Unit)

        val summary = useCase(listOf("a.txt", "missing.txt", "c.txt"))

        assertFalse(summary.allSucceeded)
        assertEquals(listOf("a.txt", "c.txt"), summary.deleted)
        assertEquals(mapOf("missing.txt" to WorkspaceError.NotFound), summary.failed)
    }

    @Test
    fun `given an empty list when invoked then summary is empty and succeeded`() = runTest {
        val summary = useCase(emptyList())

        assertTrue(summary.allSucceeded)
        assertTrue(summary.deleted.isEmpty())
    }
}
