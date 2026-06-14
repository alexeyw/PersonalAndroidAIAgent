package app.knotwork.android.domain.usecases.workspace

import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceFile
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.models.WorkspaceUsage
import app.knotwork.android.domain.services.AgentWorkspace
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that [ListWorkspaceUseCase] bundles the listing + usage and short-circuits on failure. */
class ListWorkspaceUseCaseTest {

    private val workspace = mockk<AgentWorkspace>()
    private val useCase = ListWorkspaceUseCase(workspace)

    @Test
    fun `given list and usage succeed when invoked then returns combined listing`() = runTest {
        val files = listOf(
            WorkspaceFile("a.txt", sizeBytes = 5, lastModified = 1, isDirectory = false, isText = true),
        )
        coEvery { workspace.list() } returns WorkspaceResult.Success(files)
        coEvery { workspace.usage() } returns WorkspaceResult.Success(WorkspaceUsage(usedBytes = 5, limitBytes = 100))

        val result = useCase()

        assertTrue(result is WorkspaceResult.Success)
        val listing = (result as WorkspaceResult.Success).value
        assertEquals(files, listing.files)
        assertEquals(5L, listing.usage.usedBytes)
        assertEquals(100L, listing.usage.limitBytes)
    }

    @Test
    fun `given list fails when invoked then returns that failure without reading usage`() = runTest {
        coEvery { workspace.list() } returns WorkspaceResult.Failure(WorkspaceError.NotFound)

        val result = useCase()

        assertEquals(WorkspaceResult.Failure(WorkspaceError.NotFound), result)
    }

    @Test
    fun `given usage fails when invoked then returns that failure`() = runTest {
        coEvery { workspace.list() } returns WorkspaceResult.Success(emptyList())
        coEvery { workspace.usage() } returns WorkspaceResult.Failure(WorkspaceError.NotFound)

        val result = useCase()

        assertEquals(WorkspaceResult.Failure(WorkspaceError.NotFound), result)
    }
}
