package app.knotwork.android.domain.usecases.workspace

import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.models.WorkspaceTextPreview
import app.knotwork.android.domain.services.AgentWorkspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies [PreviewWorkspaceFileUseCase] delegates with the fixed preview budget. */
class PreviewWorkspaceFileUseCaseTest {

    private val workspace = mockk<AgentWorkspace>()
    private val useCase = PreviewWorkspaceFileUseCase(workspace)

    @Test
    fun `given a path when invoked then reads a preview with the configured budget`() = runTest {
        val preview = WorkspaceTextPreview(text = "hi", totalBytes = 2, truncated = false)
        coEvery { workspace.readTextPreview("note.txt", PreviewWorkspaceFileUseCase.PREVIEW_MAX_BYTES) } returns
            WorkspaceResult.Success(preview)

        val result = useCase("note.txt")

        assertEquals(WorkspaceResult.Success(preview), result)
        coVerify(exactly = 1) {
            workspace.readTextPreview("note.txt", PreviewWorkspaceFileUseCase.PREVIEW_MAX_BYTES)
        }
    }

    @Test
    fun `given the workspace refuses when invoked then propagates the failure`() = runTest {
        coEvery { workspace.readTextPreview(any(), any()) } returns WorkspaceResult.Failure(WorkspaceError.NotAText)

        val result = useCase("blob.bin")

        assertEquals(WorkspaceResult.Failure(WorkspaceError.NotAText), result)
    }
}
