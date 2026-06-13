package app.knotwork.android.domain.usecases.workspace

import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.models.WorkspaceTextPreview
import app.knotwork.android.domain.services.AgentWorkspace
import javax.inject.Inject

/**
 * Produces a bounded, read-only preview of a workspace text file for the Files
 * screen's preview sheet.
 *
 * Delegates to [AgentWorkspace.readTextPreview] with a fixed byte budget so a
 * very large file never overflows the screen (or memory): the preview shows the
 * leading [PREVIEW_MAX_BYTES] and flags itself [WorkspaceTextPreview.truncated]
 * so the UI can tell the user to export the file to read it in full.
 *
 * @property workspace The agent's jailed file sandbox.
 */
class PreviewWorkspaceFileUseCase @Inject constructor(private val workspace: AgentWorkspace) {
    /**
     * Reads the leading [PREVIEW_MAX_BYTES] of the file at [relativePath].
     *
     * @param relativePath Path of the file to preview, relative to the workspace
     *   root.
     * @return The preview slice, or a typed failure (binary content, missing
     *   file, or a path that escapes the sandbox).
     */
    suspend operator fun invoke(relativePath: String): WorkspaceResult<WorkspaceTextPreview> =
        workspace.readTextPreview(relativePath = relativePath, maxBytes = PREVIEW_MAX_BYTES)

    /** Preview tuning constants. */
    companion object {
        /**
         * Leading bytes shown in a preview (64 KiB). Large enough to read a real
         * report, small enough to render and hold cheaply; anything beyond is
         * marked truncated and the user is steered toward export.
         */
        const val PREVIEW_MAX_BYTES: Int = 64 * 1024
    }
}
