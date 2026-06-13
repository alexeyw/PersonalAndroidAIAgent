package app.knotwork.android.domain.usecases.workspace

import app.knotwork.android.domain.models.WorkspaceError
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.services.AgentWorkspace
import javax.inject.Inject

/**
 * Deletes one or more workspace files, used by both the single-file delete and
 * the multi-select bulk delete on the Files screen.
 *
 * Deletion is per-file and independent: one path failing (for example, it was
 * removed by the agent between listing and confirming) does not abort the rest.
 * Every outcome is collected into a [WorkspaceDeleteSummary] so the screen can
 * refresh and, if anything failed, tell the user precisely what did not delete.
 *
 * @property workspace The agent's jailed file sandbox.
 */
class DeleteWorkspaceFilesUseCase @Inject constructor(private val workspace: AgentWorkspace) {
    /**
     * Attempts to delete every path in [relativePaths].
     *
     * @param relativePaths Workspace-relative paths to delete.
     * @return A [WorkspaceDeleteSummary] partitioning the paths into those that
     *   were removed and those that failed (with the typed cause).
     */
    suspend operator fun invoke(relativePaths: List<String>): WorkspaceDeleteSummary {
        val deleted = mutableListOf<String>()
        val failed = mutableMapOf<String, WorkspaceError>()
        for (path in relativePaths) {
            when (val result = workspace.delete(path)) {
                is WorkspaceResult.Success -> deleted += path
                is WorkspaceResult.Failure -> failed[path] = result.error
            }
        }
        return WorkspaceDeleteSummary(deleted = deleted, failed = failed)
    }
}

/**
 * Outcome of a (possibly bulk) workspace delete.
 *
 * @property deleted Paths that were successfully removed.
 * @property failed Paths that could not be removed, mapped to the typed cause.
 */
data class WorkspaceDeleteSummary(val deleted: List<String>, val failed: Map<String, WorkspaceError>) {
    /** `true` when every requested path was removed. */
    val allSucceeded: Boolean get() = failed.isEmpty()
}
