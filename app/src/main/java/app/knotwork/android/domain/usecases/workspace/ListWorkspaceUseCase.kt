package app.knotwork.android.domain.usecases.workspace

import app.knotwork.android.domain.models.WorkspaceListing
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.services.AgentWorkspace
import javax.inject.Inject

/**
 * Loads everything the Files screen needs to render in one call: the workspace
 * file listing and the storage-budget usage, bundled into a [WorkspaceListing].
 *
 * Fetching both together (rather than letting the ViewModel issue two
 * independent calls) keeps the rendered list and the quota indicator mutually
 * consistent — they reflect the same point in time — and gives the screen a
 * single success/failure outcome to map to its populated / empty / error states.
 *
 * @property workspace The agent's jailed file sandbox.
 */
class ListWorkspaceUseCase @Inject constructor(private val workspace: AgentWorkspace) {
    /**
     * Lists the workspace and reads its usage.
     *
     * @return [WorkspaceResult.Success] with the combined [WorkspaceListing], or
     *   the first [WorkspaceResult.Failure] encountered (listing is attempted
     *   before usage). A failure here is an unexpected I/O fault — neither call
     *   crosses the containment boundary.
     */
    suspend operator fun invoke(): WorkspaceResult<WorkspaceListing> {
        val files = when (val listed = workspace.list()) {
            is WorkspaceResult.Failure -> return listed
            is WorkspaceResult.Success -> listed.value
        }
        val usage = when (val used = workspace.usage()) {
            is WorkspaceResult.Failure -> return used
            is WorkspaceResult.Success -> used.value
        }
        return WorkspaceResult.Success(WorkspaceListing(files = files, usage = usage))
    }
}
