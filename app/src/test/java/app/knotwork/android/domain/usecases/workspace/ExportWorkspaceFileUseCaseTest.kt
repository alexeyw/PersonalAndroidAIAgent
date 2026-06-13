package app.knotwork.android.domain.usecases.workspace

import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.services.AgentWorkspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Verifies [ExportWorkspaceFileUseCase] forwards the path and sink to the workspace. */
class ExportWorkspaceFileUseCaseTest {

    private val workspace = mockk<AgentWorkspace>()
    private val useCase = ExportWorkspaceFileUseCase(workspace)

    @Test
    fun `given a path and sink when invoked then delegates to exportTo`() = runTest {
        val sink = ByteArrayOutputStream()
        coEvery { workspace.exportTo("report.md", sink) } returns WorkspaceResult.Success(Unit)

        val result = useCase("report.md", sink)

        assertEquals(WorkspaceResult.Success(Unit), result)
        coVerify { workspace.exportTo("report.md", sink) }
    }

    @Test
    fun `given the workspace refuses when invoked then propagates the failure`() = runTest {
        val sink = ByteArrayOutputStream()
        coEvery { workspace.exportTo(any(), any()) } returns WorkspaceResult.Failure(WorkspaceError.NotFound)

        val result = useCase("missing.md", sink)

        assertEquals(WorkspaceResult.Failure(WorkspaceError.NotFound), result)
    }
}
